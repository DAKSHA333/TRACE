package com.trace.workspace.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class QueryEngineTest {
    @Test
    fun parsesLastSeenObjectQuery() {
        val parsed = QueryParser.parse("Where is my blue notebook?")

        assertEquals(TraceIntent.LAST_SEEN, parsed.intent)
        assertEquals("blue notebook", parsed.objectName)
    }

    @Test
    fun parsesCompareQuery() {
        val parsed = QueryParser.parse("What changed since my previous scan?")

        assertEquals(TraceIntent.COMPARE_SCANS, parsed.intent)
    }

    @Test
    fun parsesBareObjectNameAsLastSeenSearch() {
        val parsed = QueryParser.parse("bottle")

        assertEquals(TraceIntent.LAST_SEEN, parsed.intent)
        assertEquals("bottle", parsed.objectName)
    }
}
