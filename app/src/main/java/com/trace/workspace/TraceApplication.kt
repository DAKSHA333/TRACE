package com.trace.workspace

import android.app.Application
import com.trace.workspace.data.TraceDatabase
import com.trace.workspace.data.TraceRepository

class TraceApplication : Application() {
    val database by lazy { TraceDatabase.create(this) }
    val repository by lazy { TraceRepository(database.traceDao(), filesDir) }
}
