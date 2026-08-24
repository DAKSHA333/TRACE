package com.trace.workspace.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceReasonerTest {
    @Test
    fun calculatesWorkspaceZone() {
        val zone = WorkspaceReasoner.zoneFor(BoundingBox(0.05f, 0.68f, 0.20f, 0.90f))

        assertEquals("left front", zone)
    }

    @Test
    fun detectsAddedAndRemovedObjects() {
        val change = WorkspaceReasoner.changed(
            previous = listOf("Notebook", "USB-C Hub"),
            current = listOf("Notebook", "ESP32"),
        )

        assertEquals(listOf("ESP32"), change.added)
        assertEquals(listOf("USB-C Hub"), change.removed)
        assertEquals(listOf("Notebook"), change.retained)
    }
}
