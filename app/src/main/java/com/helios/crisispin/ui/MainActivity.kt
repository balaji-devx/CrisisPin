package com.helios.crisispin.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.runtime.*
import com.helios.crisispin.ble.BleAdvertiser
import com.helios.crisispin.ble.BleScanner
import com.helios.crisispin.ui.screens.*
import com.helios.crisispin.ui.theme.CrisisPinTheme
import com.helios.crisispin.utils.AlertManager
import com.helios.crisispin.utils.PermissionHelper

sealed class Screen {
    object Splash : Screen()
    object Onboarding : Screen()
    object Permission : Screen()
    object Home : Screen()
    data class AlertSent(val alertType: String) : Screen()
    data class IncomingAlert(val alertType: String) : Screen()
    object History : Screen()
    object Settings : Screen()
}

class MainActivity : ComponentActivity() {

    private var bleAdvertiser: BleAdvertiser? = null
    private var bleScanner: BleScanner? = null
    private var alertManager: AlertManager? = null

    private var receivedMessageState by mutableStateOf("No Alerts")
    private var isBroadcastingState by mutableStateOf(false)
    private var bleActiveState by mutableStateOf(false)
    private var nearbyDevicesState by mutableStateOf(0)
    private var alertsReceivedState by mutableStateOf(0)
    private var currentScreen by mutableStateOf<Screen>(Screen.Splash)

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            when (state) {
                BluetoothAdapter.STATE_ON -> {
                    bleActiveState = true
                    bleScanner?.startScanning()
                }
                BluetoothAdapter.STATE_OFF -> {
                    bleActiveState = false
                    bleScanner?.stopScanning()
                    bleAdvertiser?.stopAdvertising()
                    isBroadcastingState = false
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        alertManager = AlertManager(this)

        if (PermissionHelper.hasPermissions(this)) {
            setupBle()
        }

        setContent {
            CrisisPinTheme {
                AppNavigation()
            }
        }
    }

    @Composable
    fun AppNavigation() {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "nav"
        ) { screen ->
            when (screen) {
                is Screen.Splash -> SplashScreen(
                    onFinished = { currentScreen = Screen.Onboarding }
                )
                is Screen.Onboarding -> OnboardingScreen(
                    onFinished = {
                        currentScreen = if (PermissionHelper.hasPermissions(this@MainActivity))
                            Screen.Home
                        else
                            Screen.Permission
                    }
                )
                is Screen.Permission -> PermissionScreen(
                    onPermissionsGranted = {
                        PermissionHelper.requestPermissions(this@MainActivity)
                        currentScreen = Screen.Home
                    }
                )
                is Screen.Home -> HomeScreen(
                    bleActive = bleActiveState,
                    isBroadcasting = isBroadcastingState,
                    nearbyDevices = nearbyDevicesState,
                    alertsReceived = alertsReceivedState,
                    receivedMessage = receivedMessageState,
                    onSendAlert = { alertType ->
                        bleAdvertiser?.startAdvertising(alertType)
                        isBroadcastingState = true
                        currentScreen = Screen.AlertSent(alertType)
                    },
                    onStopAlert = {
                        bleAdvertiser?.stopAdvertising()
                        isBroadcastingState = false
                    },
                    onNavigate = { dest ->
                        currentScreen = when (dest) {
                            "history" -> Screen.History
                            "settings" -> Screen.Settings
                            else -> Screen.Home
                        }
                    }
                )
                is Screen.AlertSent -> AlertSentScreen(
                    alertType = screen.alertType,
                    onCancel = {
                        bleAdvertiser?.stopAdvertising()
                        isBroadcastingState = false
                        currentScreen = Screen.Home
                    }
                )
                is Screen.IncomingAlert -> IncomingAlertScreen(
                    alertType = screen.alertType,
                    onAcknowledge = { currentScreen = Screen.Home },
                    onCallSecurity = { currentScreen = Screen.Home }
                )
                is Screen.History -> AlertHistoryScreen(
                    alerts = emptyList(),
                    onBack = { currentScreen = Screen.Home }
                )
                is Screen.Settings -> SettingsScreen(
                    bleActive = bleActiveState,
                    onBack = { currentScreen = Screen.Home }
                )
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (PermissionHelper.hasPermissions(this)) {
            setupBle()
        }
    }

    private fun setupBle() {
        bleAdvertiser = BleAdvertiser(this)
        bleScanner = BleScanner(this) { message ->
            runOnUiThread {
                receivedMessageState = message
                alertsReceivedState++
                nearbyDevicesState = (nearbyDevicesState + 1).coerceAtMost(99)
                alertManager?.triggerAlert(message)
                // Show incoming alert screen
                currentScreen = Screen.IncomingAlert(message)
            }
        }

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothStateReceiver, filter)

        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter.isEnabled) {
            bleActiveState = true
            bleScanner?.startScanning()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(bluetoothStateReceiver) } catch (e: Exception) { }
        bleAdvertiser?.stopAdvertising()
        bleScanner?.stopScanning()
        alertManager?.release()
    }
}