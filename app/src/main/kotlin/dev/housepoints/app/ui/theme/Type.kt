package dev.housepoints.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.housepoints.app.R

@OptIn(ExperimentalTextApi::class)
private fun variable(resource: Int, weight: Int, vararg extra: FontVariation.Setting) = Font(
    resId = resource,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), *extra),
)

private val Bricolage = FontFamily(
    variable(R.font.bricolage, 600, FontVariation.Setting("opsz", 36f)),
    variable(R.font.bricolage, 700, FontVariation.Setting("opsz", 48f)),
)

private val AtkinsonNext = FontFamily(
    variable(R.font.atkinson_next, 400),
    variable(R.font.atkinson_next, 700),
)

private val AtkinsonMono = FontFamily(
    variable(R.font.atkinson_mono, 400),
    variable(R.font.atkinson_mono, 600),
)

/** DESIGN.md type scale (sp). Nothing smaller than 14 sp anywhere. */
@Immutable
data class HpType(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val figure: TextStyle,
    val figureStrong: TextStyle,
    val figureLarge: TextStyle,
    val pictureLabel: TextStyle,
    val initials: TextStyle,
)

val HousePointsType = HpType(
    display = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight(700), fontSize = 44.sp, lineHeight = 48.sp),
    headline = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight(600), fontSize = 26.sp, lineHeight = 32.sp),
    title = TextStyle(fontFamily = AtkinsonNext, fontWeight = FontWeight(700), fontSize = 18.sp, lineHeight = 24.sp),
    body = TextStyle(fontFamily = AtkinsonNext, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 24.sp),
    label = TextStyle(fontFamily = AtkinsonNext, fontWeight = FontWeight(700), fontSize = 14.sp, lineHeight = 20.sp),
    caption = TextStyle(fontFamily = AtkinsonNext, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 20.sp),
    figure = TextStyle(fontFamily = AtkinsonMono, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 24.sp),
    figureStrong = TextStyle(fontFamily = AtkinsonMono, fontWeight = FontWeight(600), fontSize = 16.sp, lineHeight = 24.sp),
    figureLarge = TextStyle(fontFamily = AtkinsonMono, fontWeight = FontWeight(600), fontSize = 26.sp, lineHeight = 32.sp),
    pictureLabel = TextStyle(fontFamily = AtkinsonNext, fontWeight = FontWeight(700), fontSize = 22.sp, lineHeight = 28.sp),
    initials = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight(700), fontSize = 22.sp, lineHeight = 24.sp),
)
