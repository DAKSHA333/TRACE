package com.trace.workspace.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val createdAt: Long,
)

@Entity(
    tableName = "scans",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("projectId")],
)
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val capturedAt: Long,
    val imagePath: String,
    val scanType: String,
)

@Entity(tableName = "known_objects")
data class KnownObjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val canonicalName: String,
    val colour: String,
    val description: String,
    val embedding: String,
    val createdAt: Long,
)

@Entity(
    tableName = "object_observations",
    foreignKeys = [
        ForeignKey(
            entity = ScanEntity::class,
            parentColumns = ["id"],
            childColumns = ["scanId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = KnownObjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["knownObjectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("scanId"), Index("knownObjectId")],
)
data class ObjectObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scanId: Long,
    val knownObjectId: Long,
    val detectedLabel: String,
    val confidence: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val workspaceZone: String,
    val croppedImagePath: String,
)

@Entity(
    tableName = "object_relationships",
    foreignKeys = [
        ForeignKey(
            entity = ScanEntity::class,
            parentColumns = ["id"],
            childColumns = ["scanId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("scanId"), Index("firstObjectId"), Index("secondObjectId")],
)
data class ObjectRelationshipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scanId: Long,
    val firstObjectId: Long,
    val relationship: String,
    val secondObjectId: Long,
)

data class ObservationWithContext(
    val observationId: Long,
    val scanId: Long,
    val projectId: Long,
    val projectName: String,
    val capturedAt: Long,
    val imagePath: String,
    val objectName: String,
    val detectedLabel: String,
    val confidence: Float,
    val workspaceZone: String,
)
