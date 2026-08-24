package com.trace.workspace.domain

import kotlin.math.abs

data class BoundingBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

data class DetectedWorkspaceObject(
    val label: String,
    val confidence: Float,
    val box: BoundingBox,
    val color: String = "unknown",
)

data class ConfirmedWorkspaceObject(
    val name: String,
    val label: String,
    val confidence: Float,
    val box: BoundingBox,
    val color: String,
)

object WorkspaceReasoner {
    fun zoneFor(box: BoundingBox): String {
        val horizontal = when {
            box.centerX < 0.33f -> "left"
            box.centerX > 0.66f -> "right"
            else -> "center"
        }
        val depth = when {
            box.centerY < 0.33f -> "back"
            box.centerY > 0.66f -> "front"
            else -> "middle"
        }
        return "$horizontal $depth"
    }

    fun relationship(first: ConfirmedWorkspaceObject, second: ConfirmedWorkspaceObject): String {
        val dx = first.box.centerX - second.box.centerX
        val dy = first.box.centerY - second.box.centerY
        return if (abs(dx) >= abs(dy)) {
            if (dx < 0) "LEFT_OF" else "RIGHT_OF"
        } else {
            if (dy < 0) "ABOVE" else "BELOW"
        }
    }

    fun changed(previous: List<String>, current: List<String>): ScanChange {
        val previousSet = previous.map { it.lowercase() }.toSet()
        val currentSet = current.map { it.lowercase() }.toSet()
        return ScanChange(
            added = current.filter { it.lowercase() !in previousSet },
            removed = previous.filter { it.lowercase() !in currentSet },
            retained = current.filter { it.lowercase() in previousSet },
        )
    }
}

data class ScanChange(
    val added: List<String>,
    val removed: List<String>,
    val retained: List<String>,
)
