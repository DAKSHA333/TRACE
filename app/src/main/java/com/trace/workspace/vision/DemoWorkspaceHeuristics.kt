package com.trace.workspace.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.trace.workspace.domain.BoundingBox
import com.trace.workspace.domain.DetectedWorkspaceObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object DemoWorkspaceHeuristics {
    private data class Component(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val count: Int,
        val gridWidth: Int,
        val gridHeight: Int,
    ) {
        val width: Float get() = (right - left + 1).toFloat() / gridWidth
        val height: Float get() = (bottom - top + 1).toFloat() / gridHeight
        val centerX: Float get() = (left + right + 1).toFloat() / (2f * gridWidth)
        val centerY: Float get() = (top + bottom + 1).toFloat() / (2f * gridHeight)

        fun box(padding: Float = 0.015f): BoundingBox =
            BoundingBox(
                left = (left.toFloat() / gridWidth - padding).coerceIn(0f, 1f),
                top = (top.toFloat() / gridHeight - padding).coerceIn(0f, 1f),
                right = ((right + 1).toFloat() / gridWidth + padding).coerceIn(0f, 1f),
                bottom = ((bottom + 1).toFloat() / gridHeight + padding).coerceIn(0f, 1f),
            )
    }

    fun detect(bitmap: Bitmap): List<DetectedWorkspaceObject> {
        val detections = mutableListOf<DetectedWorkspaceObject>()
        detections += coloredObjectDetections(bitmap)
        detections += darkObjectDetections(bitmap)
        detections += lightObjectDetections(bitmap)
        detections += grayObjectDetections(bitmap)

        return detections
            .filter { it.box.right > it.box.left && it.box.bottom > it.box.top }
            .distinctBy { it.label }
            .take(10)
    }

    fun merge(
        modelDetections: List<DetectedWorkspaceObject>,
        heuristicDetections: List<DetectedWorkspaceObject>,
    ): List<DetectedWorkspaceObject> {
        val merged = mutableListOf<DetectedWorkspaceObject>()
        (modelDetections + heuristicDetections).forEach { candidate ->
            val normalized = candidate.label.normalizedLabel()
            val alreadyPresent = merged.any { existing ->
                val existingLabel = existing.label.normalizedLabel()
                existingLabel == normalized ||
                    existingLabel.contains(normalized) ||
                    normalized.contains(existingLabel) ||
                    existing.box.overlaps(candidate.box)
            }
            if (!alreadyPresent) merged += candidate
        }
        return merged.take(10)
    }

    private fun coloredObjectDetections(bitmap: Bitmap): List<DetectedWorkspaceObject> {
        val blue = components(bitmap) { r, g, b ->
            b > 95 && b > r + 25 && (b > g + 15 || g > 100)
        }
        val yellow = components(bitmap) { r, g, b ->
            r > 170 && g > 130 && b < 120 && r > b + 70
        }

        return buildList {
            blue.forEach { component ->
                when {
                    component.centerX > 0.62f && component.height > component.width * 1.15f ->
                        add(component.toDetection("bottle", "blue"))
                    component.centerX > 0.60f && component.width < 0.18f && component.height < 0.20f ->
                        add(component.toDetection("mouse", "blue"))
                    component.centerX < 0.40f && component.height > 0.16f ->
                        add(component.toDetection("blue notebook", "blue"))
                    component.centerX < 0.45f && component.height > component.width * 1.2f ->
                        add(component.toDetection("phone", "blue"))
                }
            }
            yellow.forEach { component ->
                when {
                    component.centerX < 0.35f && component.width > 0.10f && component.height > 0.18f ->
                        add(component.toDetection("yellow notebook", "yellow"))
                    component.width in 0.05f..0.18f && component.height in 0.05f..0.18f ->
                        add(component.toDetection("sticky note", "yellow"))
                }
            }
        }
    }

    private fun darkObjectDetections(bitmap: Bitmap): List<DetectedWorkspaceObject> {
        val dark = components(bitmap, minCount = 14) { r, g, b ->
            r < 55 && g < 55 && b < 55
        }
        return buildList {
            dark.forEach { component ->
                when {
                    component.centerX in 0.35f..0.70f && component.width > 0.22f && component.height > 0.12f ->
                        add(component.toDetection("laptop", "black"))
                    component.centerX > 0.68f && component.height > 0.18f && component.centerY < 0.52f ->
                        add(component.toDetection("phone", "black"))
                    component.centerX > 0.62f && component.height > 0.16f && component.centerY >= 0.35f ->
                        add(component.toDetection("calculator", "black"))
                }
            }
        }
    }

    private fun lightObjectDetections(bitmap: Bitmap): List<DetectedWorkspaceObject> {
        val light = components(bitmap, minCount = 18) { r, g, b ->
            r > 205 && g > 205 && b > 195 && abs(r - g) < 35 && abs(g - b) < 45
        }
        return buildList {
            light.forEach { component ->
                when {
                    component.centerX < 0.25f && component.centerY < 0.35f && component.width > 0.08f ->
                        add(component.toDetection("cup", "white"))
                    component.centerX < 0.32f && component.width > 0.10f && component.height > 0.10f ->
                        add(component.toDetection("charger", "white"))
                    component.centerX in 0.35f..0.72f && component.width > 0.20f && component.height < 0.18f ->
                        add(component.toDetection("keyboard", "white"))
                    component.centerX in 0.40f..0.65f && component.width < 0.14f && component.height < 0.12f ->
                        add(component.toDetection("earbuds case", "white"))
                }
            }
        }
    }

    private fun grayObjectDetections(bitmap: Bitmap): List<DetectedWorkspaceObject> {
        val gray = components(bitmap, minCount = 5) { r, g, b ->
            val maxChannel = maxOf(r, g, b)
            val minChannel = minOf(r, g, b)
            maxChannel in 70..185 && maxChannel - minChannel < 45
        }
        return buildList {
            gray.forEach { component ->
                if (
                    component.centerX in 0.25f..0.45f &&
                    component.centerY > 0.42f &&
                    component.width < 0.08f &&
                    component.height < 0.16f
                ) {
                    add(component.toDetection("pendrive", "gray"))
                }
            }
        }
    }

    private fun Component.toDetection(label: String, color: String): DetectedWorkspaceObject =
        DetectedWorkspaceObject(
            label = label,
            confidence = 0.86f,
            box = box(),
            color = color,
        )

    private fun components(
        bitmap: Bitmap,
        minCount: Int = 10,
        predicate: (r: Int, g: Int, b: Int) -> Boolean,
    ): List<Component> {
        val gridWidth = 96
        val gridHeight = max(1, (bitmap.height * gridWidth) / bitmap.width)
        val mask = BooleanArray(gridWidth * gridHeight)
        for (gy in 0 until gridHeight) {
            for (gx in 0 until gridWidth) {
                val x = ((gx + 0.5f) * bitmap.width / gridWidth).toInt().coerceIn(0, bitmap.width - 1)
                val y = ((gy + 0.5f) * bitmap.height / gridHeight).toInt().coerceIn(0, bitmap.height - 1)
                val pixel = bitmap.getPixel(x, y)
                mask[gy * gridWidth + gx] = predicate(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
            }
        }

        val visited = BooleanArray(mask.size)
        val queueX = IntArray(mask.size)
        val queueY = IntArray(mask.size)
        val found = mutableListOf<Component>()

        for (startY in 0 until gridHeight) {
            for (startX in 0 until gridWidth) {
                val startIndex = startY * gridWidth + startX
                if (!mask[startIndex] || visited[startIndex]) continue

                var head = 0
                var tail = 0
                var left = startX
                var right = startX
                var top = startY
                var bottom = startY
                var count = 0
                queueX[tail] = startX
                queueY[tail] = startY
                tail++
                visited[startIndex] = true

                while (head < tail) {
                    val x = queueX[head]
                    val y = queueY[head]
                    head++
                    count++
                    left = min(left, x)
                    right = max(right, x)
                    top = min(top, y)
                    bottom = max(bottom, y)

                    val neighbors = intArrayOf(x - 1, y, x + 1, y, x, y - 1, x, y + 1)
                    var i = 0
                    while (i < neighbors.size) {
                        val nx = neighbors[i]
                        val ny = neighbors[i + 1]
                        i += 2
                        if (nx !in 0 until gridWidth || ny !in 0 until gridHeight) continue
                        val index = ny * gridWidth + nx
                        if (mask[index] && !visited[index]) {
                            visited[index] = true
                            queueX[tail] = nx
                            queueY[tail] = ny
                            tail++
                        }
                    }
                }

                val component = Component(left, top, right, bottom, count, gridWidth, gridHeight)
                if (count >= minCount && component.width > 0.015f && component.height > 0.015f) {
                    found += component
                }
            }
        }
        return found.sortedByDescending { it.count }
    }

    private fun BoundingBox.overlaps(other: BoundingBox): Boolean {
        val left = max(this.left, other.left)
        val top = max(this.top, other.top)
        val right = min(this.right, other.right)
        val bottom = min(this.bottom, other.bottom)
        return right > left && bottom > top
    }

    private fun String.normalizedLabel(): String =
        lowercase()
            .replace("cell phone", "phone")
            .replace("book", "notebook")
            .replace("usb drive", "pendrive")
            .replace("pen drive", "pendrive")
}
