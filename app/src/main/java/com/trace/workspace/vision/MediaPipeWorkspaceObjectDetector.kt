package com.trace.workspace.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.trace.workspace.domain.BoundingBox
import com.trace.workspace.domain.DetectedWorkspaceObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

class MediaPipeWorkspaceObjectDetector(
    private val context: Context,
) : WorkspaceObjectDetector {
    private val fallback = DemoObjectDetector()

    override suspend fun detect(imagePath: String): List<DetectedWorkspaceObject> = withContext(Dispatchers.Default) {
        runCatching {
            val bitmap = loadBitmap(imagePath)
            val detector = createDetector()
            val result = detector.detect(BitmapImageBuilder(bitmap).build())
            detector.close()

            result.detections()
                .mapNotNull { detection ->
                    val category = detection.categories().maxByOrNull { it.score() } ?: return@mapNotNull null
                    val box = detection.boundingBox()
                    val normalizedBox = BoundingBox(
                        left = (box.left / bitmap.width).coerceIn(0f, 1f),
                        top = (box.top / bitmap.height).coerceIn(0f, 1f),
                        right = (box.right / bitmap.width).coerceIn(0f, 1f),
                        bottom = (box.bottom / bitmap.height).coerceIn(0f, 1f),
                    )
                    DetectedWorkspaceObject(
                        label = friendlyLabel(category.categoryName()),
                        confidence = category.score(),
                        box = normalizedBox,
                        color = dominantColorName(bitmap, normalizedBox),
                    )
                }
                .filter { it.confidence >= 0.35f }
                .distinctBy { it.label.lowercase() }
                .take(8)
                .let { detections ->
                    DemoWorkspaceHeuristics.merge(
                        modelDetections = detections.ifEmpty { fallback.detect(imagePath) },
                        heuristicDetections = DemoWorkspaceHeuristics.detect(bitmap),
                    )
                }
        }.getOrElse {
            fallback.detect(imagePath)
        }
    }

    private fun createDetector(): ObjectDetector {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("efficientdet_lite0.tflite")
            .build()
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setMaxResults(8)
            .setScoreThreshold(0.35f)
            .build()
        return ObjectDetector.createFromOptions(context, options)
    }

    private fun loadBitmap(imagePath: String): Bitmap {
        val uri = Uri.parse(imagePath)
        val stream = when (uri.scheme) {
            "file" -> File(uri.path ?: imagePath).inputStream()
            "content" -> context.contentResolver.openInputStream(uri)
            else -> File(imagePath).inputStream()
        } ?: error("Could not open image")
        return stream.use { BitmapFactory.decodeStream(it) }
            ?: error("Could not decode image")
    }

    private fun friendlyLabel(label: String): String {
        return when (label.lowercase()) {
            "cell phone" -> "phone"
            "book" -> "notebook"
            "cup" -> "cup"
            "bottle" -> "bottle"
            "laptop" -> "laptop"
            "keyboard" -> "keyboard"
            "mouse" -> "mouse"
            "remote" -> "remote"
            else -> label.lowercase()
        }
    }

    private fun dominantColorName(bitmap: Bitmap, box: BoundingBox): String {
        val left = (box.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val top = (box.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val right = max(left + 1, (box.right * bitmap.width).toInt().coerceIn(0, bitmap.width))
        val bottom = max(top + 1, (box.bottom * bitmap.height).toInt().coerceIn(0, bitmap.height))

        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L
        val stepX = max(1, (right - left) / 12)
        val stepY = max(1, (bottom - top) / 12)

        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val pixel = bitmap.getPixel(min(x, bitmap.width - 1), min(y, bitmap.height - 1))
                red += Color.red(pixel)
                green += Color.green(pixel)
                blue += Color.blue(pixel)
                count++
                x += stepX
            }
            y += stepY
        }

        if (count == 0L) return "unknown"
        val r = (red / count).toInt()
        val g = (green / count).toInt()
        val b = (blue / count).toInt()
        val maxChannel = maxOf(r, g, b)
        val minChannel = minOf(r, g, b)

        return when {
            maxChannel < 45 -> "black"
            minChannel > 210 -> "white"
            maxChannel - minChannel < 25 -> "gray"
            b > r + 35 && b > g + 20 -> "blue"
            r > g + 35 && r > b + 35 -> "red"
            g > r + 25 && g > b + 25 -> "green"
            r > 160 && g > 120 && b < 100 -> "yellow"
            else -> "mixed"
        }
    }
}
