package com.helios.crisispin.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
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
    object Login      : Screen()
    object Home       : Screen()
    data class AlertSent(val alertType: String) : Screen()
    data class IncomingAlert(val alertType: String) : Screen()
    object History    : Screen()
    object Settings   : Screen()
}

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var lbm: LocalBroadcastManager

    private var bleActive        by mutableStateOf(false)
    private var isBroadcasting   by mutableStateOf(false)
    private var isRelaying       by mutableStateOf(false)
    private var lastMessage      by mutableStateOf("No Alerts")
    private var deviceCount      by mutableStateOf(0)   // unique BLE devices seen
    private var alertsReceived   by mutableStateOf(0)   // total alerts received
    private var screen           by mutableStateOf<Screen>(Screen.Splash)
    private var history          by mutableStateOf<List<AlertHistoryItem>>(emptyList())
    private var soundOn          by mutableStateOf(true)
    private var vibOn            by mutableStateOf(true)
    private var eventMode        by mutableStateOf(false)
    private var pendingAlertType by mutableStateOf<String?>(null)
    private var showBtSheet      by mutableStateOf(false)
    private var alertScreenOpen  = false

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val ok = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter.isEnabled
        bleActive = ok
        if (ok) startCrisisService()
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> startCrisisService() }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                CrisisPinService.ACTION_ALERT_RECEIVED -> {
                    val msg = intent.getStringExtra(CrisisPinService.EXTRA_MESSAGE)
                    val devCount = intent.getIntExtra(CrisisPinService.EXTRA_DEVICE_COUNT, -1)

                    if (devCount >= 0) deviceCount = devCount

                    if (msg != null) {
                        lastMessage = msg
                        alertsReceived++
                        addHistory(msg, "received")
                        if (!alertScreenOpen) {
                            alertScreenOpen = true
                            pendingAlertType = msg
                            screen = Screen.IncomingAlert(msg)
                        }
                    }
                }
                CrisisPinService.ACTION_ALERT_BLOCKED -> {
                    // Show toast: alert from this type is blocked for N seconds
                    val type = intent.getStringExtra(CrisisPinService.EXTRA_BLOCKED_TYPE) ?: ""
                    val remaining = intent.getLongExtra(CrisisPinService.EXTRA_UNBLOCK_TIME, 120L)
                    val mins = remaining / 60
                    val secs = remaining % 60
                    val timeStr = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
                    Toast.makeText(
                        this@MainActivity,
                        "⚠️ $type alerts blocked for $timeStr",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                CrisisPinService.ACTION_BLE_STATE_CHANGED ->
                    bleActive = intent.getBooleanExtra(CrisisPinService.EXTRA_BLE_ACTIVE, false)
                CrisisPinService.ACTION_RELAY_STATE_CHANGED ->
                    isRelaying = intent.getBooleanExtra(CrisisPinService.EXTRA_RELAY_ACTIVE, false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("cp_prefs", Context.MODE_PRIVATE)
        lbm   = LocalBroadcastManager.getInstance(this)
        soundOn = prefs.getBoolean("sound", true)
        vibOn   = prefs.getBoolean("vib", true)

        lbm.registerReceiver(receiver, IntentFilter().apply {
            addAction(CrisisPinService.ACTION_ALERT_RECEIVED)
            addAction(CrisisPinService.ACTION_ALERT_BLOCKED)
            addAction(CrisisPinService.ACTION_BLE_STATE_CHANGED)
            addAction(CrisisPinService.ACTION_RELAY_STATE_CHANGED)
        })

        bleActive = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter.isEnabled
        setContent { CrisisPinTheme { Nav(); BtSheet() } }
    }

    override fun onResume() {
        super.onResume()
        // Re-register receiver in case it was unregistered
        // Sync state from service — also re-delivers any missed alert
        startService(Intent(this, CrisisPinService::class.java).apply {
            action = CrisisPinService.ACTION_SYNC_STATE
        })
    }

    private fun dismiss(alertType: String? = pendingAlertType) {
        alertScreenOpen = false
        startService(Intent(this, CrisisPinService::class.java).apply {
            action = CrisisPinService.ACTION_DISMISS_ALERT
            putExtra(CrisisPinService.EXTRA_ALERT_TYPE, alertType)
        })
        pendingAlertType = null
    }

    private fun startCrisisService() {
        if (PermissionHelper.hasPermissions(this)) CrisisPinService.startService(this)
    }

    @Composable
    fun BackHandler() {
        val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        DisposableEffect(screen) {
            val cb = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when (screen) {
                        is Screen.History, is Screen.Settings, is Screen.AlertSent ->
                            screen = Screen.Home
                        is Screen.IncomingAlert -> {
                            dismiss((screen as Screen.IncomingAlert).alertType)
                            screen = Screen.Home
                        }
                        is Screen.Home -> moveTaskToBack(true)
                        else -> {}
                    }
                }
            }
            dispatcher?.addCallback(cb)
            onDispose { cb.remove() }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun BtSheet() {
        if (!showBtSheet) return
        ModalBottomSheet(
            onDismissRequest = { showBtSheet = false },
            containerColor = NavyLight,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp))
                    .background(EmergencyRed.copy(0.15f)), Alignment.Center) {
                    Icon(Icons.Rounded.BluetoothDisabled, null,
                        tint = EmergencyRed, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.height(20.dp))
                Text("Bluetooth is Off", color = Color.White,
                    fontWeight = FontWeight.Black, fontSize = 22.sp)
                Spacer(Modifier.height(10.dp))
                Text("CrisisPin needs Bluetooth to detect and broadcast alerts.",
                    color = TextSecondary, fontSize = 14.sp,
                    textAlign = TextAlign.Center, lineHeight = 21.sp)
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = {
                        showBtSheet = false
                        enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) {
                    Icon(Icons.Rounded.Bluetooth, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Enable Bluetooth", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { showBtSheet = false }, Modifier.fillMaxWidth()) {
                    Text("Continue without Bluetooth", color = TextMuted, fontSize = 13.sp)
                }
            }
        }
    }

    @Composable
    fun Nav() {
        BackHandler()
        AnimatedContent(
            screen,
            transitionSpec = {
                when (targetState) {
                    is Screen.Home ->
                        slideInHorizontally { -it } + fadeIn() togetherWith
                                slideOutHorizontally { it } + fadeOut()
                    is Screen.History, is Screen.Settings ->
                        slideInHorizontally { it } + fadeIn() togetherWith
                                slideOutHorizontally { -it } + fadeOut()
                    else -> fadeIn() togetherWith fadeOut()
                }
            }, label = "nav"
        ) { s ->
            when (s) {
                is Screen.Splash -> SplashScreen {
                    screen = when {
                        !prefs.getBoolean("onboarded", false) -> Screen.Onboarding
                        !PermissionHelper.hasPermissions(this@MainActivity) -> Screen.Permission
                        !prefs.getBoolean("logged_in", false) -> Screen.Login
                        else -> Screen.Home
                    }
                }
                is Screen.Onboarding -> OnboardingScreen {
                    prefs.edit().putBoolean("onboarded", true).apply()
                    screen = if (PermissionHelper.hasPermissions(this@MainActivity))
                        Screen.Login else Screen.Permission
                }
                is Screen.Permission -> PermissionScreen {
                    permLauncher.launch(PermissionHelper.getRequiredPermissions())
                    screen = Screen.Login
                }
                is Screen.Login -> LoginScreen(
                    onLoginSuccess = { role ->
                        prefs.edit()
                            .putBoolean("logged_in", true)
                            .putString("user_role", role)
                            .apply()
                        startCrisisService()
                        screen = Screen.Home
                    }
                )
                is Screen.Home -> {
                    LaunchedEffect(Unit) {
                        startCrisisService()
                        val btOn = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter.isEnabled
                        if (!btOn) showBtSheet = true
                    }
                    val userRole = prefs.getString("user_role", "user") ?: "user"
                    HomeScreen(
                        bleActive        = bleActive,
                        isBroadcasting   = isBroadcasting,
                        isRelaying       = isRelaying,
                        nearbyDevices    = deviceCount,
                        alertsReceived   = alertsReceived,
                        receivedMessage  = lastMessage,
                        eventModeEnabled = eventMode,
                        userRole         = userRole,
                        onEventModeToggle = { eventMode = it },
                        onSendAlert = { type ->
                            val btOn = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter.isEnabled
                            if (!btOn) { showBtSheet = true; return@HomeScreen }
                            startService(Intent(this@MainActivity, CrisisPinService::class.java).apply {
                                action = CrisisPinService.ACTION_START_ADVERTISING
                                putExtra(CrisisPinService.EXTRA_ALERT_TYPE, type)
                            })
                            isBroadcasting = true
                            addHistory(type, "sent")
                            screen = Screen.AlertSent(type)
                        },
                        onStopAlert = {
                            startService(Intent(this@MainActivity, CrisisPinService::class.java).apply {
                                action = CrisisPinService.ACTION_STOP_ADVERTISING
                            })
                            isBroadcasting = false
                        },
                        onStopRelay = {
                            startService(Intent(this@MainActivity, CrisisPinService::class.java).apply {
                                action = CrisisPinService.ACTION_STOP_RELAY
                            })
                        },
                        onNavigate = { dest ->
                            screen = if (dest == "settings") Screen.Settings else Screen.History
                        }
                    )
                }
                is Screen.AlertSent -> AlertSentScreen(s.alertType) {
                    startService(Intent(this@MainActivity, CrisisPinService::class.java).apply {
                        action = CrisisPinService.ACTION_STOP_ADVERTISING
                    })
                    isBroadcasting = false
                    screen = Screen.Home
                }
                is Screen.IncomingAlert -> IncomingAlertScreen(
                    alertType = s.alertType,
                    onAcknowledge = {
                        startService(Intent(this@MainActivity, CrisisPinService::class.java).apply {
                            action = CrisisPinService.ACTION_START_RELAY
                            putExtra(CrisisPinService.EXTRA_ALERT_TYPE, s.alertType)
                        })
                        dismiss(s.alertType); screen = Screen.Home
                    },
                    onIgnore       = { dismiss(s.alertType); screen = Screen.Home },
                    onCallSecurity = { dismiss(s.alertType); screen = Screen.Home }
                )
                is Screen.History  -> AlertHistoryScreen(history) { screen = Screen.Home }
                is Screen.Settings -> SettingsScreen(
                    bleActive         = bleActive,
                    alertSoundEnabled = soundOn,
                    vibrationEnabled  = vibOn,
                    onAlertSoundToggle = { e ->
                        soundOn = e; prefs.edit().putBoolean("sound", e).apply()
                        startService(Intent(this@MainActivity, CrisisPinService::class.java).apply {
                            action = if (e) CrisisPinService.ACTION_SOUND_ENABLED
                            else   CrisisPinService.ACTION_SOUND_DISABLED
                        })
                    },
                    onVibrationToggle = { e ->
                        vibOn = e; prefs.edit().putBoolean("vib", e).apply()
                        startService(Intent(this@MainActivity, CrisisPinService::class.java).apply {
                            action = if (e) CrisisPinService.ACTION_VIB_ENABLED
                            else   CrisisPinService.ACTION_VIB_DISABLED
                        })
                    },
                    onBluetoothToggle = { e ->
                        if (e) enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        else @Suppress("DEPRECATION")
                        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter.disable()
                    },
                    onLogout = {
                        prefs.edit().putBoolean("logged_in", false).apply()
                        screen = Screen.Login
                    },
                    onBack = { screen = Screen.Home }
                )
            }
        }
    }

    private fun addHistory(type: String, dir: String) {
        val label = when (type.uppercase()) {
            "SOS" -> "SOS Emergency"; "MED" -> "Medical Alert"
            "FIRE" -> "Fire Alert"; "PANIC" -> "Panic Alert"
            "HELP" -> "General Help"; else -> "$type Alert"
        }
        val emoji = when (type.uppercase()) { "MED"->"🏥";"FIRE"->"🔥";"PANIC"->"⚠️";"HELP"->"🆘";else->"🚨" }
        val color = when (type.uppercase()) {
            "MED"->0xFF1E88E5.toInt();"FIRE"->0xFFFF9800.toInt()
            "PANIC"->0xFF9C27B0.toInt();"HELP"->0xFF43A047.toInt();else->0xFFE53935.toInt()
        }
        history = listOf(AlertHistoryItem(
            System.currentTimeMillis().toString(), label, emoji, color,
            System.currentTimeMillis(), dir)) + history
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(rc: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(rc, perms, results)
        startCrisisService()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { lbm.unregisterReceiver(receiver) } catch (e: Exception) { }
    }
}