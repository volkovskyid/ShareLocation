package com.gmail.volkovskyid.sharelocation

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.Intent.ACTION_VIEW
import android.content.Intent.EXTRA_TEXT
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.gmail.volkovskyid.sharelocation.ui.theme.ShareLocationTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


private val locationPermissions = arrayOf(
    ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION
)

class MainActivity : ComponentActivity() {

    private val permissionResultLauncher = MutableSharedFlow<Boolean>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(RequestMultiplePermissions()) { result ->
            permissionResultLauncher.tryEmit(result.values.all { isGranted -> isGranted })
        }

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ShareLocationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val scope = rememberCoroutineScope()
                    var hasLocationPermission by remember {
                        mutableStateOf(checkAnyPermissions(*locationPermissions))
                    }
                    var lastLocation: Location? by remember { mutableStateOf(null) }

                    LaunchedEffect(hasLocationPermission) {
                        lastLocation = fusedLocationClient.tryLastLocation()
                    }

                    if (hasLocationPermission) {
                        Share(
                            location = lastLocation,
                            modifier = Modifier.padding(innerPadding),
                            onView = { location -> viewLocation(location) },
                            onShare = { location -> shareLocation(location) },
                        )
                    } else {
                        Landing(
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            scope.launch {
                                hasLocationPermission = requestPermissions(*locationPermissions)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun Context.checkPermission(permission: String): Boolean =
        ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun Context.checkAnyPermissions(vararg permissions: String): Boolean =
        permissions.any { checkPermission(it) }

    private fun Context.filterNotGrantedPermissions(vararg permissions: String): List<String> =
        permissions.filterNot { checkPermission(it) }

    private suspend fun requestPermissions(vararg permissions: String): Boolean {
        val notGranted = filterNotGrantedPermissions(*permissions)
        if (notGranted.isEmpty()) return true
        requestMultiplePermissionsLauncher.launch(notGranted.toTypedArray())

        return permissionResultLauncher.first()
    }

    @RequiresPermission(anyOf = [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION])
    private suspend fun FusedLocationProviderClient.lastLocation(): Location? =
        suspendCancellableCoroutine { continuation ->
            lastLocation
                .addOnSuccessListener { location -> continuation.resume(location) }
                .addOnFailureListener { exception -> continuation.resumeWithException(exception) }
                .addOnCanceledListener { continuation.cancel() }
        }

    @SuppressLint("MissingPermission")
    private suspend fun FusedLocationProviderClient.tryLastLocation(): Location? =
        if (checkAnyPermissions(*locationPermissions)) lastLocation()
        else null

    private fun viewLocation(location: Location) {
        val coordinates = location.coordinates()
        val uri = "geo:$coordinates?q=$coordinates"
        startActivity(Intent(ACTION_VIEW, Uri.parse(uri)))
    }

    private fun shareLocation(location: Location) {
        val sharingIntent = Intent(ACTION_SEND)
            .setType("text/plain")
            .putExtra(EXTRA_TEXT, "https://www.google.com/maps/?q=${location.coordinates()}")
        startActivity(Intent.createChooser(sharingIntent, "Share via"))
    }
}

@Composable
fun Landing(modifier: Modifier = Modifier, onRequestPermission: () -> Unit) {
    Column(modifier = modifier.padding(16.dp)) {
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
            onClick = { onRequestPermission() },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) { Text(text = "Continue") }
    }
}

@Composable
fun Share(
    location: Location?,
    modifier: Modifier,
    onView: (Location) -> Unit,
    onShare: (Location) -> Unit,
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Welcome to Share Location app" +
                    "\n\n" +
                    "Your current location is\n\n${location.pretty()}",
            textAlign = TextAlign.Center,
            fontSize = 24.sp,
            modifier = Modifier.padding(top = 24.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        if (location != null) {
            Row(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Button(
                    onClick = { onView(location) },
                ) { Text(text = "View on map") }
                Spacer(modifier = Modifier.size(24.dp))
                Button(
                    onClick = { onShare(location) },
                ) { Text(text = "Share") }
            }
        }
    }
}

private fun Location.coordinates(): String = "$latitude,$longitude"
private fun Location?.pretty(): String = this?.run {
    String.format(Locale.ROOT, "%.4f %.4f", latitude, longitude)
} ?: "N/A"
