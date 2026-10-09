package dev.housepoints.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** DESIGN.md colour tokens. Fixed palette: dynamic colour is off so both parents' phones look identical. */
@Immutable
data class HpColors(
    val ground: Color,
    val surface: Color,
    val sunk: Color,
    val rule: Color,
    val ink: Color,
    val inkMuted: Color,
    val action: Color,
    val onAction: Color,
    val interest: Color,
    val deduct: Color,
    val scrim: Color,
    val children: List<ChildColor>,
    val isDark: Boolean,
) {
    fun child(index: Int): ChildColor = children[Math.floorMod(index, children.size)]
}

/** A child's identity colour: [fill] behind white initials, [accent] for strokes and text on the ground. */
@Immutable
data class ChildColor(val fill: Color, val accent: Color, val onFill: Color)

val LightColors = HpColors(
    ground = Color(0xFFF5F7F6),
    surface = Color(0xFFFFFFFF),
    sunk = Color(0xFFE9EEEC),
    rule = Color(0xFFD3DAD7),
    ink = Color(0xFF18232B),
    inkMuted = Color(0xFF52606A),
    action = Color(0xFF1E6B4E),
    onAction = Color(0xFFFFFFFF),
    interest = Color(0xFF8A5A00),
    deduct = Color(0xFFA3412A),
    scrim = Color(0x8C18232B),
    children = listOf(
        ChildColor(Color(0xFF2456A6), Color(0xFF2456A6), Color.White),
        ChildColor(Color(0xFF0E6E73), Color(0xFF0E6E73), Color.White),
        ChildColor(Color(0xFF7B3F8C), Color(0xFF7B3F8C), Color.White),
        ChildColor(Color(0xFF5C6B12), Color(0xFF5C6B12), Color.White),
        ChildColor(Color(0xFF4A44A8), Color(0xFF4A44A8), Color.White),
        ChildColor(Color(0xFFA63A62), Color(0xFFA63A62), Color.White),
    ),
    isDark = false,
)

val DarkColors = HpColors(
    ground = Color(0xFF121A1F),
    surface = Color(0xFF1B252B),
    sunk = Color(0xFF243037),
    rule = Color(0xFF2C3940),
    ink = Color(0xFFE6EDEA),
    inkMuted = Color(0xFFA3B1AB),
    action = Color(0xFF6CC9A0),
    onAction = Color(0xFF0D1A14),
    interest = Color(0xFFE3B55B),
    deduct = Color(0xFFF08C70),
    scrim = Color(0xB3000000),
    children = listOf(
        ChildColor(Color(0xFF2456A6), Color(0xFF8DB4F2), Color.White),
        ChildColor(Color(0xFF0E6E73), Color(0xFF6FCFD3), Color.White),
        ChildColor(Color(0xFF7B3F8C), Color(0xFFD3A2E0), Color.White),
        ChildColor(Color(0xFF5C6B12), Color(0xFFBFD06A), Color.White),
        ChildColor(Color(0xFF4A44A8), Color(0xFFADA9F5), Color.White),
        ChildColor(Color(0xFFA63A62), Color(0xFFF2A0BF), Color.White),
    ),
    isDark = true,
)
