package com.trace.workspace.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trace.workspace.TraceApplication
import com.trace.workspace.domain.AnswerFormatter
import com.trace.workspace.domain.ConfirmedWorkspaceObject
import com.trace.workspace.domain.DetectedWorkspaceObject
import com.trace.workspace.domain.TraceAnswer
import com.trace.workspace.vision.DemoObjectDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class TraceUiState(
    val onboarded: Boolean = false,
    val selectedProjectId: Long = 0,
    val pendingImagePath: String? = null,
    val detections: List<DetectedWorkspaceObject> = emptyList(),
    val confirmedObjects: List<ConfirmedWorkspaceObject> = emptyList(),
    val query: String = "",
    val answer: TraceAnswer? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

class TraceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as TraceApplication).repository
    private val detector = DemoObjectDetector()
    private val mutableState = MutableStateFlow(TraceUiState())

    val uiState: StateFlow<TraceUiState> = combine(repository.projects, mutableState) { projects, state ->
        val selected = state.selectedProjectId.takeIf { it != 0L } ?: projects.firstOrNull()?.id ?: 0L
        state.copy(selectedProjectId = selected)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TraceUiState())

    init {
        viewModelScope.launch {
            val projectId = repository.ensureDemoProject()
            mutableState.value = mutableState.value.copy(selectedProjectId = projectId)
        }
    }

    fun completeOnboarding() {
        mutableState.value = mutableState.value.copy(onboarded = true)
    }

    fun createProject(name: String, description: String) {
        viewModelScope.launch {
            val id = repository.createProject(name.ifBlank { "Untitled Workspace" }, description)
            mutableState.value = mutableState.value.copy(selectedProjectId = id, message = "Project created")
        }
    }

    fun analyzeCapturedImage(uri: Uri) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true, pendingImagePath = uri.toString())
            val detections = detector.detect(uri.toString())
            mutableState.value = mutableState.value.copy(
                busy = false,
                detections = detections,
                confirmedObjects = detections.map {
                    ConfirmedWorkspaceObject(
                        name = it.label,
                        label = it.label,
                        confidence = it.confidence,
                        box = it.box,
                        color = it.color,
                    )
                },
                message = "Detected ${detections.size} workspace objects",
            )
        }
    }

    fun useDemoScan() {
        val placeholder = File(getApplication<Application>().filesDir, "scans/demo-workspace.jpg")
        placeholder.parentFile?.mkdirs()
        analyzeCapturedImage(Uri.fromFile(placeholder))
    }

    fun renameObject(index: Int, name: String) {
        val updated = mutableState.value.confirmedObjects.toMutableList()
        if (index in updated.indices) {
            updated[index] = updated[index].copy(name = name.ifBlank { updated[index].label })
            mutableState.value = mutableState.value.copy(confirmedObjects = updated)
        }
    }

    fun removeObject(index: Int) {
        val updated = mutableState.value.confirmedObjects.toMutableList()
        if (index in updated.indices) {
            updated.removeAt(index)
            mutableState.value = mutableState.value.copy(confirmedObjects = updated)
        }
    }

    fun addManualObject(name: String) {
        val existing = mutableState.value.confirmedObjects
        val manual = ConfirmedWorkspaceObject(
            name = name.ifBlank { "manual object" },
            label = "manual",
            confidence = 1f,
            box = com.trace.workspace.domain.BoundingBox(0.40f, 0.40f, 0.60f, 0.60f),
            color = "unknown",
        )
        mutableState.value = mutableState.value.copy(confirmedObjects = existing + manual)
    }

    fun saveCurrentScan() {
        viewModelScope.launch {
            val state = mutableState.value
            val projectId = state.selectedProjectId.takeIf { it != 0L } ?: repository.ensureDemoProject()
            val path = state.pendingImagePath ?: return@launch
            repository.saveScan(projectId, path, state.confirmedObjects)
            mutableState.value = state.copy(
                pendingImagePath = null,
                detections = emptyList(),
                confirmedObjects = emptyList(),
                message = "Scan saved as timestamped memory",
            )
        }
    }

    fun updateQuery(query: String) {
        mutableState.value = mutableState.value.copy(query = query)
    }

    fun askTrace() {
        viewModelScope.launch {
            val answer = repository.answer(mutableState.value.query)
            mutableState.value = mutableState.value.copy(answer = answer)
        }
    }

    fun compareScans() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(answer = repository.compareLatestScans())
        }
    }

    fun resumeWorkspace() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(answer = repository.resumeLatestProject())
        }
    }

    fun clearData() {
        viewModelScope.launch {
            repository.clearAll()
            val projectId = repository.ensureDemoProject()
            mutableState.value = TraceUiState(onboarded = true, selectedProjectId = projectId, message = "Local TRACE memory deleted")
        }
    }
}
