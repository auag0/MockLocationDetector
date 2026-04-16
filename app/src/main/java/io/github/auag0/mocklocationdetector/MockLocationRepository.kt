package io.github.auag0.mocklocationdetector

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import io.github.auag0.mocklocationdetector.model.DetectionResult
import io.github.auag0.mocklocationdetector.util.StringUtils.safeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MockLocationRepository(private val context: Context) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    suspend fun runDetections(isGrantedFineLocation: Boolean): List<DetectionResult> {
        val detections = mutableListOf<DetectionResult>()
        detections += getAppOpsDetections()
        detections += getSettingsDetections()
        detections += getLocationDetections(isGrantedFineLocation)
        detections += getHookDetections()
        return detections
    }

    private fun getAppOpsDetections(): List<DetectionResult> {
        val detections = mutableListOf<DetectionResult>()
        var mockApp = ""

        try {
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

            for (appInfo in packages) {
                val requestedPermissions = appInfo.requestedPermissions ?: continue
                if (requestedPermissions.contains("android.permission.ACCESS_MOCK_LOCATION")) {
                    if (appInfo.packageName == context.packageName) continue

                    val uid = appInfo.applicationInfo?.uid ?: continue

                    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, uid, appInfo.packageName)
                    } else {
                        @Suppress("DEPRECATION")
                        appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, uid, appInfo.packageName)
                    }

                    if (mode == AppOpsManager.MODE_ALLOWED) {
                        mockApp = appInfo.packageName
                        break
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        detections += DetectionResult(
            title = "AppOpsManager (OPSTR_MOCK_LOCATION)",
            content = if (mockApp.isNotBlank()) "Mock app detected:\n${mockApp}" else "No mock apps found",
            isDetected = mockApp.isNotBlank()
        )
        return detections
    }

    private fun getSettingsDetections(): List<DetectionResult> {
        val mockLocation = Settings.Secure.getString(context.contentResolver, "mock_location")
        return listOf(
            DetectionResult(
                "Secure.getString(\"mock_location\")",
                mockLocation.safeString(),
                mockLocation != "0" && mockLocation != null
            )
        )
    }

    private suspend fun getLocationDetections(isGrantedFineLocation: Boolean): List<DetectionResult> {
        val detections = mutableListOf<DetectionResult>()
        val priority = if (isGrantedFineLocation) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY

        val currentLocation: Location? = withContext(Dispatchers.IO) {
            try {
                fusedLocationClient.getCurrentLocation(
                    priority,
                    CancellationTokenSource().token
                ).await()
            } catch (e: SecurityException) {
                e.printStackTrace()
                null
            }
        }

        if (currentLocation == null) {
            return listOf(
                DetectionResult("Location Data", "Failed to retrieve location", false)
            )
        }

        @Suppress("DEPRECATION")
        val isFromMockProvider = currentLocation.isFromMockProvider

        val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            currentLocation.isMock
        } else null

        val mockLocation = currentLocation.extras?.getBoolean("mockLocation")

        detections += DetectionResult("isFromMockProvider", isFromMockProvider.safeString(), isFromMockProvider)
        detections += DetectionResult("isMock", isMock.safeString(), isMock == true)
        detections += DetectionResult("mockLocation", mockLocation.safeString(), mockLocation == true)

        return detections
    }

    private fun getHookDetections(): List<DetectionResult> {
        val detections = mutableListOf<DetectionResult>()
        val location = Location("mock")

        val isProviderDetected = location.provider != "mock"
        detections += DetectionResult(
            "Location.provider",
            "${if (isProviderDetected) "" else "Not "}Hooked",
            isProviderDetected
        )

        location.extras = Bundle().apply { putBoolean("mockLocation", true) }
        val isExtrasDetected = location.extras?.getBoolean("mockLocation") != true
        detections += DetectionResult(
            "Location.extras",
            "${if (isExtrasDetected) "" else "Not "}Hooked",
            isExtrasDetected
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock = true
            val isMockDetected = !location.isMock
            detections += DetectionResult(
                "Location.isMock",
                "${if (isMockDetected) "" else "Not "}Hooked",
                isMockDetected
            )
        }
        return detections
    }
}