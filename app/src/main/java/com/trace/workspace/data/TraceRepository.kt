package com.trace.workspace.data

import com.trace.workspace.domain.AnswerFormatter
import com.trace.workspace.domain.ConfirmedWorkspaceObject
import com.trace.workspace.domain.QueryParser
import com.trace.workspace.domain.TraceAnswer
import com.trace.workspace.domain.TraceIntent
import com.trace.workspace.domain.WorkspaceReasoner
import java.text.SimpleDateFormat
import java.io.File
import java.util.Date
import java.util.Locale

class TraceRepository(
    private val dao: TraceDao,
    private val filesDir: File,
) {
    private val answerDateFormat = SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault())
    val projects = dao.observeProjects()

    fun scans(projectId: Long) = dao.observeScans(projectId)

    suspend fun ensureDemoProject(): Long {
        val existing = dao.latestProject()
        if (existing != null) {
            if (existing.name == "IoT Prototype") {
                dao.updateProject(
                    projectId = existing.id,
                    name = "My Desk",
                    description = "Personal workspace memory for everyday study and work.",
                )
            }
            return existing.id
        }
        return dao.insertProject(
            ProjectEntity(
                name = "My Desk",
                description = "Personal workspace memory for everyday study and work.",
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun createProject(name: String, description: String): Long =
        dao.insertProject(ProjectEntity(name = name, description = description, createdAt = System.currentTimeMillis()))

    suspend fun saveScan(projectId: Long, imagePath: String, objects: List<ConfirmedWorkspaceObject>): Long {
        val scanId = dao.insertScan(
            ScanEntity(
                projectId = projectId,
                capturedAt = System.currentTimeMillis(),
                imagePath = imagePath,
                scanType = "manual",
            )
        )
        val observationIdsByName = mutableMapOf<String, Long>()
        objects.forEach { item ->
            val knownId = dao.knownObjectByName(item.name)?.id ?: dao.insertKnownObject(
                KnownObjectEntity(
                    canonicalName = item.name,
                    colour = item.color,
                    description = "${item.color} ${item.label}",
                    embedding = "",
                    createdAt = System.currentTimeMillis(),
                )
            )
            val observationId = dao.insertObservation(
                ObjectObservationEntity(
                    scanId = scanId,
                    knownObjectId = knownId,
                    detectedLabel = item.label,
                    confidence = item.confidence,
                    left = item.box.left,
                    top = item.box.top,
                    right = item.box.right,
                    bottom = item.box.bottom,
                    workspaceZone = WorkspaceReasoner.zoneFor(item.box),
                    croppedImagePath = "",
                )
            )
            observationIdsByName[item.name] = observationId
        }
        objects.forEachIndexed { index, first ->
            objects.drop(index + 1).forEach { second ->
                val firstId = observationIdsByName[first.name] ?: return@forEach
                val secondId = observationIdsByName[second.name] ?: return@forEach
                dao.insertRelationship(
                    ObjectRelationshipEntity(
                        scanId = scanId,
                        firstObjectId = firstId,
                        relationship = WorkspaceReasoner.relationship(first, second),
                        secondObjectId = secondId,
                    )
                )
            }
        }
        return scanId
    }

    suspend fun answer(rawQuery: String): TraceAnswer {
        val parsed = QueryParser.parse(rawQuery)
        return when (parsed.intent) {
            TraceIntent.LAST_SEEN, TraceIntent.SEEN_AT_TIME -> {
                parsed.objectName?.let { lastSeenAnswer(it) }
                    ?: TraceAnswer("Search your memory", "Type an object name, like bottle, laptop or sticky note.")
            }
            TraceIntent.LIST_WORKSPACE -> listWorkspace()
            TraceIntent.COMPARE_SCANS -> compareLatestScans()
            TraceIntent.RESUME_PROJECT -> resumeLatestProject()
            TraceIntent.UNKNOWN -> TraceAnswer("Try a grounded question", "Ask where an object was last seen, what changed, or resume a workspace.")
        }
    }

    private suspend fun lastSeenAnswer(objectName: String): TraceAnswer {
        val observation = dao.observationsMatching(objectName).firstOrNull()
            ?: return AnswerFormatter.lastSeen(null)
        val related = strongestRelationFor(observation)
        val relationText = related?.let { " ${it}" }.orEmpty()
        return TraceAnswer(
            title = observation.objectName,
            body = "Last observed ${answerDateFormat.format(Date(observation.capturedAt))} in the ${observation.workspaceZone} area${relationText} of ${observation.projectName}.",
            evidenceImagePath = observation.imagePath,
        )
    }

    private suspend fun strongestRelationFor(observation: ObservationWithContext): String? {
        val objects = observationsForScan(observation.scanId)
        val relationships = dao.relationshipsForScan(observation.scanId)
        val relation = relationships.firstOrNull {
            it.firstObjectId == observation.observationId || it.secondObjectId == observation.observationId
        } ?: return null
        val targetId = if (relation.firstObjectId == observation.observationId) relation.secondObjectId else relation.firstObjectId
        val target = objects.firstOrNull { it.observationId == targetId } ?: return null
        val phrase = when {
            relation.firstObjectId == observation.observationId -> relation.relationship.toPhrase()
            relation.relationship == "LEFT_OF" -> "right of"
            relation.relationship == "RIGHT_OF" -> "left of"
            relation.relationship == "ABOVE" -> "below"
            relation.relationship == "BELOW" -> "above"
            else -> "near"
        }
        return "$phrase ${target.objectName}"
    }

    private fun String.toPhrase(): String =
        when (this) {
            "LEFT_OF" -> "left of"
            "RIGHT_OF" -> "right of"
            "ABOVE" -> "above"
            "BELOW" -> "below"
            else -> "near"
        }

    suspend fun compareLatestScans(): TraceAnswer {
        val scans = dao.allScansNewestFirst()
        if (scans.size < 2) {
            return TraceAnswer("Need two scans", "Capture another scan to compare what changed.")
        }
        val current = observationsForScan(scans[0].id)
        val previous = observationsForScan(scans[1].id)
        val change = WorkspaceReasoner.changed(previous.map { it.objectName }, current.map { it.objectName })
        val parts = listOf(
            "Added: ${change.added.ifEmpty { listOf("none") }.joinToString()}",
            "Removed: ${change.removed.ifEmpty { listOf("none") }.joinToString()}",
            "Still present: ${change.retained.ifEmpty { listOf("none") }.joinToString()}",
        )
        return TraceAnswer("Workspace changes", parts.joinToString("\n"), scans[0].imagePath)
    }

    suspend fun listWorkspace(): TraceAnswer {
        val scan = dao.allScansNewestFirst().firstOrNull()
            ?: return TraceAnswer("No scans yet", "Capture a workspace scan first.")
        val objects = observationsForScan(scan.id)
        val body = objects.joinToString("\n") { "${it.objectName} - ${it.workspaceZone}" }
        return TraceAnswer("Latest workspace", body.ifBlank { "No confirmed objects in the latest scan." }, scan.imagePath)
    }

    suspend fun resumeLatestProject(): TraceAnswer {
        val project = dao.latestProject()
            ?: return TraceAnswer("No project yet", "Create a project and scan your workspace first.")
        val scan = dao.scansForProject(project.id).firstOrNull()
            ?: return TraceAnswer(project.name, "No scans saved for this project yet.")
        val objects = observationsForScan(scan.id)
        val body = buildString {
            append("Resume ${project.name} from your latest saved workspace:\n")
            objects.forEach { append("${it.objectName} - ${it.workspaceZone}\n") }
        }.trim()
        return TraceAnswer("Resume workspace", body, scan.imagePath)
    }

    suspend fun clearAll() {
        dao.clearAll()
        File(filesDir, "scans").deleteRecursively()
    }

    private suspend fun observationsForScan(scanId: Long): List<ObservationWithContext> {
        val scans = dao.allScansNewestFirst()
        val scan = scans.firstOrNull { it.id == scanId } ?: return emptyList()
        val project = dao.project(scan.projectId) ?: return emptyList()
        return dao.observeKnownObjectsSnapshot().flatMap { known ->
            dao.observationsMatching(known.canonicalName)
                .filter { it.scanId == scanId && it.projectId == project.id }
        }
    }
}

suspend fun TraceDao.observeKnownObjectsSnapshot(): List<KnownObjectEntity> {
    val newest = allScansNewestFirst()
    if (newest.isEmpty()) return emptyList()
    return newest.flatMap { scan ->
        observationsMatching("")
            .filter { it.scanId == scan.id }
            .mapNotNull { knownObjectByName(it.objectName) }
    }.distinctBy { it.id }
}
