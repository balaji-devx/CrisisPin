package com.helios.crisispin.service

import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.helios.crisispin.ble.BleAdvertiser
import com.helios.crisispin.ble.BleScanner
import com.helios.crisispin.utils.AlertManager

class CrisisPinService : Service() {

    companion object {
        const val CHANNEL_ID       = "crisispin_service"
        const val CHANNEL_ALERT_ID = "crisispin_alerts"
        const val NOTIF_SERVICE_ID = 1
        const val NOTIF_ALERT_ID   = 2

        // Broadcasts TO MainActivity
        const val ACTION_ALERT_RECEIVED      = "com.helios.crisispin.ALERT_RECEIVED"
        const val ACTION_BLE_STATE_CHANGED   = "com.helios.crisispin.BLE_STATE"
        const val ACTION_RELAY_STATE_CHANGED = "com.helios.crisispin.RELAY_STATE"
        const val ACTION_ALERT_BLOCKED       = "com.helios.crisispin.ALERT_BLOCKED"
        const val EXTRA_MESSAGE      = "message"
        const val EXTRA_BLE_ACTIVE   = "ble_active"
        const val EXTRA_RELAY_ACTIVE = "relay_active"
        const val EXTRA_RELAY_TYPE   = "relay_type"
        const val EXTRA_DEVICE_COUNT = "device_count"
        const val EXTRA_BLOCKED_TYPE = "blocked_type"
        const val EXTRA_UNBLOCK_TIME = "unblock_time"

        // Commands FROM MainActivity
        const val ACTION_START_ADVERTISING = "com.helios.crisispin.START_ADV"
        const val ACTION_STOP_ADVERTISING  = "com.helios.crisispin.STOP_ADV"
        const val ACTION_START_RELAY       = "com.helios.crisispin.START_RELAY"
        const val ACTION_STOP_RELAY        = "com.helios.crisispin.STOP_RELAY"
        const val ACTION_SOUND_ENABLED     = "com.helios.crisispin.SOUND_ON"
        const val ACTION_SOUND_DISABLED    = "com.helios.crisispin.SOUND_OFF"
        const val ACTION_VIB_ENABLED       = "com.helios.crisispin.VIB_ON"
        const val ACTION_VIB_DISABLED      = "com.helios.crisispin.VIB_OFF"
        const val ACTION_DISMISS_ALERT     = "com.helios.crisispin.DISMISS_ALERT"
        const val ACTION_SYNC_STATE        = "com.helios.crisispin.SYNC_STATE"
        const val EXTRA_ALERT_TYPE         = "alert_type"

        // Timing
        private const val ALERT_COOLDOWN_MS        = 8_000L    // BLE spam filter
        private const val POST_DISMISS_COOLDOWN_MS = 120_000L  // 2 min after dismiss
        private const val RELAY_DEBOUNCE_MS        = 60_000L

        // Prefs keys
        private const val PREF_RELAY_ACTIVE  = "relay_active"
        private const val PREF_RELAY_TYPE    = "relay_type"
        private const val PREF_PENDING_ALERT = "pending_alert"

        fun startService(context: Context) {
            val i = Intent(context, CrisisPinService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(i)
            else context.startService(i)
        }
    }

    private lateinit var lbm: LocalBroadcastManager
    private lateinit var prefs: SharedPreferences
    private var scanner: BleScanner? = null
    private var advertiser: BleAdvertiser? = null
    private var alertMgr: AlertManager? = null

    private var soundEnabled = true
    private var vibrationEnabled = true
    private var isRelaying = false
    private var currentRelayType: String? = null

    // Dedup maps
    private val lastAlertMs   = mutableMapOf<String, Long>()
    private val lastDismissMs = mutableMapOf<String, Long>()
    private val lastRelayMs   = mutableMapOf<String, Long>()

    // Unique device tracking for DEVICES counter
    private val seenDevices = mutableSetOf<String>()

    // Last alert — stored so UI can pick it up on resume even if broadcast was missed
    private var pendingAlertForUI: String? = null

    private var alertScreenOpen = false

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                BluetoothAdapter.STATE_ON -> {
                    scanner?.startScanning()
                    // Restore relay if it was active
                    val wasRelaying = prefs.getBoolean(PREF_RELAY_ACTIVE, false)
                    val savedType = prefs.getString(PREF_RELAY_TYPE, null)
                    if (wasRelaying && savedType != null) startRelayInternal(savedType)
                    lbm.sendBroadcast(Intent(ACTION_BLE_STATE_CHANGED).putExtra(EXTRA_BLE_ACTIVE, true))
                }
                BluetoothAdapter.STATE_OFF -> {
                    scanner?.stopScanning()
                    advertiser?.stopAdvertising()
                    scanner?.setSelfAdvertising(null)
                    alertScreenOpen = false
                    isRelaying = false
                    currentRelayType = null
                    saveRelayState(false, null)
                    lbm.sendBroadcast(Intent(ACTION_BLE_STATE_CHANGED).putExtra(EXTRA_BLE_ACTIVE, false))
                    lbm.sendBroadcast(Intent(ACTION_RELAY_STATE_CHANGED).putExtra(EXTRA_RELAY_ACTIVE, false))
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        lbm   = LocalBroadcastManager.getInstance(this)
        prefs = getSharedPreferences("cp_service_prefs", Context.MODE_PRIVATE)
        alertScreenOpen = false

        createChannels()
        startForeground(NOTIF_SERVICE_ID, silentNotif())

        alertMgr   = AlertManager(this)
        advertiser = BleAdvertiser(this)
        scanner    = BleScanner(this) { msg, addr -> handleAlert(msg, addr) }

        registerReceiver(btReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))

        val bt = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (bt.isEnabled) {
            scanner?.startScanning()
            val wasRelaying = prefs.getBoolean(PREF_RELAY_ACTIVE, false)
            val savedType   = prefs.getString(PREF_RELAY_TYPE, null)
            if (wasRelaying && savedType != null) {
                Log.d("Service", "Restoring relay: $savedType")
                startRelayInternal(savedType)
            }
        }

        // Restore any pending alert that wasn't acknowledged before restart
        pendingAlertForUI = prefs.getString(PREF_PENDING_ALERT, null)
        Log.d("Service", "onCreate ✓")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ADVERTISING -> {
                val t = intent.getStringExtra(EXTRA_ALERT_TYPE) ?: "SOS"
                advertiser?.startAdvertising(t)
                scanner?.setSelfAdvertising(t)
            }
            ACTION_STOP_ADVERTISING -> {
                advertiser?.stopAdvertising()
                if (!isRelaying) scanner?.setSelfAdvertising(null)
            }
            ACTION_START_RELAY -> {
                val t = intent.getStringExtra(EXTRA_ALERT_TYPE) ?: "SOS"
                startRelayInternal(t)
            }
            ACTION_STOP_RELAY -> stopRelay()

            ACTION_DISMISS_ALERT -> {
                val type = intent.getStringExtra(EXTRA_ALERT_TYPE)
                alertScreenOpen = false
                pendingAlertForUI = null
                prefs.edit().remove(PREF_PENDING_ALERT).apply()
                if (type != null) {
                    lastDismissMs[type.uppercase()] = System.currentTimeMillis()
                    Log.d("Service", "Dismissed '$type' — 2min cooldown set")
                }
            }

            ACTION_SYNC_STATE -> {
                // UI is requesting current state — send everything back
                val btOn = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter.isEnabled
                lbm.sendBroadcast(Intent(ACTION_BLE_STATE_CHANGED).putExtra(EXTRA_BLE_ACTIVE, btOn))
                lbm.sendBroadcast(Intent(ACTION_RELAY_STATE_CHANGED)
                    .putExtra(EXTRA_RELAY_ACTIVE, isRelaying)
                    .putExtra(EXTRA_RELAY_TYPE, currentRelayType))
                // Re-deliver any missed alert
                pendingAlertForUI?.let { pending ->
                    Log.d("Service", "Re-delivering missed alert: $pending")
                    lbm.sendBroadcast(Intent(ACTION_ALERT_RECEIVED).putExtra(EXTRA_MESSAGE, pending))
                }
                // Send device count
                lbm.sendBroadcast(Intent(ACTION_ALERT_RECEIVED)
                    .putExtra(EXTRA_DEVICE_COUNT, seenDevices.size))
            }

            ACTION_SOUND_ENABLED  -> { soundEnabled = true;     alertMgr?.setSoundEnabled(true)  }
            ACTION_SOUND_DISABLED -> { soundEnabled = false;    alertMgr?.setSoundEnabled(false) }
            ACTION_VIB_ENABLED    -> { vibrationEnabled = true; alertMgr?.setVibrationEnabled(true)  }
            ACTION_VIB_DISABLED   -> { vibrationEnabled = false;alertMgr?.setVibrationEnabled(false) }
        }
        return START_STICKY
    }

    private fun handleAlert(message: String, deviceAddress: String) {
        val now  = System.currentTimeMillis()
        val type = message.uppercase()

        // Track unique devices regardless of cooldowns
        val isNewDevice = seenDevices.add(deviceAddress)

        // Gate 1: BLE spam filter
        if (now - (lastAlertMs[type] ?: 0L) < ALERT_COOLDOWN_MS) return

        // Gate 2: Post-dismiss cooldown (2 min)
        val dismissTime = lastDismissMs[type] ?: 0L
        if (now - dismissTime < POST_DISMISS_COOLDOWN_MS) {
            if (isNewDevice) {
                // New device seen — notify UI of device count update only
                val remaining = ((POST_DISMISS_COOLDOWN_MS - (now - dismissTime)) / 1000L)
                Log.d("Service", "Blocked '$type' — ${remaining}s remaining cooldown")
                lbm.sendBroadcast(Intent(ACTION_ALERT_BLOCKED)
                    .putExtra(EXTRA_BLOCKED_TYPE, type)
                    .putExtra(EXTRA_UNBLOCK_TIME, remaining))
            }
            return
        }

        // Gate 3: Screen already open
        if (alertScreenOpen) return

        lastAlertMs[type] = now
        alertScreenOpen = true

        // Store pending alert so it can be re-delivered on resume if broadcast is missed
        pendingAlertForUI = message
        prefs.edit().putString(PREF_PENDING_ALERT, message).apply()

        Log.d("Service", "✅ Alert: $type from $deviceAddress")

        lbm.sendBroadcast(Intent(ACTION_ALERT_RECEIVED)
            .putExtra(EXTRA_MESSAGE, message)
            .putExtra(EXTRA_DEVICE_COUNT, seenDevices.size))
        alertNotif(message)
        alertMgr?.triggerAlert(message, soundEnabled, vibrationEnabled)
    }

    private fun startRelayInternal(alertType: String) {
        val now = System.currentTimeMillis()
        if (now - (lastRelayMs[alertType] ?: 0L) < RELAY_DEBOUNCE_MS) {
            Log.d("Service", "Relay debounced: $alertType"); return
        }
        lastRelayMs[alertType] = now
        isRelaying = true
        currentRelayType = alertType
        scanner?.setSelfAdvertising(alertType)
        advertiser?.startAdvertising(alertType)
        saveRelayState(true, alertType)
        lbm.sendBroadcast(Intent(ACTION_RELAY_STATE_CHANGED)
            .putExtra(EXTRA_RELAY_ACTIVE, true)
            .putExtra(EXTRA_RELAY_TYPE, alertType))
        updateNotif("📡 Relaying $alertType — tap Stop to end")
    }

    private fun stopRelay() {
        if (!isRelaying) return
        isRelaying = false
        currentRelayType = null
        advertiser?.stopAdvertising()
        scanner?.setSelfAdvertising(null)
        saveRelayState(false, null)
        lbm.sendBroadcast(Intent(ACTION_RELAY_STATE_CHANGED).putExtra(EXTRA_RELAY_ACTIVE, false))
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_SERVICE_ID, silentNotif())
    }

    private fun saveRelayState(active: Boolean, type: String?) {
        prefs.edit().putBoolean(PREF_RELAY_ACTIVE, active).putString(PREF_RELAY_TYPE, type).apply()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "CrisisPin", NotificationManager.IMPORTANCE_MIN)
                    .also { it.setSound(null, null); it.enableVibration(false); it.setShowBadge(false) })
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERT_ID, "Alerts", NotificationManager.IMPORTANCE_HIGH)
                    .also { it.enableVibration(true); it.enableLights(true) })
        }
    }

    private fun launchPi() = PendingIntent.getActivity(
        this, 0, packageManager.getLaunchIntentForPackage(packageName), PendingIntent.FLAG_IMMUTABLE)

    private fun silentNotif() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("CrisisPin").setContentText("Listening for alerts")
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentIntent(launchPi()).setOngoing(true).setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_MIN).build()

    private fun updateNotif(text: String) {
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CrisisPin Active").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(launchPi()).setOngoing(true).setSilent(true).build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_SERVICE_ID, n)
    }

    private fun alertNotif(message: String) {
        val pi = PendingIntent.getActivity(this, NOTIF_ALERT_ID,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val title = when (message.uppercase()) {
            "SOS"   -> "🚨 SOS Emergency Nearby!"; "MED" -> "🏥 Medical Emergency!"
            "FIRE"  -> "🔥 Fire Alert!";            "PANIC" -> "⚠️ Panic Alert!"
            "HELP"  -> "🆘 Help Needed!";           else -> "⚠️ Alert: $message"
        }
        NotificationCompat.Builder(this, CHANNEL_ALERT_ID)
            .setContentTitle(title).setContentText("Tap to respond")
            .setSmallIcon(android.R.drawable.ic_dialog_alert).setContentIntent(pi)
            .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM).setFullScreenIntent(pi, true)
            .build()
            .also { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIF_ALERT_ID, it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        advertiser?.stopAdvertising()
        scanner?.stopScanning()
        alertMgr?.release()
        try { unregisterReceiver(btReceiver) } catch (e: Exception) { }
    }
}