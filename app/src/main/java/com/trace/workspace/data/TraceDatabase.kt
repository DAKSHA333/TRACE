package com.trace.workspace.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class,
        ScanEntity::class,
        KnownObjectEntity::class,
        ObjectObservationEntity::class,
        ObjectRelationshipEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class TraceDatabase : RoomDatabase() {
    abstract fun traceDao(): TraceDao

    companion object {
        fun create(context: Context): TraceDatabase =
            Room.databaseBuilder(context, TraceDatabase::class.java, "trace.db")
                .build()
    }
}
