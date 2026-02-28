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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.helios.crisispin.ble.BleAdvertiser
import com.helios.crisispin.ble.BleScanner
import com.helios.crisispin.ui.theme.CrisisPinTheme
import com.helios.crisispin.utils.AlertManager
import com.helios.crisispin.utils.PermissionHelper

class MainActivity : ComponentActivity() {

    private var bleAdvertiser: BleAdvertiser? = null
    private var bleScanner: BleScanner? = null
    private var alertManager: AlertManager? = null
    private var receivedMessageState by mutableStateOf("No Alerts")

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            when (state) {
                BluetoothAdapter.STATE_ON -> {
                    Log.d("BLE", "Bluetooth turned ON — starting scanner")
                    bleScanner?.startScanning()
                }
                BluetoothAdapter.STATE_OFF -> {
                    Log.d("BLE", "Bluetooth turned OFF — stopping scanner")
                    bleScanner?.stopScanning()
                    bleAdvertiser?.stopAdvertising()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        alertManager = AlertManager(this)

        if (PermissionHelper.hasPermissions(this)) {
            setupBle()
        } else {
            PermissionHelper.requestPermissions(this)
        }

        setContent {
            CrisisPinTheme {
                CrisisPinApp(
                    bleAdvertiser = bleAdvertiser,
                    receivedMessage = receivedMessageState
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
                alertManager?.triggerAlert(message) // vibrate + speak on receive
            }
        }

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothStateReceiver, filter)

        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter.isEnabled) {
            Log.d("BLE", "Bluetooth already ON — starting scanner immediately")
            bleScanner?.startScanning()
        } else {
            Log.d("BLE", "Bluetooth is OFF — waiting for STATE_ON broadcast")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(bluetoothStateReceiver)
        } catch (e: Exception) {
            Log.e("BLE", "Receiver not registered: ${e.message}")
        }
        bleAdvertiser?.stopAdvertising()
        bleScanner?.stopScanning()
        alertManager?.release()
    }
}

@Composable
fun CrisisPinApp(
    bleAdvertiser: BleAdvertiser?,
    receivedMessage: String
) {
    var statusText by remember { mutableStateOf("System Ready") }
    var isBroadcasting by remember { mutableStateOf(false) }

    Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Status: $statusText",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Latest Received Alert:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = receivedMessage,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (bleAdvertiser == null) return@Button
                    if (!isBroadcasting) {
                        bleAdvertiser.startAdvertising("SOS")
                        statusText = "Broadcasting Emergency"
                        isBroadcasting = true
                    } else {
                        bleAdvertiser.stopAdvertising()
                        statusText = "System Ready"
                        isBroadcasting = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isBroadcasting)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (!isBroadcasting) "SEND EMERGENCY ALERT" else "STOP BROADCAST")
            }
        }
    }
}