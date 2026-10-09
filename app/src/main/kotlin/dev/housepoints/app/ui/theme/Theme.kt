package dev.housepoints.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** DESIGN.md spacing scale (dp). */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    val gutter = 16.dp

    /** NFR-A11Y-1: no touch target smaller than this. */
    val touch = 48.dp
}

/** DESIGN.md radius tokens: nothing else is used. */
object Radius {
    val small = RoundedCornerShape(4.dp)
    val medium = RoundedCornerShape(12.dp)
    val sheet = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
}

private val LocalColors = staticCompositionLocalOf { LightColors }
private val LocalType = staticCompositionLocalOf { HousePointsType }

object Hp {
    val colors: HpColors @Composable get() = LocalColors.current
    val type: HpType @Composable get() = LocalType.current
}

@Composable
fun HousePointsTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    val material = if (dark) {
        darkColorScheme(
            primary = colors.action, onPrimary = colors.onAction, background = colors.ground, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink, onSurfaceVariant = colors.inkMuted,
            surfaceVariant = colors.sunk, outline = colors.rule, outlineVariant = colors.rule, error = colors.deduct,
            surfaceContainerLow = colors.surface, surfaceContainer = colors.surface, surfaceContainerHigh = colors.surface,
        )
    } else {
        lightColorScheme(
            primary = colors.action, onPrimary = colors.onAction, background = colors.ground, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink, onSurfaceVariant = colors.inkMuted,
            surfaceVariant = colors.sunk, outline = colors.rule, outlineVariant = colors.rule, error = colors.deduct,
            surfaceContainerLow = colors.surface, surfaceContainer = colors.surface, surfaceContainerHigh = colors.surface,
        )
    }
    CompositionLocalProvider(LocalColors provides colors, LocalType provides HousePointsType) {
        MaterialTheme(
            colorScheme = material,
            shapes = Shapes(extraSmall = Radius.small, small = Radius.small, medium = Radius.medium, large = Radius.medium),
            content = content,
        )
    }
}
