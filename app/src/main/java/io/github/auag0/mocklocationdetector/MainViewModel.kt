package io.github.auag0.mocklocationdetector

import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import io.github.auag0.mocklocationdetector.AnyUtils.safeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MainViewModel(
    private val app: Application
) : AndroidViewModel(app) {
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(app)
    }
    private val _results: MutableLiveData<List<DetectionResult>> = MutableLiveData(emptyList())
    val results: LiveData<List<DetectionResult>> = _results

    fun runDetection(activity: Activity) {
        _results.value = emptyList()
        val detections: MutableList<DetectionResult> = mutableListOf()
        viewModelScope.launch {
            detections += getLocationDetections(activity)
            detections += getSettingsDetections()
            _results.value = detections
        }
    }

    private fun getSettingsDetections(): List<DetectionResult> {
        val detections: MutableList<DetectionResult> = mutableListOf()
        val mockLocation = Settings.Secure.getString(app.contentResolver, "mock_location")
        detections += DetectionResult(
            "Secure.getString(\"mock_location\")",
            mockLocation.safeString(),
            mockLocation != "0"
        )
        return detections
    }

    private suspend fun getLocationDetections(activity: Activity): List<DetectionResult> {
        val detections: MutableList<DetectionResult> = mutableListOf()
        val isGrantedFineLocation =
            activity.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val priority = if (isGrantedFineLocation) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val currentLocation: Location = withContext(Dispatchers.IO) {
            try {
                fusedLocationClient.getCurrentLocation(
                    priority,
                    CancellationTokenSource().token
                ).await()
            } catch (e: SecurityException) {
                e.printStackTrace()
                null
            }
        } ?: return emptyList()

        @Suppress("DEPRECATION") val isFromMockProvider = currentLocation.isFromMockProvider
        val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            currentLocation.isMock
        } else null
        val mockLocation = currentLocation.extras?.getBoolean("mockLocation")

        detections += DetectionResult(
            "isFromMockProvider",
            isFromMockProvider.safeString(),
            isFromMockProvider
        )
        detections += DetectionResult(
            "isMock",
            isMock.safeString(),
            isMock == true
        )
        detections += DetectionResult(
            "mockLocation",
            mockLocation.safeString(),
            mockLocation == true
        )

        return detections
    }
}