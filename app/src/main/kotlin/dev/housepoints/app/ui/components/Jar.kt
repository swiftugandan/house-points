package dev.housepoints.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.housepoints.app.ui.theme.ChildColor
import dev.housepoints.app.ui.theme.Hp
import dev.housepoints.contracts.Points

/** How a balance becomes coins for Picture style (DESIGN.md "Child view: Picture style"). */
data class JarModel(val coins: Int, val partial: Boolean, val coinValue: Long, val goalCoins: Int?) {
    companion object {
        const val COLUMNS = 5
        const val LEVELS = 40
        private const val SMALL_COIN = 10L
        private const val BIG_COIN = 100L

        fun of(balance: Points, goal: Points?): JarModel {
            val largest = maxOf(balance.value, goal?.value ?: 0L)
            val value = if (largest <= SMALL_COIN * COLUMNS * LEVELS) SMALL_COIN else BIG_COIN
            val positive = balance.value.coerceAtLeast(0)
            return JarModel(
                coins = (positive / value).toInt().coerceAtMost(COLUMNS * LEVELS),
                partial = positive % value != 0L,
                coinValue = value,
                goalCoins = goal?.value?.let { ((it + value - 1) / value).toInt().coerceAtMost(COLUMNS * LEVELS) },
            )
        }
    }
}

/**
 * The jar: coins fill level by level (five to a level) so the goal line sits exactly where the target is.
 * New coins drop in over 360 ms each, ease-out without overshoot, and only when animations are enabled.
 */
