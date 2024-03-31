package com.gmail.volkovskyid.sharelocation

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.gmail.volkovskyid.sharelocation.ui.theme.ShareLocationTheme
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val locationPermissions = arrayOf(
    ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION
)

class MainActivity : ComponentActivity() {

    private val permissionResultLauncher = MutableSharedFlow<Boolean>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val requestMultiplePermissionsLauncher = registerForActivityResult(RequestMultiplePermissions()) { result ->
        permissionResultLauncher.tryEmit(result.values.all { isGranted -> isGranted })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ShareLocationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Landing(
                        modifier = Modifier.padding(innerPadding)
                    ) { requestPermissions(*locationPermissions) }
                }
            }
        }
    }

    private suspend fun requestPermissions(vararg permissions: String): Boolean {
        val notGranted = filterNotGrantedPermissions(*permissions)
        if (notGranted.isEmpty()) return true
        requestMultiplePermissionsLauncher.launch(notGranted.toTypedArray())

        return permissionResultLauncher.first()
    }
}

@Composable
fun Landing(modifier: Modifier = Modifier, onRequestPermission: suspend () -> Unit) {
    Column(modifier = modifier.padding(16.dp)) {
        val scope = rememberCoroutineScope()
        Text(
            text = "Welcome to Share Location app" +
                    "\n\n" +
                    "In order to share your current location, please grant location permission" +
                    "\n\n" +
                    "Tap on Continue",
            textAlign = TextAlign.Center,
            fontSize = 24.sp,
            modifier = Modifier.padding(top = 24.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = { scope.launch { onRequestPermission() } },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) { Text(text = "Continue") }
    }
}

private fun Context.checkPermission(permission: String): Boolean =
    ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.filterNotGrantedPermissions(vararg permissions: String): List<String> =
    permissions.filterNot { checkPermission(it) }
