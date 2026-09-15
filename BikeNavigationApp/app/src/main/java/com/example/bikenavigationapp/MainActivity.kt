package com.example.bikenavigationapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.bikenavigationapp.ui.theme.BikeNavigationAppTheme
import java.util.UUID

// AT-09 / HM-10 Custom Serial Service UUIDs
val UART_SERVICE_UUID: UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
val UART_TX_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")
val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

// Simple navigation states
enum class Screen {
    MAIN_MENU,
    BLUETOOTH_MENU
}

class MainActivity : ComponentActivity() {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private var bluetoothGatt: BluetoothGatt? = null

    // UI State variables
    private var currentScreen = mutableStateOf(Screen.MAIN_MENU)
    private var scannedDevices = mutableStateListOf<BluetoothDevice>()
    private var connectionStatus = mutableStateOf("Disconnected")
    private var connectedDevice = mutableStateOf<BluetoothDevice?>(null)

    // 1. Launcher to request turning on Bluetooth
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            checkPermissionsAndScan() // Start scanning immediately after user turns BT on
        } else {
            connectionStatus.value = "Bluetooth must be enabled to scan."
        }
    }

    // 2. Launcher for missing permissions
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            checkBluetoothStateAndScan()
        } else {
            connectionStatus.value = "Permissions denied."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BikeNavigationAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (currentScreen.value) {
                        Screen.MAIN_MENU -> MainMenuScreen(
                            modifier = Modifier.padding(innerPadding),
                            status = connectionStatus.value,
                            onNavigateToBluetooth = { currentScreen.value = Screen.BLUETOOTH_MENU }
                        )
                        Screen.BLUETOOTH_MENU -> BluetoothMenuScreen(
                            modifier = Modifier.padding(innerPadding),
                            devices = scannedDevices,
                            status = connectionStatus.value,
                            connectedDevice = connectedDevice.value,
                            onBackClick = { currentScreen.value = Screen.MAIN_MENU },
                            onScanClick = { checkPermissionsAndScan() },
                            onDeviceClick = { connectToDevice(it) },
                            onDisconnectClick = { disconnectDevice() }
                        )
                    }
                }
            }
        }
    }

    private fun checkPermissionsAndScan() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ANSWER_PHONE_CALLS,
                Manifest.permission.READ_PHONE_STATE
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ANSWER_PHONE_CALLS,
                Manifest.permission.READ_PHONE_STATE
            )
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            checkBluetoothStateAndScan()
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkBluetoothStateAndScan() {
        if (bluetoothAdapter?.isEnabled == false) {
            // Automatically prompt the user to turn on Bluetooth
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        } else {
            startBleScan()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        scannedDevices.clear()
        connectionStatus.value = "Scanning..."
        val scanner = bluetoothAdapter?.bluetoothLeScanner

        scanner?.startScan(object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (!scannedDevices.contains(result.device) && result.device.name != null) {
                    scannedDevices.add(result.device)
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(object : ScanCallback() {})
        connectionStatus.value = "Connecting to ${device.name}..."

        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    private fun disconnectDevice() {
        connectionStatus.value = "Disconnecting..."
        bluetoothGatt?.disconnect()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectionStatus.value = "Connected! Discovering services..."
                connectedDevice.value = gatt.device
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectionStatus.value = "Disconnected"
                connectedDevice.value = null
                gatt.close()
                bluetoothGatt = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(UART_SERVICE_UUID)
                val txCharacteristic = service?.getCharacteristic(UART_TX_CHARACTERISTIC_UUID)

                if (txCharacteristic != null) {
                    gatt.setCharacteristicNotification(txCharacteristic, true)
                    val descriptor = txCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                    connectionStatus.value = "Ready. Waiting for media commands."
                } else {
                    connectionStatus.value = "Error: UART TX characteristic not found."
                }
            }
        }

        // --- NEW: For Android 13+ (API 33+) ---
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == UART_TX_CHARACTERISTIC_UUID) {
                val command = String(value).trim()
                handleMediaCommand(command)

                // Provide visual UI feedback
                connectionStatus.value = "Last command received: $command"
            }
        }

        // --- OLD: For Android 12 and below ---
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == UART_TX_CHARACTERISTIC_UUID) {
                val data = characteristic.value
                val command = String(data).trim()
                handleMediaCommand(command)

                // Provide visual UI feedback
                connectionStatus.value = "Last command received: $command"
            }
        }
    }

    private fun handleMediaCommand(command: String) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager

        when (command) {
            // --- Media Playback Controls ---
            "PLAY_PAUSE", "P" -> sendMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "NEXT", "N" -> sendMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_NEXT)
            "PREV", "B" -> sendMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_PREVIOUS)

            // --- Global Volume Controls ---
            "VOL_UP", "U" -> {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE,
                    AudioManager.FLAG_SHOW_UI // Shows the volume slider on screen
                )
            }
            "VOL_DOWN", "D" -> {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI
                )
            }

            // --- Phone Call Controls ---
            "ANSWER", "A" -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                    telecomManager.acceptRingingCall()
                }
            }
            "REJECT", "END", "R" -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                    telecomManager.endCall()
                }
            }
        }
    }

    // Helper function to keep the switch statement clean
    private fun sendMediaKey(audioManager: AudioManager, keyCode: Int) {
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }
}

@Composable
fun MainMenuScreen(
    modifier: Modifier = Modifier,
    status: String,
    onNavigateToBluetooth: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Bike Navigation Core",
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Remote Status: $status",
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onNavigateToBluetooth,
            modifier = Modifier.fillMaxWidth(0.8f).height(50.dp)
        ) {
            Text("Manage Bluetooth Remote")
        }
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothMenuScreen(
    modifier: Modifier = Modifier,
    devices: List<BluetoothDevice>,
    status: String,
    connectedDevice: BluetoothDevice?,
    onBackClick: () -> Unit,
    onScanClick: () -> Unit,
    onDeviceClick: (BluetoothDevice) -> Unit,
    onDisconnectClick: () -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Bluetooth Devices") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Status: $status", color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))

            // Show Scan or Disconnect depending on connection state
            if (connectedDevice == null) {
                Button(onClick = onScanClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Turn On & Scan for Remotes")
                }
            } else {
                Button(
                    onClick = onDisconnectClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Disconnect from ${connectedDevice.name ?: "Device"}")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // List of scanned devices
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(devices) { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable(enabled = connectedDevice == null) { onDeviceClick(device) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = device.name ?: "Unknown Device",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = device.address,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}