package com.helios.crisispin.service

import android.Manifest
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
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.helios.crisispin.ble.BleAdvertiser
import com.helios.crisispin.ble.BleScanner
import com.helios.crisispin.utils.AlertManager
import com.helios.crisispin.utils.CrisisMessage

class CrisisPinService : Service() {

    companion object {
        const val CHANNEL_ID         = "crisispin_service"
        const val CHANNEL_ALERT_ID   = "crisispin_alerts"
        const val NOTIF_SERVICE_ID   = 1
        const val NOTIF_ALERT_ID     = 2

        const val ACTION_ALERT_RECEIVED      = "com.helios.crisispin.ALERT_RECEIVED"
        const val ACTION_BLE_STATE_CHANGED   = "com.helios.crisispin.BLE_STATE"
        const val ACTION_RELAY_STATE_CHANGED = "com.helios.crisispin.RELAY_STATE"
        const val ACTION_ALERT_BLOCKED       = "com.helios.crisispin.ALERT_BLOCKED"
        const val EXTRA_MESSAGE      = "message"
        const val EXTRA_MSG_ENCODED  = "msg_encoded"
        const val EXTRA_BLE_ACTIVE   = "ble_active"
        const val EXTRA_RELAY_ACTIVE = "relay_active"
        const val EXTRA_BLOCKED_TYPE = "blocked_type"
        const val EXTRA_UNBLOCK_TIME = "unblock_time"
        const val EXTRA_DEVICE_COUNT = "device_count"

        const val ACTION_START_ADVERTISING  = "com.helios.crisispin.START_ADV"
        const val ACTION_STOP_ADVERTISING   = "com.helios.crisispin.STOP_ADV"
        const val ACTION_START_RELAY        = "com.helios.crisispin.START_RELAY"
        const val ACTION_STOP_RELAY         = "com.helios.crisispin.STOP_RELAY"
        const val ACTION_SOUND_ENABLED      = "com.helios.crisispin.SOUND_ON"
        const val ACTION_SOUND_DISABLED     = "com.helios.crisispin.SOUND_OFF"
        const val ACTION_VIB_ENABLED  = "com.helios.crisispin.VIB_ON"
        const val ACTION_VIB_DISABLED = "com.helios.crisispin.VIB_OFF"
        const val ACTION_DISMISS_ALERT      = "com.helios.crisispin.DISMISS_ALERT"
        const val ACTION_SYNC_STATE        = "com.helios.crisispin.SYNC_STATE"
        const val EXTRA_ALERT_TYPE          = "alert_type"

        const val ALERT_COOLDOWN_MS = 8_000L
        const val RELAY_DEBOUNCE_MS = 60_000L

        fun startService(context: Context) {
            val intent = Intent(context, CrisisPinService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
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

    private val lastAlertFireTime = mutableMapOf<String, Long>() // keyed by msgId
    private val lastRelayTime     = mutableMapOf<String, Long>() // keyed by alertType
    private var alertIsActive = false
    private var lastReceivedMsg: CrisisMessage? = null

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> {
                    bleScanner?.startScanning()
                    localBroadcast.sendBroadcast(Intent(ACTION_BLE_STATE_CHANGED).putExtra(EXTRA_BLE_ACTIVE, true))
                }
                BluetoothAdapter.STATE_OFF -> {
                    bleScanner?.stopScanning()
                    bleAdvertiser?.stopAdvertising()
                    isRelaying = false; alertIsActive = false
                    localBroadcast.sendBroadcast(Intent(ACTION_BLE_STATE_CHANGED).putExtra(EXTRA_BLE_ACTIVE, false))
                    localBroadcast.sendBroadcast(Intent(ACTION_RELAY_STATE_CHANGED).putExtra(EXTRA_RELAY_ACTIVE, false))
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onCreate() {
        super.onCreate()
        alertIsActive = false
        localBroadcast = LocalBroadcastManager.getInstance(this)
        createNotificationChannels()
        startForeground(NOTIF_SERVICE_ID, buildSilentNotification())
        alertManager  = AlertManager(this)
        bleAdvertiser = BleAdvertiser(this)
        bleScanner    = BleScanner(this) { msg -> onAlertReceived(msg) }
        registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter.isEnabled) bleScanner?.startScanning()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ADVERTISING -> {
                val alertType = intent.getStringExtra(EXTRA_ALERT_TYPE) ?: "SOS"
                bleAdvertiser?.startAdvertising(alertType)
            }
            ACTION_STOP_ADVERTISING -> bleAdvertiser?.stopAdvertising()
            ACTION_START_RELAY -> {
                val encoded = intent.getStringExtra(EXTRA_MSG_ENCODED)
                val msg = (if (encoded != null) CrisisMessage.decode(encoded) else null) ?: lastReceivedMsg
                if (msg != null) startMeshRelay(msg)
            }
            ACTION_STOP_RELAY -> stopMeshRelay()
            ACTION_DISMISS_ALERT -> { alertIsActive = false }
            ACTION_SOUND_ENABLED    -> alertManager?.setSoundEnabled(true)
            ACTION_SOUND_DISABLED   -> alertManager?.setSoundEnabled(false)
            ACTION_VIB_ENABLED  -> alertManager?.setVibrationEnabled(true)
            ACTION_VIB_DISABLED -> alertManager?.setVibrationEnabled(false)
        }
        return START_STICKY
    }

    private fun onAlertReceived(msg: CrisisMessage) {
        val now = System.currentTimeMillis()
        val lastFire = lastAlertFireTime[msg.msgId] ?: 0L
        if (now - lastFire < ALERT_COOLDOWN_MS) return

        lastAlertFireTime[msg.msgId] = now
        lastReceivedMsg = msg
        saveToHistory(msg.type, "received")

        if (alertIsActive) {
            Log.d("CrisisPinService", "UI busy — history saved for ${msg.msgId}")
            return
        }
        alertIsActive = true
        Log.d("CrisisPinService", "Dispatching: ${msg.type} from ${msg.originId}")

        localBroadcast.sendBroadcast(
            Intent(ACTION_ALERT_RECEIVED)
                .putExtra(EXTRA_MESSAGE, msg.type)
                .putExtra(EXTRA_MSG_ENCODED, CrisisMessage.encode(msg))
        )
        showAlertNotification(msg.type)
        alertManager?.triggerAlert(msg.type)
    }

    private fun startMeshRelay(msg: CrisisMessage) {
        val now = System.currentTimeMillis()
        if (now - (lastRelayTime[msg.type] ?: 0L) < RELAY_DEBOUNCE_MS) return
        lastRelayTime[msg.type] = now
        isRelaying = true
        bleAdvertiser?.startRelaying(msg)
        localBroadcast.sendBroadcast(Intent(ACTION_RELAY_STATE_CHANGED).putExtra(EXTRA_RELAY_ACTIVE, true))
        updateServiceNotification("Relaying ${msg.type} — tap Stop to end")
    }

    private fun stopMeshRelay() {
        if (!isRelaying) return
        isRelaying = false
        bleAdvertiser?.stopAdvertising()
        localBroadcast.sendBroadcast(Intent(ACTION_RELAY_STATE_CHANGED).putExtra(EXTRA_RELAY_ACTIVE, false))
        getSystemService(NotificationManager::class.java).notify(NOTIF_SERVICE_ID, buildSilentNotification())
    }

    private fun saveToHistory(alertType: String, direction: String) {
        val p = getSharedPreferences("crisispin_history", Context.MODE_PRIVATE)
        val ids = p.getStringSet("history_ids", mutableSetOf())!!.toMutableSet()
        val id  = "${System.currentTimeMillis()}_${alertType}_$direction"
        val label = when (alertType.uppercase()) {
            "SOS"->"SOS Emergency";"MED"->"Medical Alert";"FIRE"->"Fire Alert"
            "PANIC"->"Panic Alert";"HELP"->"General Help";else->"$alertType Alert"
        }
        val emoji    = when (alertType.uppercase()) { "MED"->"🏥";"FIRE"->"🔥";"PANIC"->"⚠️";"HELP"->"🆘";else->"🚨" }
        val colorHex = when (alertType.uppercase()) {
            "MED"->0xFF1E88E5.toInt();"FIRE"->0xFFFF9800.toInt()
            "PANIC"->0xFF9C27B0.toInt();"HELP"->0xFF43A047.toInt();else->0xFFE53935.toInt()
        }
        p.edit().putStringSet("history_ids", ids + id)
            .putString("h_label_$id", label).putString("h_emoji_$id", emoji)
            .putInt("h_color_$id", colorHex).putLong("h_ts_$id", System.currentTimeMillis())
            .putString("h_dir_$id", direction).apply()
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
            .setContentTitle("CrisisPin").setContentText("Listening for emergency alerts")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pi).setOngoing(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN).build()
    }

    private fun updateServiceNotification(text: String) {
        val pi = PendingIntent.getActivity(this, 0,
            packageManager.getLaunchIntentForPackage(packageName), PendingIntent.FLAG_IMMUTABLE)
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CrisisPin Active").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pi).setOngoing(true).setSilent(true).build()
            .also { getSystemService(NotificationManager::class.java).notify(NOTIF_SERVICE_ID, it) }
    }

    private fun showAlertNotification(alertType: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            putExtra("show_alert_message", alertType)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(this, NOTIF_ALERT_ID, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val title = when (alertType.uppercase()) {
            "SOS"->"🚨 SOS Emergency Nearby!";"MED"->"🏥 Medical Emergency Nearby!"
            "FIRE"->"🔥 Fire Alert Nearby!";"PANIC"->"⚠️ Panic Alert Nearby!"
            "HELP"->"🆘 Help Needed Nearby!";else->"⚠️ Emergency: $alertType"
        }
        NotificationCompat.Builder(this, CHANNEL_ALERT_ID)
            .setContentTitle(title).setContentText("Tap to open CrisisPin and respond")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pi).setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pi, true).build()
            .also { getSystemService(NotificationManager::class.java).notify(NOTIF_ALERT_ID, it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onDestroy() {
        super.onDestroy()
        bleAdvertiser?.stopAdvertising()
        bleScanner?.stopScanning()
        alertManager?.release()
        try { unregisterReceiver(bluetoothStateReceiver) } catch (e: Exception) { }
    }
}