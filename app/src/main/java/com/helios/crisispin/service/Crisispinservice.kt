package com.helios.crisispin.service

import android.app.*
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.helios.crisispin.ble.BleAdvertiser
import com.helios.crisispin.ble.BleScanner
import com.helios.crisispin.utils.*

/**
 * Core background service for CrisisPin.
 * Manages BLE scanning, mesh relaying, and trust-aware alert processing.
 */
class CrisisPinService : Service() {

    companion object {
        const val CHANNEL_ID         = "crisispin_service"
        const val CHANNEL_ALERT_ID   = "crisispin_alerts"
        const val NOTIF_SERVICE_ID   = 1
        const val NOTIF_ALERT_ID     = 2

        const val ACTION_ALERT_RECEIVED      = "com.helios.crisispin.ALERT_RECEIVED"
        const val ACTION_BLE_STATE_CHANGED   = "com.helios.crisispin.BLE_STATE"
        const val ACTION_RELAY_STATE_CHANGED = "com.helios.crisispin.RELAY_STATE"
        const val ACTION_SCORE_UPDATED       = "com.helios.crisispin.SCORE_UPDATED"
        const val ACTION_ALERT_CANCELLED     = "com.helios.crisispin.ALERT_CANCELLED"
        
        const val EXTRA_MESSAGE      = "message"
        const val EXTRA_MSG_ID       = "msg_id"
        const val EXTRA_MSG_ENCODED  = "msg_encoded"
        const val EXTRA_BLE_ACTIVE   = "ble_active"
        const val EXTRA_RELAY_ACTIVE = "relay_active"
        const val EXTRA_SCORE        = "score"

        const val ACTION_START_ADVERTISING  = "com.helios.crisispin.START_ADV"
        const val ACTION_STOP_ADVERTISING   = "com.helios.crisispin.STOP_ADV"
        const val ACTION_START_RELAY        = "com.helios.crisispin.START_RELAY"
        const val ACTION_STOP_RELAY         = "com.helios.crisispin.STOP_RELAY"
        const val ACTION_SOUND_ENABLED      = "com.helios.crisispin.SOUND_ON"
        const val ACTION_SOUND_DISABLED     = "com.helios.crisispin.SOUND_OFF"
        const val ACTION_VIBRATION_ENABLED  = "com.helios.crisispin.VIB_ON"
        const val ACTION_VIBRATION_DISABLED = "com.helios.crisispin.VIB_OFF"
        const val ACTION_DISMISS_ALERT      = "com.helios.crisispin.DISMISS_ALERT"
        const val EXTRA_ALERT_TYPE          = "alert_type"

        const val ALERT_COOLDOWN_MS = 8_000L
        const val RELAY_DEBOUNCE_MS = 60_000L
        const val MAX_MAP_SIZE = 500
        const val SCORE_UPDATE_THROTTLE_MS = 300L

        // CANCEL should not live forever (anti-chaos)
        const val CANCEL_TTL_MS = 60_000L

        /**
         * Safely starts the service as a foreground service on Android 8+.
         */
        fun startService(context: Context) {
            val intent = Intent(context, CrisisPinService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }

        fun stopService(context: Context) =
            context.stopService(Intent(context, CrisisPinService::class.java))
    }

    private var bleAdvertiser: BleAdvertiser? = null
    private var bleScanner:    BleScanner?    = null
    private var alertManager:  AlertManager?  = null
    private var isRelaying = false
    private lateinit var localBroadcast: LocalBroadcastManager

    // Trust/Confidence tracking
    private val ackSetByMsgId = mutableMapOf<String, MutableSet<String>>()
    private val firstSeenTime = mutableMapOf<String, Long>()
    private val duplicateAckCount = mutableMapOf<String, Int>()
    private val lastAckTimePerDevice = mutableMapOf<String, Long>() // originId -> timestamp
    
    // FIX 3 & 4: Cancel tracking and suppression
    private val cancelSeenTimeByMsgId = mutableMapOf<String, Long>() // msgId -> when we saw CANCEL

    // Current alert lifecycle state
    private var activeAlertMsgId: String? = null

    // FIX 4: Throttle confidence score updates
    private val lastScoreUpdate = mutableMapOf<String, Long>()

    private val lastAlertFireTime = mutableMapOf<String, Long>()
    private val lastRelayTime     = mutableMapOf<String, Long>()
    private var alertIsActive = false

    // FIX 3: Track current outgoing alert for cancellation
    private var currentSentMsg: CrisisMessage? = null
    
    // ERROR 1 FIX: Use an anonymous object to correctly override removeEldestEntry at class level
    private val recentAlerts = object : java.util.LinkedHashMap<String, CrisisMessage>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CrisisMessage>?): Boolean {
            return size > 16  // Keep max 16 recent alerts
        }
    }

    // Score update throttling
    private val scoreHandler = Handler(Looper.getMainLooper())
    private val pendingScoreUpdates = mutableSetOf<String>()

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> {
                    // FIX 1: Prevent duplicate scanning start
                    if (!(bleScanner?.isScanning() ?: false)) {
                        safeStartScanning()
                    }
                    localBroadcast.sendBroadcast(Intent(ACTION_BLE_STATE_CHANGED).putExtra(EXTRA_BLE_ACTIVE, true))
                }
                BluetoothAdapter.STATE_OFF -> {
                    safeStopScanning()
                    bleAdvertiser?.stopAdvertising()
                    isRelaying = false
                    stopActiveAlert(reason = "bt_off")
                    localBroadcast.sendBroadcast(Intent(ACTION_BLE_STATE_CHANGED).putExtra(EXTRA_BLE_ACTIVE, false))
                    localBroadcast.sendBroadcast(Intent(ACTION_RELAY_STATE_CHANGED).putExtra(EXTRA_RELAY_ACTIVE, false))
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // STEP 3C: Foreground Notification immediately in onCreate
        createNotificationChannels()
        startForeground(NOTIF_SERVICE_ID, buildSilentNotification())

        alertIsActive = false
        localBroadcast = LocalBroadcastManager.getInstance(this)
        alertManager  = AlertManager(this)
        bleAdvertiser = BleAdvertiser(this)
        bleScanner    = BleScanner(this) { msg -> onAlertReceived(msg) }
        registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        
        // STEP 3B: Guarantee scan start in onCreate
        // FIX 1: Prevent duplicate scanning start
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter.isEnabled && !(bleScanner?.isScanning() ?: false)) {
            safeStartScanning()
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeStartScanning() {
        if (!PermissionHelper.hasPermissions(this)) {
            Log.e("CrisisPinService", "Scanning aborted: Missing permissions")
            return
        }
        try { bleScanner?.startScanning() } catch (se: SecurityException) { }
    }

    @SuppressLint("MissingPermission")
    private fun safeStopScanning() {
        try { bleScanner?.stopScanning() } catch (se: SecurityException) { }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // STEP 3B: Guarantee scan start in onStartCommand (background reliability)
        // FIX 1: Prevent duplicate scanning start
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter.isEnabled && !(bleScanner?.isScanning() ?: false)) {
            safeStartScanning()
        }

        when (intent?.action) {
            ACTION_START_ADVERTISING -> {
                val alertType = intent.getStringExtra(EXTRA_ALERT_TYPE) ?: "SOS"
                val myId = DeviceIdentity.getDeviceId(this)
                val msg = CrisisMessage.newAlert(alertType, myId)
                currentSentMsg = msg
                bleAdvertiser?.startRelaying(msg)
            }
            ACTION_STOP_ADVERTISING -> {
                val isCancel = intent?.getBooleanExtra("is_cancel", false) ?: false
                if (isCancel && currentSentMsg != null) {
                    val cancelMsg = CrisisMessage.createCancel(currentSentMsg!!, DeviceIdentity.getDeviceId(this))
                    bleAdvertiser?.startRelaying(cancelMsg)
                    Handler(Looper.getMainLooper()).postDelayed({
                        bleAdvertiser?.stopAdvertising()
                    }, 5000)
                } else {
                    bleAdvertiser?.stopAdvertising()
                }
                currentSentMsg = null
            }
            ACTION_START_RELAY -> {
                // Prefer msg_id (UI passes exact alert context). Fallback to encoded payload, then most recent.
                val msgId = intent.getStringExtra(EXTRA_MSG_ID)
                val encodedMsg = intent.getStringExtra(EXTRA_MSG_ENCODED)
                val msg = when {
                    msgId != null -> recentAlerts[msgId]
                    !encodedMsg.isNullOrBlank() -> {
                        val bytes = try { Base64.decode(encodedMsg, Base64.DEFAULT) } catch (_: Exception) { null }
                        bytes?.let { CrisisMessage.decodeFromBle(it) }
                    }
                    else -> recentAlerts.values.lastOrNull()
                }
                if (msg != null) {
                    // CANCEL overrides ACK (race safety)
                    if (!isCancelledWithinTtl(msg.msgId)) {
                        // FIX 2: Prevent self-acknowledgement
                        val deviceId = DeviceIdentity.getDeviceId(this)
                        if (msg.originId != deviceId) {
                            val ackMsg = CrisisMessage.createAck(msg, deviceId)
                            startMeshRelay(ackMsg)
                        }
                    }
                }
            }
            ACTION_STOP_RELAY -> stopMeshRelay()
            ACTION_DISMISS_ALERT -> { 
                val msgId = intent.getStringExtra(EXTRA_MSG_ID)
                stopActiveAlert(reason = "dismiss", msgId = msgId)
            }
            ACTION_SOUND_ENABLED    -> alertManager?.setSoundEnabled(true)
            ACTION_SOUND_DISABLED   -> alertManager?.setSoundEnabled(false)
            ACTION_VIBRATION_ENABLED  -> alertManager?.setVibrationEnabled(true)
            ACTION_VIBRATION_DISABLED -> alertManager?.setVibrationEnabled(false)
        }
        return START_STICKY
    }

    private fun onAlertReceived(msg: CrisisMessage) {
        val now = System.currentTimeMillis()

        cleanupOldCancels(now)
        
        // FIX 3: Handle CANCEL signals
        if ((msg.flags and CrisisMessage.FLAG_CANCEL) != 0) {
            handleCancel(msg.msgId, now)
            return
        }

        // Trust Engine: Handle ACKs
        if ((msg.flags and CrisisMessage.FLAG_ACK) != 0) {
            // Step 3: Ignore ACKs if alert was already cancelled
            if (isCancelledWithinTtl(msg.msgId, now)) return
            handleAck(msg)
            return
        }

        // Step 3: Ignore normal alert if we already processed a CANCEL for it (race-safe within TTL)
        if (isCancelledWithinTtl(msg.msgId, now)) return

        val lastFire = lastAlertFireTime[msg.msgId] ?: 0L
        if (now - lastFire < ALERT_COOLDOWN_MS) return

        lastAlertFireTime[msg.msgId] = now
        capMapByOldest(lastAlertFireTime)
        
        if (!firstSeenTime.containsKey(msg.msgId)) {
            firstSeenTime[msg.msgId] = now
            capMapByOldest(firstSeenTime)
        }
        
        // Add to recent alerts queue (handles simultaneous alerts gracefully)
        recentAlerts[msg.msgId] = msg
        HistoryPrefs.save(this, msg.type, "received", msg.msgId)

        if (alertIsActive) {
            throttleScoreUpdate(msg)
            return
        }
        alertIsActive = true
        activeAlertMsgId = msg.msgId
        Log.d("CrisisPinService", "Dispatching alert: ${msg.type} from ${msg.originId}")

        val encoded = CrisisMessage.encodeForBle(msg)?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        localBroadcast.sendBroadcast(
            Intent(ACTION_ALERT_RECEIVED)
                .putExtra(EXTRA_MESSAGE, msg.type)
                .putExtra(EXTRA_MSG_ID, msg.msgId)
                .putExtra(EXTRA_MSG_ENCODED, encoded)
        )
        showAlertNotification(msg.type, msg.msgId)
        alertManager?.triggerAlert(msg.type)
        throttleScoreUpdate(msg)
    }

    private fun handleAck(msg: CrisisMessage) {
        val now = System.currentTimeMillis()

        // CANCEL overrides ACK (race safety)
        if (isCancelledWithinTtl(msg.msgId, now)) return
        val originalSeen = firstSeenTime[msg.msgId]
        
        // 1. Ignore ACKs arriving too fast (<1s after original) to prevent burst spoofing
        if (originalSeen != null && now - originalSeen < 1000L) return

        // 2. Rate limit: ignore ACK if same device sends within 5 seconds
        val lastDeviceAck = lastAckTimePerDevice[msg.originId]
        if (lastDeviceAck != null && now - lastDeviceAck < 5000L) return
        lastAckTimePerDevice[msg.originId] = now
        capMapByOldest(lastAckTimePerDevice)

        val acks = ackSetByMsgId.getOrPut(msg.msgId) { mutableSetOf() }
        if (msg.originId in acks) {
            // Suspicious: same device sending ACK multiple times for same message
            duplicateAckCount[msg.msgId] = (duplicateAckCount[msg.msgId] ?: 0) + 1
            capMapByOldest(duplicateAckCount)
        } else {
            acks.add(msg.originId)
        }
        capMapByOldest(ackSetByMsgId)
        
        // Use recentAlerts lookup instead of any global single-message reference
        recentAlerts[msg.msgId]?.let { throttleScoreUpdate(it) }
    }

    private fun handleCancel(msgId: String, now: Long = System.currentTimeMillis()) {
        // Duplicate suppression
        val already = cancelSeenTimeByMsgId[msgId]
        if (already != null && now - already <= CANCEL_TTL_MS) return

        // TTL rule: only accept cancel if original is recent (or we haven't seen it yet)
        val originalSeen = firstSeenTime[msgId]
        if (originalSeen != null && now - originalSeen > CANCEL_TTL_MS) {
            Log.d("CrisisPinService", "Ignoring late CANCEL for $msgId (original too old)")
            return
        }

        cancelSeenTimeByMsgId[msgId] = now
        capMapByOldest(cancelSeenTimeByMsgId)

        Log.d("CrisisPinService", "Processing CANCEL for $msgId")

        // CANCEL overrides ACK: remove from trackers to avoid further scoring
        ackSetByMsgId.remove(msgId)
        duplicateAckCount.remove(msgId)

        // Stop alert UX instantly (no ghost vibration)
        stopActiveAlert(reason = "cancel", msgId = msgId)

        // Mark history (keep record but tag as cancelled)
        HistoryPrefs.markCancelled(this, msgId)

        // Remove from cache to prevent later scoring/UI
        recentAlerts.remove(msgId)
        firstSeenTime.remove(msgId)
        lastAlertFireTime.remove(msgId)

        localBroadcast.sendBroadcast(
            Intent(ACTION_ALERT_CANCELLED)
                .putExtra(EXTRA_MSG_ID, msgId)
        )
    }

    private fun stopActiveAlert(reason: String, msgId: String? = null) {
        // If a msgId is provided, only stop if it matches current active alert.
        if (msgId != null && activeAlertMsgId != null && activeAlertMsgId != msgId) return

        alertIsActive = false
        activeAlertMsgId = null

        // Stop vibration/sound immediately
        alertManager?.stopAlert()

        // Dismiss alert notification to avoid stale UI
        try { getSystemService(NotificationManager::class.java).cancel(NOTIF_ALERT_ID) } catch (_: Exception) { }
    }

    private fun cleanupOldCancels(now: Long = System.currentTimeMillis()) {
        cancelSeenTimeByMsgId.entries.removeIf { now - it.value > CANCEL_TTL_MS }
    }

    private fun isCancelledWithinTtl(msgId: String, now: Long = System.currentTimeMillis()): Boolean {
        val t = cancelSeenTimeByMsgId[msgId] ?: return false
        return now - t <= CANCEL_TTL_MS
    }

    private fun throttleScoreUpdate(msg: CrisisMessage) {
        if (pendingScoreUpdates.contains(msg.msgId)) return
        
        // FIX 4: Throttle confidence score updates
        val now = System.currentTimeMillis()
        val last = lastScoreUpdate[msg.msgId] ?: 0L
        if (now - last > 300) {
            lastScoreUpdate[msg.msgId] = now
            pendingScoreUpdates.add(msg.msgId)
            scoreHandler.postDelayed({
                pendingScoreUpdates.remove(msg.msgId)
                updateConfidence(msg)
            }, SCORE_UPDATE_THROTTLE_MS)
        }
    }

    private fun updateConfidence(msg: CrisisMessage) {
        val acks = ackSetByMsgId[msg.msgId]?.size ?: 0
        val dups = duplicateAckCount[msg.msgId] ?: 0
        val seen = bleScanner?.getNearbyCount() ?: 0
        val startTime = firstSeenTime[msg.msgId] ?: System.currentTimeMillis()
        
        val score = ConfidenceEngine.computeScore(acks, seen, msg.hop, startTime, dups)
        
        localBroadcast.sendBroadcast(
            Intent(ACTION_SCORE_UPDATED)
                .putExtra(EXTRA_MSG_ID, msg.msgId)
                .putExtra(EXTRA_SCORE, score)
        )
    }

    private fun startMeshRelay(msg: CrisisMessage) {
        val now = System.currentTimeMillis()
        if (now - (lastRelayTime[msg.type] ?: 0L) < RELAY_DEBOUNCE_MS) return
        lastRelayTime[msg.type] = now
        capMapByOldest(lastRelayTime)
        isRelaying = true
        bleAdvertiser?.startRelaying(msg)
        localBroadcast.sendBroadcast(Intent(ACTION_RELAY_STATE_CHANGED).putExtra(EXTRA_RELAY_ACTIVE, true))
    }

    private fun stopMeshRelay() {
        if (!isRelaying) return
        isRelaying = false
        bleAdvertiser?.stopAdvertising()
        localBroadcast.sendBroadcast(Intent(ACTION_RELAY_STATE_CHANGED).putExtra(EXTRA_RELAY_ACTIVE, false))
        getSystemService(NotificationManager::class.java).notify(NOTIF_SERVICE_ID, buildSilentNotification())
    }

    /**
     * Step 7: Memory safety helper. Prevents unbounded growth of tracking maps.
     */
    private fun <K, V> capMapByOldest(map: MutableMap<K, V>, maxSize: Int = MAX_MAP_SIZE) {
        if (map.size <= maxSize) return
        val it = map.keys.iterator()
        if (it.hasNext()) { it.next(); it.remove() }
    }
    
    private fun <T> capSet(set: MutableSet<T>, maxSize: Int = MAX_MAP_SIZE) {
        if (set.size <= maxSize) return
        val it = set.iterator()
        if (it.hasNext()) { it.next(); it.remove() }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(CHANNEL_ID, "CrisisPin Service", NotificationManager.IMPORTANCE_MIN)
                .apply { setShowBadge(false); setSound(null, null); enableVibration(false) }
                .also { getSystemService(NotificationManager::class.java).createNotificationChannel(it) }
            NotificationChannel(CHANNEL_ALERT_ID, "Emergency Alerts", NotificationManager.IMPORTANCE_HIGH)
                .apply { enableVibration(false); enableLights(true) }
                .also { getSystemService(NotificationManager::class.java).createNotificationChannel(it) }
        }
    }

    private fun buildSilentNotification(): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            packageManager.getLaunchIntentForPackage(packageName), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CrisisPin")
            // FIX 5: Improve foreground notification text
            .setContentText("CrisisPin is actively scanning nearby alerts")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pi).setOngoing(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN).build()
    }

    private fun showAlertNotification(alertType: String, msgId: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            putExtra("show_alert_message", alertType)
            putExtra(EXTRA_MSG_ID, msgId)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(this, NOTIF_ALERT_ID, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val deletePi = PendingIntent.getService(
            this,
            NOTIF_ALERT_ID + 100,
            Intent(this, CrisisPinService::class.java).apply {
                action = ACTION_DISMISS_ALERT
                putExtra(EXTRA_MSG_ID, msgId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = when (alertType.uppercase()) {
            "SOS"->"🚨 SOS Emergency Nearby!";"MED"->"🏥 Medical Emergency Nearby!"
            "FIRE"->"🔥 Fire Alert Nearby!";"PANIC"->"⚠️ Panic Alert Nearby!"
            "HELP"->"🆘 Help Needed Nearby!";else->"⚠️ Emergency: $alertType"
        }
        NotificationCompat.Builder(this, CHANNEL_ALERT_ID)
            .setContentTitle(title).setContentText("Tap to open CrisisPin and respond")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pi).setAutoCancel(true)
            .setDeleteIntent(deletePi)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pi, true).build()
            .also { getSystemService(NotificationManager::class.java).notify(NOTIF_ALERT_ID, it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        bleAdvertiser?.stopAdvertising()
        safeStopScanning()
        alertManager?.release()
        try { unregisterReceiver(bluetoothStateReceiver) } catch (e: Exception) { }
    }
}