@Composable
fun Jar(model: JarModel, colour: ChildColor, goalIcon: ImageVector?, description: String, animate: Boolean, modifier: Modifier = Modifier) {
    val ink = Hp.colors.ink
    val lid = Hp.colors.sunk
    val glass = Hp.colors.surface
    val layout = remember(model) { JarLayout.of(model) }
    val shown = remember { Animatable(if (animate) (model.coins - NEW_COINS_TO_DROP).coerceAtLeast(0).toFloat() else model.coins.toFloat()) }
    LaunchedEffect(model.coins, animate) {
        if (animate) shown.animateTo(model.coins.toFloat(), tween(durationMillis = DROP_MS * NEW_COINS_TO_DROP, easing = FastOutSlowInEasing))
        else shown.snapTo(model.coins.toFloat())
    }
    Layout(
        modifier = modifier.aspectRatio(JAR_ASPECT).semantics { contentDescription = description },
        content = {
            Canvas(Modifier.fillMaxSize()) {
                val geometry = JarGeometry(size)
                drawJar(geometry, ink, lid, glass)
                drawCoins(geometry, layout, shown.value, model.partial && shown.value >= model.coins, colour, ink)
                model.goalCoins?.let { drawGoalLine(geometry, layout, it, ink) }
            }
            if (goalIcon != null && model.goalCoins != null) {
                Box {
                    Icon(goalIcon, contentDescription = null, tint = colour.accent, modifier = Modifier.fillMaxSize())
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = (width / JAR_ASPECT).toInt()
        val canvas = measurables[0].measure(Constraints.fixed(width, height))
        val geometry = JarGeometry(Size(width.toFloat(), height.toFloat()))
        val iconPlaceable = measurables.getOrNull(1)?.measure(Constraints.fixed(geometry.iconSize.toInt(), geometry.iconSize.toInt()))
        layout(width, height) {
            canvas.place(0, 0)
            if (iconPlaceable != null && model.goalCoins != null) {
                val y = geometry.levelTop(layout, model.goalCoins) - geometry.iconSize / 2
                iconPlaceable.place((geometry.right + geometry.iconGap).toInt(), y.toInt())
            }
        }
    }
}

private const val JAR_ASPECT = 340f / 400f
private const val NEW_COINS_TO_DROP = 3
private const val DROP_MS = 360

/** Proportions taken from the concept board's 340 × 400 jar. */
private class JarGeometry(size: Size) {
    private val unit = size.width / 340f
    val left = 30f * unit
    val right = 250f * unit
    val bottom = 380f * unit
    val innerLeft = left + 14f * unit
    val innerRight = right - 14f * unit
    val innerBottom = bottom - 12f * unit
    val iconSize = 40f * unit
    val iconGap = 18f * unit
    val stroke = 2.dp
    val unitPx = unit

    /** Just above the top of the [coins]-th coin: where the goal line goes. */
    fun levelTop(layout: JarLayout, coins: Int): Float {
        val levels = layout.levelsFor(coins).coerceAtLeast(1)
        return innerBottom - (((levels - 1) * layout.step + layout.coinHeight) * unit).toFloat() - LINE_GAP * unit
    }

    fun coinTopLeft(layout: JarLayout, index: Int, drop: Float): Offset {
        val columnWidth = JarLayout.INNER_WIDTH / layout.columns
        val column = index % layout.columns
        val level = index / layout.columns
        val x = innerLeft + ((column * columnWidth + (columnWidth - layout.coinWidth) / 2) * unit).toFloat()
        val y = innerBottom - ((level * layout.step + layout.coinHeight) * unit).toFloat() - drop
        return Offset(x, y)
    }

    fun coinSize(layout: JarLayout): Size = Size((layout.coinWidth * unit).toFloat(), (layout.coinHeight * unit).toFloat())

    private companion object {
        const val LINE_GAP = 4f
    }
}

private fun DrawScope.drawJar(g: JarGeometry, ink: androidx.compose.ui.graphics.Color, lid: androidx.compose.ui.graphics.Color, glass: androidx.compose.ui.graphics.Color) {
    val u = g.unitPx
    val body = Path().apply {
        moveTo(92f * u, 44f * u)
        lineTo(92f * u, 62f * u)
        cubicTo(92f * u, 72f * u, 30f * u, 84f * u, 30f * u, 122f * u)
        lineTo(30f * u, 340f * u)
        cubicTo(30f * u, 364f * u, 46f * u, 380f * u, 70f * u, 380f * u)
        lineTo(210f * u, 380f * u)
        cubicTo(234f * u, 380f * u, 250f * u, 364f * u, 250f * u, 340f * u)
        lineTo(250f * u, 122f * u)
        cubicTo(250f * u, 84f * u, 188f * u, 72f * u, 188f * u, 62f * u)
        lineTo(188f * u, 44f * u)
    }
    drawPath(body, glass)
    drawPath(body, ink, style = Stroke(width = g.stroke.toPx(), cap = StrokeCap.Round))
    drawRoundRect(lid, topLeft = Offset(78f * u, 14f * u), size = Size(124f * u, 30f * u), cornerRadius = CornerRadius(6f * u))
    drawRoundRect(ink, topLeft = Offset(78f * u, 14f * u), size = Size(124f * u, 30f * u), cornerRadius = CornerRadius(6f * u), style = Stroke(g.stroke.toPx()))
}

private fun DrawScope.drawCoins(g: JarGeometry, layout: JarLayout, shown: Float, partial: Boolean, colour: ChildColor, ink: androidx.compose.ui.graphics.Color) {
    val whole = shown.toInt()
    val size = g.coinSize(layout)
    val strokePx = 1.5.dp.toPx()
    fun coin(topLeft: Offset) {
        drawOval(colour.fill, topLeft, size)
        drawOval(ink, topLeft, size, style = Stroke(strokePx))
    }
    for (index in 0 until whole) coin(g.coinTopLeft(layout, index, 0f))
    val falling = shown - whole
    if (falling > 0f) coin(g.coinTopLeft(layout, whole, (1f - falling) * size.height * DROP_HEIGHT_IN_COINS))
    if (partial) {
        drawOval(
            colour.accent, g.coinTopLeft(layout, whole, 0f), size,
            style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
        )
    }
}

private fun DrawScope.drawGoalLine(g: JarGeometry, layout: JarLayout, goalCoins: Int, ink: androidx.compose.ui.graphics.Color) {
    val y = g.levelTop(layout, goalCoins)
    drawLine(
        ink, Offset(g.left + 8f * g.unitPx, y), Offset(g.right - 8f * g.unitPx, y),
        strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 7.dp.toPx())),
    )
}

private const val DROP_HEIGHT_IN_COINS = 8f

/**
 * Where coins sit in the jar, in the jar's own 340-unit drawing coordinates. Picks the largest coins (fewest
 * columns) that fit, then spaces levels so the goal (or the balance, with no goal) fills about three
 * quarters of the jar: big coins and a high goal line for small numbers, a full jar for big ones.
 */
data class JarLayout(val columns: Int, val coinWidth: Double, val coinHeight: Double, val step: Double) {
    fun levelsFor(coins: Int): Int = (coins + columns - 1) / columns

    companion object {
        const val INNER_WIDTH: Double = 192.0
        const val USABLE_HEIGHT: Double = 238.0
        private const val TARGET_FILL = 0.75
        private const val MAX_FILL = 0.85
        private const val WIDTH_FILL = 0.86
        private const val COIN_ASPECT = 0.3
        private const val MIN_OVERLAP = 0.45
        private const val MAX_COLUMNS = 5
        private const val NO_GOAL_MINIMUM = 10

        fun of(model: JarModel): JarLayout {
            val needed = model.goalCoins?.let { maxOf(it, model.coins, 1) } ?: maxOf(model.coins, NO_GOAL_MINIMUM)
            val columns = (1..MAX_COLUMNS).firstOrNull { c ->
                ceilDiv(needed, c) * minStep(c) <= USABLE_HEIGHT * MAX_FILL
            } ?: MAX_COLUMNS
            val width = INNER_WIDTH / columns * WIDTH_FILL
            val height = width * COIN_ASPECT
            val levels = ceilDiv(needed, columns).coerceAtLeast(1)
            val step = (USABLE_HEIGHT * TARGET_FILL / levels).coerceIn(minStep(columns), height)
            return JarLayout(columns, width, height, minOf(step, USABLE_HEIGHT / maxOf(levels, ceilDiv(model.coins, columns))))
        }

        private fun minStep(columns: Int): Double = INNER_WIDTH / columns * WIDTH_FILL * COIN_ASPECT * MIN_OVERLAP

        private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b
    }
}
