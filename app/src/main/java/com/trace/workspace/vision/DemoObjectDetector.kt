package com.trace.workspace.vision

import com.trace.workspace.domain.BoundingBox
import com.trace.workspace.domain.DetectedWorkspaceObject

interface WorkspaceObjectDetector {
    suspend fun detect(imagePath: String): List<DetectedWorkspaceObject>
}

class DemoObjectDetector : WorkspaceObjectDetector {
    override suspend fun detect(imagePath: String): List<DetectedWorkspaceObject> =
        listOf(
            DetectedWorkspaceObject("laptop", 0.93f, BoundingBox(0.08f, 0.18f, 0.48f, 0.55f), "silver"),
            DetectedWorkspaceObject("blue notebook", 0.88f, BoundingBox(0.55f, 0.20f, 0.86f, 0.52f), "blue"),
            DetectedWorkspaceObject("esp32", 0.81f, BoundingBox(0.18f, 0.62f, 0.38f, 0.82f), "black"),
            DetectedWorkspaceObject("usb-c hub", 0.78f, BoundingBox(0.64f, 0.60f, 0.82f, 0.77f), "gray"),
        )
}
