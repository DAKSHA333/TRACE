package com.trace.workspace.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TraceDao {
    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM scans WHERE projectId = :projectId ORDER BY capturedAt DESC")
    fun observeScans(projectId: Long): Flow<List<ScanEntity>>

    @Query("SELECT * FROM known_objects ORDER BY canonicalName")
    fun observeKnownObjects(): Flow<List<KnownObjectEntity>>

    @Query("SELECT * FROM object_observations WHERE scanId = :scanId")
    fun observeObservations(scanId: Long): Flow<List<ObjectObservationEntity>>

    @Query("SELECT * FROM object_relationships WHERE scanId = :scanId")
    suspend fun relationshipsForScan(scanId: Long): List<ObjectRelationshipEntity>

    @Insert
    suspend fun insertProject(project: ProjectEntity): Long

    @Insert
    suspend fun insertScan(scan: ScanEntity): Long

    @Insert
    suspend fun insertKnownObject(knownObject: KnownObjectEntity): Long

    @Insert
    suspend fun insertObservation(observation: ObjectObservationEntity): Long

    @Insert
    suspend fun insertRelationship(relationship: ObjectRelationshipEntity): Long

    @Query("DELETE FROM projects")
    suspend fun deleteProjects()

    @Query("DELETE FROM known_objects")
    suspend fun deleteKnownObjects()

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun project(projectId: Long): ProjectEntity?

    @Query("SELECT * FROM projects ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestProject(): ProjectEntity?

    @Query("SELECT * FROM scans WHERE projectId = :projectId ORDER BY capturedAt DESC")
    suspend fun scansForProject(projectId: Long): List<ScanEntity>

    @Query("SELECT * FROM scans ORDER BY capturedAt DESC")
    suspend fun allScansNewestFirst(): List<ScanEntity>

    @Query("SELECT * FROM known_objects WHERE lower(canonicalName) = lower(:name) LIMIT 1")
    suspend fun knownObjectByName(name: String): KnownObjectEntity?

    @Query("SELECT * FROM known_objects WHERE id = :id")
    suspend fun knownObject(id: Long): KnownObjectEntity?

    @Transaction
    @Query(
        """
        SELECT object_observations.id AS observationId,
               scans.id AS scanId,
               projects.id AS projectId,
               projects.name AS projectName,
               scans.capturedAt AS capturedAt,
               scans.imagePath AS imagePath,
               known_objects.canonicalName AS objectName,
               object_observations.detectedLabel AS detectedLabel,
               object_observations.confidence AS confidence,
               object_observations.workspaceZone AS workspaceZone
        FROM object_observations
        INNER JOIN scans ON scans.id = object_observations.scanId
        INNER JOIN projects ON projects.id = scans.projectId
        INNER JOIN known_objects ON known_objects.id = object_observations.knownObjectId
        WHERE lower(known_objects.canonicalName) LIKE '%' || lower(:name) || '%'
           OR lower(object_observations.detectedLabel) LIKE '%' || lower(:name) || '%'
        ORDER BY scans.capturedAt DESC
        """
    )
    suspend fun observationsMatching(name: String): List<ObservationWithContext>

    @Transaction
    suspend fun clearAll() {
        deleteProjects()
        deleteKnownObjects()
    }
}
