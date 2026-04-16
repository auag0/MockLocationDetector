package io.github.auag0.mocklocationdetector

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.auag0.mocklocationdetector.model.DetectionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val isLoading: Boolean = false,
    val results: List<DetectionResult> = emptyList()
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = MockLocationRepository(app)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun runDetection(isGrantedFineLocation: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, results = emptyList())

            val detections = repository.runDetections(isGrantedFineLocation)

            _uiState.value = _uiState.value.copy(isLoading = false, results = detections)
        }
    }
}