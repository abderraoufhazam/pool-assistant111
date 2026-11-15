package com.poolassistant.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * Shared UI state for the main screen: capture/overlay status and detection
 * state, surfaced as [LiveData] so the UI re-renders without touching services
 * directly from views.
 */
class MainViewModel : ViewModel() {

    enum class Status { IDLE, CAPTURING, ANALYZING, ERROR }

    private val _status = MutableLiveData(Status.IDLE)
    val status: LiveData<Status> = _status

    private val _statusMessage = MutableLiveData("")
    val statusMessage: LiveData<String> = _statusMessage

    private val _overlayActive = MutableLiveData(false)
    val overlayActive: LiveData<Boolean> = _overlayActive

    private val _detectionSummary = MutableLiveData("")
    val detectionSummary: LiveData<String> = _detectionSummary

    fun setStatus(s: Status, message: String = "") {
        _status.value = s
        _statusMessage.value = message
    }

    fun setOverlayActive(active: Boolean) {
        _overlayActive.value = active
    }

    fun setDetectionSummary(summary: String) {
        _detectionSummary.value = summary
    }
}
