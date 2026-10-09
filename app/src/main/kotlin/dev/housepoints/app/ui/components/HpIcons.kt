package dev.housepoints.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import dev.housepoints.contracts.IconKey

/**
 * The app's single icon set: 2 dp rounded strokes on a 24-unit grid (DESIGN.md "Iconography"), the same
 * drawings as the concept board. Tinted at the call site.
 */
object HpIcons {
    private fun stroke(name: String, pathData: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()

    val Plus by lazy { stroke("plus", "M12 5v14M5 12h14") }
    val Minus by lazy { stroke("minus", "M5 12h14") }
    val Sync by lazy { stroke("sync", "M21 12a9 9 0 0 1-15.5 6.2L3 16M3 12a9 9 0 0 1 15.5-6.2L21 8M21 3v5h-5M3 21v-5h5") }
    val Settings by lazy { stroke("settings", "M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6") }
    val Back by lazy { stroke("back", "M15 18l-6-6 6-6") }
    val Close by lazy { stroke("close", "M6 6l12 12M18 6L6 18") }
    val Share by lazy { stroke("share", "M4 12v7a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-7M12 3v12M8 7l4-4 4 4") }
    val Lock by lazy { stroke("lock", "M6 11h12v9H6zM8 11V8a4 4 0 0 1 8 0v3") }
    val Speaker by lazy { stroke("speaker", "M11 5L6 9H3v6h3l5 4zM15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13") }
    val Check by lazy { stroke("check", "M5 12l5 5 9-10") }
    val Phone by lazy { stroke("phone", "M7 2h10v20H7zM11 18h2") }
    val ChevronRight by lazy { stroke("chevron", "M9 6l6 6-6 6") }
    val Undo by lazy { stroke("undo", "M9 14L4 9l5-5M4 9h11a5 5 0 0 1 0 10h-3") }
    val Qr by lazy { stroke("qr", "M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h3v3h-3zM18 18h3v3h-3z") }
    val Edit by lazy { stroke("edit", "M4 20l1-4L16 5l3 3L8 19zM14 7l3 3") }

    /** Chore, value and goal pictures, keyed by the [IconKey] stored in ops. Order is the picker order. */
    val catalogue: List<Pair<IconKey, ImageVector>> by lazy {
        listOf(
            "bin" to "M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14M10 10v6M14 10v6",
            "dishes" to "M4 3v7a2 2 0 0 0 4 0V3M6 12v9M18 3c-2.5 1.5-3 5-3 8h3v10",
            "bed" to "M3 18V7M3 14h18M21 18v-4a3 3 0 0 0-3-3h-7v3M7 13a2 2 0 1 1 0-4 2 2 0 0 1 0 4z",
            "toys" to "M3 10h18v10H3zM3 10l2-5h14l2 5M10 14h4",
            "plant" to "M12 21v-8M12 13c0-4 3-7 8-7 0 5-3 8-8 7zM12 15c0-3-2-5-6-5 0 4 2 6 6 5z",
            "car" to "M3 17v-5l2-5h14l2 5v5zM3 12h18M7 17v2M17 17v2M7 14.5h1M16 14.5h1",
            "laundry" to "M4 3h16v18H4zM4 7h16M12 18a4 4 0 1 0 0-8 4 4 0 0 0 0 8z",
            "table" to "M3 10h18M6 10v10M18 10v10M8 6h8",
            "broom" to "M14 3l-4 9M7 12h8l2 9H5zM9 16v5M13 16v5",
            "shirt" to "M8 3L3 6l2 4 3-1v12h8V9l3 1 2-4-5-3c0 2-1.5 3-4 3S8 5 8 3z",
            "dog" to "M12 20c-3 0-6-1.5-6-4s3-5 6-5 6 2.5 6 5-3 4-6 4zM5 10a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3zM9 7a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3zM15 7a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3zM19 10a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z",
            "book" to "M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2zM4 21V5M8 7h7",
            "pencil" to "M4 20l1-4L16 5l3 3L8 19zM14 7l3 3",
            "music" to "M9 18V5l11-2v13M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0zM20 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z",
            "heart" to "M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z",
            "shield" to "M12 3l7 3v6c0 4-3 7-7 9-4-2-7-5-7-9V6z",
            "mountain" to "M3 20l6-10 4 6 3-4 5 8z",
            "star" to "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1-4.4-4.3 6.1-.9z",
            "people" to "M9 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM3 20v-1a5 5 0 0 1 10 0v1M16 11a3 3 0 1 0 0-6M21 20v-1a5 5 0 0 0-4-4.9",
            "smile" to "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM8 14c1 1.5 2.5 2 4 2s3-.5 4-2M9 9.5v.5M15 9.5v.5",
            "headphones" to "M4 15v-3a8 8 0 0 1 16 0v3M4 15h3v6H4zM17 15h3v6h-3z",
            "kite" to "M12 2l6 8-6 8-6-8zM12 18c0 2-2 3-3 4",
            "gift" to "M3 9h18v4H3zM5 13v8h14v-8M12 9v12M12 9c-2 0-4-1-4-3s3-2 4 3c1-5 4-5 4-3s-2 3-4 3",
            "bike" to "M5 18a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19 18a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM5 15l4-7h6l4 7M9 8l3 7H5M14 5h3",
        ).map { (key, path) -> IconKey(key) to stroke(key, path) }
    }

    private val byKey: Map<IconKey, ImageVector> by lazy { catalogue.toMap() }

    /** Unknown keys (from a newer app version) fall back to the star. */
    fun of(key: IconKey): ImageVector = byKey[key] ?: byKey.getValue(IconKey("star"))
}
