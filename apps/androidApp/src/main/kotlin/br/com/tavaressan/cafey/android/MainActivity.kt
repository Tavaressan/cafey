package br.com.tavaressan.cafey.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import br.com.tavaressan.cafey.shared.App
import br.com.tavaressan.cafey.shared.auth.AndroidPlatformContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // O TokenStorage e AndroidBleClient do Android dependem do Context; precisa existir antes do AppContainer.
        AndroidPlatformContext.init(applicationContext)

        solicitarPermissoesBluetoothSeNecessario()

        setContent {
            App(baseUrl = BuildConfig.BACKEND_BASE_URL)
        }
    }

    private fun solicitarPermissoesBluetoothSeNecessario() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissions(permissionsToRequest.toTypedArray(), BLE_PERMISSIONS_REQUEST_CODE)
        }
    }

    companion object {
        private const val BLE_PERMISSIONS_REQUEST_CODE = 1001
    }
}
