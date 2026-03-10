package com.helios.crisispin.service

import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
        const val CHANNEL_ID         = "crisispin_service"
        const val CHANNEL_ALERT_ID   = "crisispin_alerts"
        const val NOTIF_SERVICE_ID   = 1
        const val NOTIF_ALERT_ID     = 2

        // Broadcasts TO MainActivity
        const val ACTION_ALERT_RECEIVED      = "com.helios.crisispin.ALERT_RECEIVED"
        const val ACTION_BLE_STATE_CHANGED   = "com.helios.crisispin.BLE_STATE"
        const val ACTION_RELAY_STATE_CHANGED = "com.helios.crisispin.RELAY_STATE"
        const val EXTRA_MESSAGE      = "message"
        const val EXTRA_BLE_ACTIVE   = "ble_active"
        const val EXTRA_RELAY_ACTIVE = "relay_active"

        // Commands FROM MainActivity
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

        // ── COOLDOWN: how long to suppress duplicate BLE packets for same alert ──
        // BLE scanner fires every 100-500ms for the same nearby advertiser.
        // 8 seconds = 1 alert fires through, then 8s of silence.
        // Short enough that a second DIFFERENT device can still trigger quickly.
        // IMPORTANT: This only applies between packets — user can dismiss and
        //            immediately receive the NEXT alert from a different device.
        const val ALERT_COOLDOWN_MS = 8_000L

        // Mesh relay: separate 60s debounce to prevent relay loop (different from alert cooldown)
        const val RELAY_DEBOUNCE_MS = 60_000L

        fun startService(context: Context) {
            val intent = Intent(context, CrisisPinService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, CrisisPinService::class.java))
        }
    }

    private var bleAdvertiser: BleAdvertiser? = null
    private var bleScanner: BleScanner? = null
    private var alertManager: AlertManager? = null
    private var isRelaying = false
    private var soundEnabled = true
    private var vibrationEnabled = true

    private lateinit var localBroadcast: LocalBroadcastManager

    // ── BUG 1 FIX: SEPARATE maps for alerts vs relay ──────────────────────────
    // Previously both used the same lastAlertTime map.
    // Relay writes with 60s debounce, alert cooldown is 8s.
    // If they share a map, acknowledging+relaying "MED" blocks next "MED" alert for 60s.
    private val lastAlertFireTime  = mutableMapOf<String, Long>() // per-type alert cooldown
    private val lastRelayTime      = mutableMapOf<String, Long>() // per-type relay debounce

    // ── BUG 2 FIX: alertIsActive tracks whether UI is currently showing the alert ─
    // Set to true when we broadcast to UI.
    // Set to false when ACTION_DISMISS_ALERT received OR when service restarts.
    // Previously this was never cleared on service restart → all alerts blocked forever.
    private var alertIsActive = false  // starts FALSE — safe on restart

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            when (state) {
                BluetoothAdapter.STATE_ON -> {
                    bleScanner?.startScanning()
                    localBroadcast.sendBroadcast(
                        Intent(ACTION_BLE_STATE_CHANGED).putExtra(EXTRA_BLE_ACTIVE, true)
                    )
                }
                BluetoothAdapter.STATE_OFF -> {
                    bleScanner?.stopScanning()
                    bleAdvertiser?.stopAdvertising()
                    isRelaying = false
                    alertIsActive = false // clear on BT off — safe reset
                    localBroadcast.sendBroadcast(
                        Intent(ACTION_BLE_STATE_CHANGED).putExtra(EXTRA_BLE_ACTIVE, false)
                    )
                    localBroadcast.sendBroadcast(
                        Intent(ACTION_RELAY_STATE_CHANGED).putExtra(EXTRA_RELAY_ACTIVE, false)
                    )
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // alertIsActive = false by default — correct. Service restart is clean.
        localBroadcast = LocalBroadcastManager.getInstance(this)
        createNotificationChannels()
        startForeground(NOTIF_SERVICE_ID, buildSilentNotification())

        alertManager = AlertManager(this)
        bleAdvertiser = BleAdvertiser(this)
        bleScanner = BleScanner(this) { message ->
            onAlertReceived(message)
        }

        registerReceiver(
            bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        )

        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter.isEnabled) bleScanner?.startScanning()

        Log.d("CrisisPinService", "Service created — alertIsActive=$alertIsActive")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ADVERTISING -> {
                val alertType = intent.getStringExtra(EXTRA_ALERT_TYPE) ?: "SOS"
                bleAdvertiser?.startAdvertising(alertType)
            }
            ACTION_STOP_ADVERTISING -> {
                bleAdvertiser?.stopAdvertising()
            }
            ACTION_START_RELAY -> {
                val alertType = intent.getStringExtra(EXTRA_ALERT_TYPE) ?: "SOS"
                startMeshRelay(alertType)
            }
            ACTION_STOP_RELAY -> {
                stopMeshRelay()
            }
            // ── BUG 2 FIX: Dismiss clears alertIsActive so next alert can show ──
            ACTION_DISMISS_ALERT -> {
                alertIsActive = false
                Log.d("CrisisPinService", "Alert dismissed — ready for next alert")
            }
            ACTION_SOUND_ENABLED    -> { soundEnabled = true;  alertManager?.setSoundEnabled(true)  }
            ACTION_SOUND_DISABLED   -> { soundEnabled = false; alertManager?.setSoundEnabled(false) }
            ACTION_VIBRATION_ENABLED  -> { vibrationEnabled = true;  alertManager?.setVibrationEnabled(true)  }
            ACTION_VIBRATION_DISABLED -> { vibrationEnabled = false; alertManager?.setVibrationEnabled(false) }
        }
        return START_STICKY
    }

    private fun onAlertReceived(message: String) {
        val now = System.currentTimeMillis()

        // ── GATE 1: Time cooldown — kills the BLE packet spam loop ──────────────
        // BLE scanner fires every 100ms for same nearby advertiser.
        // We only let one through per ALERT_COOLDOWN_MS window (8 seconds).
        // Uses SEPARATE map from relay — relay does not reset alert cooldown.
        val lastFire = lastAlertFireTime[message] ?: 0L
        if (now - lastFire < ALERT_COOLDOWN_MS) {
            return // silent drop — no log (would spam logcat every 100ms)
        }

        // ── GATE 2: Alert screen already open ───────────────────────────────────
        // If UI is already showing an alert, don't fire another broadcast.
        // This covers the case where the cooldown expired while user is still
        // looking at a previous alert.
        if (alertIsActive) {
            Log.d("CrisisPinService", "Alert already active — skipping '$message'")
            return
        }

        // Both gates passed — new alert
        lastAlertFireTime[message] = now
        alertIsActive = true
        Log.d("CrisisPinService", "✅ New alert dispatched: $message")

        // 1. Broadcast to UI (LocalBroadcastManager — guaranteed same-process delivery)
        localBroadcast.sendBroadcast(
            Intent(ACTION_ALERT_RECEIVED).putExtra(EXTRA_MESSAGE, message)
        )

        // 2. Heads-up notification (only fires once per cooldown window)
        showAlertNotification(message)

        // 3. Vibrate + TTS
        alertManager?.triggerAlert(message)
    }

    private fun startMeshRelay(alertType: String) {
        val now = System.currentTimeMillis()
        // Uses SEPARATE relay map — completely independent from alert cooldown
        val lastRelay = lastRelayTime[alertType] ?: 0L
        if (now - lastRelay < RELAY_DEBOUNCE_MS) {
            Log.d("CrisisPinService", "Relay debounced for '$alertType'")
            return
        }
        lastRelayTime[alertType] = now
        isRelaying = true
        bleAdvertiser?.startAdvertising(alertType)
        localBroadcast.sendBroadcast(
            Intent(ACTION_RELAY_STATE_CHANGED).putExtra(EXTRA_RELAY_ACTIVE, true)
        )
        updateServiceNotification("📡 Relaying $alertType — tap Stop to end")
        Log.d("CrisisPinService", "Mesh relay started: $alertType")
    }

    private fun stopMeshRelay() {
        if (!isRelaying) return
        isRelaying = false
        bleAdvertiser?.stopAdvertising()
        localBroadcast.sendBroadcast(
            Intent(ACTION_RELAY_STATE_CHANGED).putExtra(EXTRA_RELAY_ACTIVE, false)
        )
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_SERVICE_ID, buildSilentNotification())
        Log.d("CrisisPinService", "Mesh relay stopped")
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(CHANNEL_ID, "CrisisPin Service", NotificationManager.IMPORTANCE_MIN)
                .apply { setShowBadge(false); setSound(null, null); enableVibration(false) }
                .also { getSystemService(NotificationManager::class.java).createNotificationChannel(it) }

            NotificationChannel(CHANNEL_ALERT_ID, "Emergency Alerts", NotificationManager.IMPORTANCE_HIGH)
                .apply { enableVibration(true); enableLights(true) }
                .also { getSystemService(NotificationManager::class.java).createNotificationChannel(it) }
        }
    }

    private fun buildSilentNotification(): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            packageManager.getLaunchIntentForPackage(packageName), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CrisisPin").setContentText("Listening for emergency alerts")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pi).setOngoing(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN).build()
    }

    private fun updateServiceNotification(text: String) {
        val pi = PendingIntent.getActivity(this, 0,
            packageManager.getLaunchIntentForPackage(packageName), PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CrisisPin Active").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pi).setOngoing(true).setSilent(true).build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_SERVICE_ID, notif)
    }

    private fun showAlertNotification(message: String) {
        // FIX: Pass the alert message in the Intent so that tapping the notification
        // opens IncomingAlert screen directly, whether app is cold-started or resumed.
        // Requires android:launchMode="singleTop" on MainActivity in AndroidManifest.xml.
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            putExtra("show_alert_message", message)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(this, NOTIF_ALERT_ID,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val title = when (message.uppercase()) {
            "SOS"   -> "🚨 SOS Emergency Nearby!"
            "MED"   -> "🏥 Medical Emergency Nearby!"
            "FIRE"  -> "🔥 Fire Alert Nearby!"
            "PANIC" -> "⚠️ Panic Alert Nearby!"
            "HELP"  -> "🆘 Help Needed Nearby!"
            else    -> "⚠️ Emergency: $message"
        }
        NotificationCompat.Builder(this, CHANNEL_ALERT_ID)
            .setContentTitle(title).setContentText("Tap to open CrisisPin and respond")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pi).setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pi, true)
            .build()
            .also { getSystemService(NotificationManager::class.java).notify(NOTIF_ALERT_ID, it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        bleAdvertiser?.stopAdvertising()
        bleScanner?.stopScanning()
        alertManager?.release()
        try { unregisterReceiver(bluetoothStateReceiver) } catch (e: Exception) { }
    }
}