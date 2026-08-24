package com.trace.workspace.domain

import com.trace.workspace.data.ObservationWithContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TraceIntent {
    LAST_SEEN,
    SEEN_AT_TIME,
    LIST_WORKSPACE,
    COMPARE_SCANS,
    RESUME_PROJECT,
    UNKNOWN,
}

data class ParsedTraceQuery(
    val intent: TraceIntent,
    val objectName: String? = null,
    val projectName: String? = null,
    val timeExpression: String? = null,
)

data class TraceAnswer(
    val title: String,
    val body: String,
    val evidenceImagePath: String? = null,
)

object QueryParser {
    private val filler = setOf(
        "where", "is", "was", "were", "my", "the", "a", "an", "did", "i", "see", "seen",
        "last", "previously", "yesterday", "today", "at", "on", "desk", "workspace", "what",
        "changed", "since", "previous", "scan", "resume", "project", "it",
    )

    fun parse(raw: String): ParsedTraceQuery {
        val query = raw.trim().lowercase()
        val time = when {
            "yesterday" in query -> "yesterday"
            "today" in query -> "today"
            "previously" in query -> "previously"
            else -> null
        }
        val objectName = query
            .replace("usb c", "usb-c")
            .split(Regex("[^a-z0-9-]+"))
            .filter { it.isNotBlank() && it !in filler }
            .joinToString(" ")
            .ifBlank { null }
        val intent = when {
            "what changed" in query || "compare" in query -> TraceIntent.COMPARE_SCANS
            "resume" in query -> TraceIntent.RESUME_PROJECT
            "what was on" in query || "list" in query -> TraceIntent.LIST_WORKSPACE
            "yesterday" in query || "previously" in query || "where was" in query -> TraceIntent.SEEN_AT_TIME
            "where" in query || "last seen" in query || objectName != null -> TraceIntent.LAST_SEEN
            else -> TraceIntent.UNKNOWN
        }
        return ParsedTraceQuery(intent = intent, objectName = objectName, timeExpression = time)
    }
}

object AnswerFormatter {
    private val formatter = SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault())

    fun lastSeen(observation: ObservationWithContext?): TraceAnswer {
        if (observation == null) {
            return TraceAnswer(
                title = "Not observed yet",
                body = "I could not find that in saved workspace memories.",
            )
        }
        return TraceAnswer(
            title = observation.objectName,
            body = "Last observed ${formatter.format(Date(observation.capturedAt))} in the ${observation.workspaceZone} area of ${observation.projectName}.",
            evidenceImagePath = observation.imagePath,
        )
    }
}
