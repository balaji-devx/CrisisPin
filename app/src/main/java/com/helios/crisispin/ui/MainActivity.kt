package com.helios.crisispin.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.helios.crisispin.service.CrisisPinService
import com.helios.crisispin.ui.screens.*
import com.helios.crisispin.ui.theme.*
import com.helios.crisispin.utils.PermissionHelper

sealed class Screen {
    object Splash     : Screen()
    object Onboarding : Screen()
    object Permission : Screen()
    object Home       : Screen()
    data class AlertSent(val alertType: String) : Screen()
    data class IncomingAlert(val alertType: String) : Screen()
    object Alerts     : Screen()   // received alerts only
    object History    : Screen()   // all alerts (sent + received)
    object Settings   : Screen()
}

class MainActivity : ComponentActivity() {

    companion object {
        // Used by notification PendingIntent to pass the alert message on tap
        const val EXTRA_SHOW_ALERT = "show_alert_message"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var localBroadcast: LocalBroadcastManager

    private var bleActiveState       by mutableStateOf(false)
    private var isBroadcastingState  by mutableStateOf(false)
    private var isRelayingState      by mutableStateOf(false)
    private var receivedMessageState by mutableStateOf("No Alerts")
    private var nearbyDevicesState   by mutableStateOf(0)
    private var alertsReceivedState  by mutableStateOf(0)
    private var currentScreen        by mutableStateOf<Screen>(Screen.Splash)
    private var alertHistoryState    by mutableStateOf<List<AlertHistoryItem>>(emptyList())
    private var alertSoundEnabled    by mutableStateOf(true)
    private var vibrationEnabled     by mutableStateOf(true)
    private var eventModeEnabled     by mutableStateOf(false)
    private var pendingIncomingAlert by mutableStateOf<String?>(null)
    private var pendingIncomingMsgEncoded: String? = null  // full encoded CrisisMessage for relay
    private var showBtBottomSheet    by mutableStateOf(false)

    @Volatile private var isShowingIncomingAlert = false

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        bleActiveState = adapter.isEnabled
        if (adapter.isEnabled) startCrisisPinService()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> startCrisisPinService() }

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                CrisisPinService.ACTION_ALERT_RECEIVED -> {
                    val message = intent.getStringExtra(CrisisPinService.EXTRA_MESSAGE) ?: return
                    receivedMessageState = message
                    alertsReceivedState++
                    nearbyDevicesState = (nearbyDevicesState + 1).coerceAtMost(99)
                    alertHistoryState = loadHistoryFromPrefs()
                    val encoded = intent.getStringExtra(CrisisPinService.EXTRA_MSG_ENCODED)
                    if (!isShowingIncomingAlert) showIncomingAlert(message, encoded)
                }
                CrisisPinService.ACTION_BLE_STATE_CHANGED ->
                    bleActiveState = intent.getBooleanExtra(CrisisPinService.EXTRA_BLE_ACTIVE, false)
                CrisisPinService.ACTION_RELAY_STATE_CHANGED ->
                    isRelayingState = intent.getBooleanExtra(CrisisPinService.EXTRA_RELAY_ACTIVE, false)
            }
        }
    }

    private fun showIncomingAlert(message: String, encodedMsg: String? = null) {
        pendingIncomingAlert = message
        pendingIncomingMsgEncoded = encodedMsg
        isShowingIncomingAlert = true
        currentScreen = Screen.IncomingAlert(message)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("crisispin_prefs", Context.MODE_PRIVATE)
        localBroadcast = LocalBroadcastManager.getInstance(this)
        alertSoundEnabled = prefs.getBoolean("sound_enabled", true)
        vibrationEnabled  = prefs.getBoolean("vibration_enabled", true)

        val filter = IntentFilter().apply {
            addAction(CrisisPinService.ACTION_ALERT_RECEIVED)
            addAction(CrisisPinService.ACTION_BLE_STATE_CHANGED)
            addAction(CrisisPinService.ACTION_RELAY_STATE_CHANGED)
        }
        localBroadcast.registerReceiver(serviceReceiver, filter)
        bleActiveState = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter.isEnabled
        alertHistoryState = loadHistoryFromPrefs()
        requestBatteryOptimizationExemption()

        // Handle cold-start from notification tap
        handleIncomingIntent(intent)

        setContent {
            CrisisPinTheme {
                AppNavigation()
                BluetoothBottomSheet()
            }
        }
    }

    // Called when activity is already alive and notification is tapped (requires singleTop in manifest)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val alertMessage = intent?.getStringExtra(EXTRA_SHOW_ALERT) ?: return
        if (alertMessage.isNotBlank() && !isShowingIncomingAlert) {
            // 300ms delay lets Compose initialize before navigating on cold start
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                showIncomingAlert(alertMessage)
            }, 300)
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                } catch (e: Exception) { }
            }
        }
    }

    private fun loadHistoryFromPrefs(): List<AlertHistoryItem> {
        val hp = getSharedPreferences("crisispin_history", Context.MODE_PRIVATE)
        val ids = hp.getStringSet("history_ids", emptySet()) ?: return emptyList()
        return ids.mapNotNull { id ->
            val label    = hp.getString("h_label_$id", null) ?: return@mapNotNull null
            val emoji    = hp.getString("h_emoji_$id", "🚨") ?: "🚨"
            val colorHex = hp.getInt("h_color_$id", 0xFFE53935.toInt())
            val ts       = hp.getLong("h_ts_$id", 0L)
            val dir      = hp.getString("h_dir_$id", "received") ?: "received"
            AlertHistoryItem(id = id, type = label, emoji = emoji,
                colorHex = colorHex, timestampMs = ts, direction = dir)
        }.sortedByDescending { it.timestampMs }
    }

    private fun saveHistoryEntry(alertType: String, direction: String) {
        val hp = getSharedPreferences("crisispin_history", Context.MODE_PRIVATE)
        val ids = hp.getStringSet("history_ids", mutableSetOf())!!.toMutableSet()
        val id = "${System.currentTimeMillis()}_${alertType}_$direction"
        val label = when (alertType.uppercase()) {
            "SOS" -> "SOS Emergency"; "MED" -> "Medical Alert"
            "FIRE" -> "Fire Alert";   "PANIC" -> "Panic Alert"
            "HELP" -> "General Help"; else -> "$alertType Alert"
        }
        val emoji    = when (alertType.uppercase()) { "MED"->"🏥";"FIRE"->"🔥";"PANIC"->"⚠️";"HELP"->"🆘";else->"🚨" }
        val colorHex = when (alertType.uppercase()) {
            "MED"->0xFF1E88E5.toInt();"FIRE"->0xFFFF9800.toInt()
            "PANIC"->0xFF9C27B0.toInt();"HELP"->0xFF43A047.toInt();else->0xFFE53935.toInt()
        }
        hp.edit()
            .putStringSet("history_ids", ids + id)
            .putString("h_label_$id", label).putString("h_emoji_$id", emoji)
            .putInt("h_color_$id", colorHex).putLong("h_ts_$id", System.currentTimeMillis())
            .putString("h_dir_$id", direction).apply()
    }

    private fun dismissAlert() {
        isShowingIncomingAlert = false
        pendingIncomingAlert = null
        pendingIncomingMsgEncoded = null
        startService(Intent(this, CrisisPinService::class.java).apply {
            action = CrisisPinService.ACTION_DISMISS_ALERT
        })
    }

    @Composable
    fun AppBackHandler() {
        val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        DisposableEffect(currentScreen) {
            val callback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when (currentScreen) {
                        is Screen.History, is Screen.Alerts,
                        is Screen.Settings, is Screen.AlertSent -> currentScreen = Screen.Home
                        is Screen.IncomingAlert -> { dismissAlert(); currentScreen = Screen.Home }
                        is Screen.Home -> moveTaskToBack(true)
                        else -> { }
                    }
                }
            }
            backDispatcher?.addCallback(callback)
            onDispose { callback.remove() }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun BluetoothBottomSheet() {
        if (showBtBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBtBottomSheet = false },
                containerColor = NavyLight,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 28.dp).padding(bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(20.dp))
                        .background(EmergencyRed.copy(0.15f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.BluetoothDisabled, null, tint = EmergencyRed, modifier = Modifier.size(36.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("Bluetooth is Off", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("CrisisPin needs Bluetooth to scan for nearby alerts and broadcast your own.\n\nWithout it, you won't receive alerts from others.",
                        color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 21.sp)
                    Spacer(Modifier.height(28.dp))
                    Button(onClick = { showBtBottomSheet = false; enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                        modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)) {
                        Icon(Icons.Rounded.Bluetooth, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Enable Bluetooth", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { showBtBottomSheet = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("Continue without Bluetooth", color = TextMuted, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    @Composable
    fun AppNavigation() {
        AppBackHandler()
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                when (targetState) {
                    is Screen.Home -> slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                    is Screen.History, is Screen.Alerts, is Screen.Settings ->
                        slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                    else -> fadeIn() togetherWith fadeOut()
                }
            }, label = "nav"
        ) { screen ->
            when (screen) {
                is Screen.Splash -> SplashScreen(onFinished = {
                    val done = prefs.getBoolean("onboarding_done", false)
                    currentScreen = when {
                        !done -> Screen.Onboarding
                        !PermissionHelper.hasPermissions(this@MainActivity) -> Screen.Permission
                        else -> Screen.Home
                    }
                })
                is Screen.Onboarding -> OnboardingScreen(onFinished = {
                    prefs.edit().putBoolean("onboarding_done", true).apply()
                    currentScreen = if (PermissionHelper.hasPermissions(this@MainActivity)) Screen.Home else Screen.Permission
                })
                is Screen.Permission -> PermissionScreen(onPermissionsGranted = {
                    permissionLauncher.launch(PermissionHelper.getRequiredPermissions())
                    currentScreen = Screen.Home
                })
                is Screen.Home -> {
                    LaunchedEffect(Unit) {
                        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
                        if (!adapter.isEnabled) showBtBottomSheet = true else startCrisisPinService()
                    }
                    HomeScreen(
                        bleActive = bleActiveState, isBroadcasting = isBroadcastingState,
                        isRelaying = isRelayingState, nearbyDevices = nearbyDevicesState,
                        alertsReceived = alertsReceivedState, receivedMessage = receivedMessageState,
                        eventModeEnabled = eventModeEnabled, userRole = "user", onEventModeToggle = { eventModeEnabled = it },
                        onSendAlert = { alertType ->
                            val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
                            if (!adapter.isEnabled) { showBtBottomSheet = true; return@HomeScreen }
                            startService(Intent(this@MainActivity, CrisisPinService::class.java).apply {
                                action = CrisisPinService.ACTION_START_ADVERTISING
                                putExtra(CrisisPinService.EXTRA_ALERT_TYPE, alertType)
                            })
                            isBroadcastingState = true
                            saveHistoryEntry(alertType, "sent")
                            alertHistoryState = loadHistoryFromPrefs()
                            currentScreen = Screen.AlertSent(alertType)
                        },
                        onStopAlert = {
                            startService(Intent(this@MainActivity, CrisisPinService::class.java).apply {
                                action = CrisisPinService.ACTION_STOP_ADVERTISING
                            })
                            isBroadcastingState = false
                        },
                        onStopRelay = {
                            startService(Intent(this@MainActivity, CrisisPinService::class.java).apply {
                                action = CrisisPinService.ACTION_STOP_RELAY
                            })
                        },
                        onNavigate = { dest ->
                            currentScreen = when (dest) {
                                "alerts"   -> Screen.Alerts    // received only
                                "history"  -> Screen.History   // all
                                "settings" -> Screen.Settings
                                else       -> Screen.Home
                            }
                        }
                    )
                }
                is Screen.AlertSent -> AlertSentScreen(alertType = screen.alertType, onCancel = {
                    startService(Intent(this@MainActivity, CrisisPinService::class.java).apply {
                        action = CrisisPinService.ACTION_STOP_ADVERTISING
                    })
                    isBroadcastingState = false; currentScreen = Screen.Home
                })
                is Screen.IncomingAlert -> IncomingAlertScreen(
                    alertType = screen.alertType,
                    onAcknowledge = {
                        pendingIncomingAlert?.let { alertType ->
                            startService(Intent(this@MainActivity, CrisisPinService::class.java).apply {
                                action = CrisisPinService.ACTION_START_RELAY
                                putExtra(CrisisPinService.EXTRA_ALERT_TYPE, alertType)
                                pendingIncomingMsgEncoded?.let { putExtra(CrisisPinService.EXTRA_MSG_ENCODED, it) }
                            })
                        }
                        dismissAlert(); currentScreen = Screen.Home
                    },
                    onIgnore = { dismissAlert(); currentScreen = Screen.Home },
                    onCallSecurity = { dismissAlert(); currentScreen = Screen.Home }
                )
                // Alerts = received only
                is Screen.Alerts -> AlertHistoryScreen(
                    alerts = alertHistoryState.filter { it.direction == "received" },
                    title = "Received Alerts",
                    onBack = { currentScreen = Screen.Home }
                )
                // History = everything
                is Screen.History -> AlertHistoryScreen(
                    alerts = alertHistoryState,
                    title = "Alert History",
                    onBack = { currentScreen = Screen.Home }
                )
                is Screen.Settings -> SettingsScreen(
                    bleActive = bleActiveState, alertSoundEnabled = alertSoundEnabled,
                    vibrationEnabled = vibrationEnabled,
                    onAlertSoundToggle = { enabled ->
                        alertSoundEnabled = enabled
                        prefs.edit().putBoolean("sound_enabled", enabled).apply()
                        startService(Intent(this@MainActivity, CrisisPinService::class.java).apply {
                            action = if (enabled) CrisisPinService.ACTION_SOUND_ENABLED else CrisisPinService.ACTION_SOUND_DISABLED
                        })
                    },
                    onVibrationToggle = { enabled ->
                        vibrationEnabled = enabled
                        prefs.edit().putBoolean("vibration_enabled", enabled).apply()
                        startService(Intent(this@MainActivity, CrisisPinService::class.java).apply {
                            action = if (enabled) CrisisPinService.ACTION_VIBRATION_ENABLED else CrisisPinService.ACTION_VIBRATION_DISABLED
                        })
                    },
                    onBluetoothToggle = { enable ->
                        if (enable) enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        else @Suppress("DEPRECATION") (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter.disable()
                    },
                    onBack = { currentScreen = Screen.Home }
                )
            }
        }
    }

    private fun startCrisisPinService() {
        if (PermissionHelper.hasPermissions(this)) CrisisPinService.startService(this)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (PermissionHelper.hasPermissions(this)) startCrisisPinService()
    }

    override fun onResume() {
        super.onResume()
        alertHistoryState = loadHistoryFromPrefs()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { localBroadcast.unregisterReceiver(serviceReceiver) } catch (e: Exception) { }
    }
}