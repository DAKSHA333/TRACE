package com.trace.workspace.data

import com.trace.workspace.domain.AnswerFormatter
import com.trace.workspace.domain.ConfirmedWorkspaceObject
import com.trace.workspace.domain.QueryParser
import com.trace.workspace.domain.TraceAnswer
import com.trace.workspace.domain.TraceIntent
import com.trace.workspace.domain.WorkspaceReasoner
import java.io.File

class TraceRepository(
    private val dao: TraceDao,
    private val filesDir: File,
) {
    val projects = dao.observeProjects()

    fun scans(projectId: Long) = dao.observeScans(projectId)

    suspend fun ensureDemoProject(): Long {
        val existing = dao.latestProject()
        if (existing != null) return existing.id
        return dao.insertProject(
            ProjectEntity(
                name = "IoT Prototype",
                description = "Hackathon demo workspace for ESP32, notebook, laptop and components.",
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
                AnswerFormatter.lastSeen(parsed.objectName?.let { dao.observationsMatching(it).firstOrNull() })
            }
            TraceIntent.LIST_WORKSPACE -> listWorkspace()
            TraceIntent.COMPARE_SCANS -> compareLatestScans()
            TraceIntent.RESUME_PROJECT -> resumeLatestProject()
            TraceIntent.UNKNOWN -> TraceAnswer("Try a grounded question", "Ask where an object was last seen, what changed, or resume a workspace.")
        }
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
