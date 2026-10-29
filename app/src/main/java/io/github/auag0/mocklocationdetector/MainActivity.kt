package io.github.auag0.mocklocationdetector

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import io.github.auag0.mocklocationdetector.ui.MainScreen
import io.github.auag0.mocklocationdetector.ui.theme.AppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private var fineLocationGranted by mutableStateOf(false)
    private var coarseLocationGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()
                val uiState by viewModel.uiState.collectAsState()

                var lastRequestedPermission by remember { mutableStateOf<String?>(null) }
                var pendingDetection by remember { mutableStateOf(false) }

                fun showOpenSettingsSnackbar() {
                    coroutineScope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "Please grant location permission from settings",
                            actionLabel = "Open",
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            openPermissionSettings()
                        }
                    }
                }

                val requestPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    updatePermissionsState()

                    val requestedPermission = lastRequestedPermission
                    if (!granted && !hasAnyLocationPermission() &&
                        requestedPermission != null &&
                        !shouldShowRequestPermissionRationale(requestedPermission)
                    ) {
                        // The system will no longer show the dialog ("Don't ask again").
                        showOpenSettingsSnackbar()
                    }

                    if (pendingDetection) {
                        pendingDetection = false
                        if (hasAnyLocationPermission()) {
                            viewModel.runDetection()
                        }
                    }
                }

                fun requestPermission(permissionName: String, runAfterGrant: Boolean = false) {
                    if (isGrantedPermission(permissionName)) {
                        if (runAfterGrant) viewModel.runDetection()
                        return
                    }
                    lastRequestedPermission = permissionName
                    pendingDetection = runAfterGrant
                    requestPermissionLauncher.launch(permissionName)
                }

                MainScreen(
                    uiState = uiState,
                    fineLocationGranted = fineLocationGranted,
                    coarseLocationGranted = coarseLocationGranted,
                    onRunDetection = {
                        if (hasAnyLocationPermission()) {
                            viewModel.runDetection()
                        } else {
                            requestPermission(ACCESS_FINE_LOCATION, runAfterGrant = true)
                        }
                    },
                    onRequestCoarse = { requestPermission(ACCESS_COARSE_LOCATION) },
                    onRequestFine = { requestPermission(ACCESS_FINE_LOCATION) },
                    onOpenSettings = { openPermissionSettings() },
                    snackbarHostState = snackbarHostState
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionsState()
    }

    private fun updatePermissionsState() {
        fineLocationGranted = isGrantedPermission(ACCESS_FINE_LOCATION)
        coarseLocationGranted = isGrantedPermission(ACCESS_COARSE_LOCATION)
    }

    private fun hasAnyLocationPermission(): Boolean {
        return isGrantedPermission(ACCESS_FINE_LOCATION) || isGrantedPermission(ACCESS_COARSE_LOCATION)
    }

    private fun openPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:${packageName}".toUri()
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun isGrantedPermission(permissionName: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionName) == PackageManager.PERMISSION_GRANTED
    }
}
