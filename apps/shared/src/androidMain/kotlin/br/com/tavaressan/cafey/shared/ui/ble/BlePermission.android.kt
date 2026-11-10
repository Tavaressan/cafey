package br.com.tavaressan.cafey.shared.ui.ble

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun permissionsGranted(context: Context): Boolean =
    requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

private fun bluetoothEnabled(context: Context): Boolean? {
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) return null
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    return manager?.adapter?.isEnabled
}

private fun bleSupported(context: Context): Boolean {
    val hasFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    return hasFeature && manager?.adapter != null
}

@Composable
actual fun rememberBlePermissionController(
    onStatusChange: (BlePermissionStatus) -> Unit,
): BlePermissionController {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var permanentlyDenied by remember { mutableStateOf(false) }

    fun computeStatus(): BlePermissionStatus = BlePermissionStatus(
        supported = bleSupported(context),
        permissionsGranted = permissionsGranted(context),
        permissionsPermanentlyDenied = permanentlyDenied && !permissionsGranted(context),
        bluetoothEnabled = bluetoothEnabled(context),
    )

    var status by remember { mutableStateOf(computeStatus()) }

    fun publish(newStatus: BlePermissionStatus = computeStatus()) {
        status = newStatus
        onStatusChange(newStatus)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val denied = result.filterValues { !it }.keys
        if (denied.isNotEmpty() && activity != null) {
            val noRationale = denied.any { perm ->
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
            }
            if (noRationale) permanentlyDenied = true
        }
        if (result.values.all { it }) permanentlyDenied = false
        publish()
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        publish()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) publish()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) { publish() }

    return remember {
        object : BlePermissionController {
            override val status: BlePermissionStatus get() = status

            override fun requestPermissions() {
                permanentlyDenied = false
                permissionLauncher.launch(requiredPermissions())
            }

            override fun requestBluetoothEnable() {
                // ACTION_REQUEST_ENABLE exige confirmação do usuário (nunca ligar silenciosamente).
                @Suppress("DEPRECATION")
                val intent = Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBtLauncher.launch(intent)
            }

            override fun openAppSettings() {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }

            override fun openBluetoothSettings() {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }

            override fun refresh() = publish()
        }
    }
}
