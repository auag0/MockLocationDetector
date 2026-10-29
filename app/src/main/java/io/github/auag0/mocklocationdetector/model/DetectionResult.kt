package io.github.auag0.mocklocationdetector.model

enum class DetectionStatus {
    CLEAN,
    DETECTED,
    UNKNOWN
}

data class DetectionResult(
    val title: String,
    val content: String,
    val status: DetectionStatus
)
