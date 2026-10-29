package io.github.auag0.mocklocationdetector

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import io.github.auag0.mocklocationdetector.model.DetectionResult
import io.github.auag0.mocklocationdetector.model.DetectionStatus
import io.github.auag0.mocklocationdetector.util.StringUtils.safeString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MockLocationRepository(private val context: Context) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    suspend fun runDetections(): List<DetectionResult> {
        val detections = mutableListOf<DetectionResult>()
        detections += getAppOpsDetections()
        detections += getSettingsDetections()
        detections += getLocationDetections()
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
            status = if (mockApp.isNotBlank()) DetectionStatus.DETECTED else DetectionStatus.CLEAN
        )
        return detections
    }

    private fun getSettingsDetections(): List<DetectionResult> {
        val mockLocation = Settings.Secure.getString(context.contentResolver, "mock_location")
        val isMockLocationEnabled = mockLocation != null && mockLocation != "0"
        return listOf(
            DetectionResult(
                "Secure.getString(\"mock_location\")",
                mockLocation.safeString(),
                if (isMockLocationEnabled) DetectionStatus.DETECTED else DetectionStatus.CLEAN
            )
        )
    }

    private suspend fun getLocationDetections(): List<DetectionResult> {
        val hasFineLocation = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarseLocation = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

        if (!hasFineLocation && !hasCoarseLocation) {
            return listOf(
                DetectionResult(
                    "Location Data",
                    "Location permission is not granted",
                    DetectionStatus.UNKNOWN
                )
            )
        }

        val priority = if (hasFineLocation) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val currentLocation = getFreshLocation(priority)
            ?: getLastLocation()
            ?: getLastKnownLocation()

        if (currentLocation == null) {
            return listOf(
                DetectionResult(
                    "Location Data",
                    "Failed to retrieve location",
                    DetectionStatus.UNKNOWN
                )
            )
        }

        @Suppress("DEPRECATION")
        val isFromMockProvider = currentLocation.isFromMockProvider

        val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            currentLocation.isMock
        } else null

        val mockLocation = currentLocation.extras?.getBoolean("mockLocation")

        return listOf(
            DetectionResult(
                "isFromMockProvider",
                isFromMockProvider.safeString(),
                if (isFromMockProvider) DetectionStatus.DETECTED else DetectionStatus.CLEAN
            ),
            DetectionResult(
                "isMock",
                isMock.safeString(),
                if (isMock == true) DetectionStatus.DETECTED else DetectionStatus.CLEAN
            ),
            DetectionResult(
                "mockLocation",
                mockLocation.safeString(),
                if (mockLocation == true) DetectionStatus.DETECTED else DetectionStatus.CLEAN
            )
        )
    }

    /**
     * Requests a fresh fix and waits at most [LOCATION_TIMEOUT_MILLIS] for it.
     */
    private suspend fun getFreshLocation(priority: Int): Location? = withContext(Dispatchers.IO) {
        val cancellationTokenSource = CancellationTokenSource()
        val timeoutJob = launch {
            delay(LOCATION_TIMEOUT_MILLIS)
            cancellationTokenSource.cancel()
        }

        try {
            fusedLocationClient.getCurrentLocation(priority, cancellationTokenSource.token).await()
        } catch (e: CancellationException) {
            // Cancelled by our timeout, unless the caller itself was cancelled.
            if (!isActive) throw e
            null
        } catch (e: SecurityException) {
            e.printStackTrace()
            null
        } catch (e: ApiException) {
            e.printStackTrace()
            null
        } finally {
            timeoutJob.cancel()
        }
    }

    private suspend fun getLastLocation(): Location? = withContext(Dispatchers.IO) {
        try {
            fusedLocationClient.lastLocation.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            e.printStackTrace()
            null
        } catch (e: ApiException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Last resort for devices without Google Play services or when FusedLocation returns nothing.
     */
    private fun getLastKnownLocation(): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        return listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    private fun hasPermission(permission: String): Boolean {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun getHookDetections(): List<DetectionResult> {
        val detections = mutableListOf<DetectionResult>()
        val location = Location("mock")

        val isProviderDetected = location.provider != "mock"
        detections += DetectionResult(
            "Location.provider",
            "${if (isProviderDetected) "" else "Not "}Hooked",
            if (isProviderDetected) DetectionStatus.DETECTED else DetectionStatus.CLEAN
        )

        location.extras = Bundle().apply { putBoolean("mockLocation", true) }
        val isExtrasDetected = location.extras?.getBoolean("mockLocation") != true
        detections += DetectionResult(
            "Location.extras",
            "${if (isExtrasDetected) "" else "Not "}Hooked",
            if (isExtrasDetected) DetectionStatus.DETECTED else DetectionStatus.CLEAN
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock = true
            val isMockDetected = !location.isMock
            detections += DetectionResult(
                "Location.isMock",
                "${if (isMockDetected) "" else "Not "}Hooked",
                if (isMockDetected) DetectionStatus.DETECTED else DetectionStatus.CLEAN
            )
        }
        return detections
    }

    private companion object {
        const val LOCATION_TIMEOUT_MILLIS = 10_000L
    }
}
