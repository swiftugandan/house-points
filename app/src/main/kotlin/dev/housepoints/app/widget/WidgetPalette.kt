package dev.housepoints.app.widget

import android.content.res.Configuration
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import dev.housepoints.app.R
import dev.housepoints.app.ui.theme.DarkColors
import dev.housepoints.app.ui.theme.HpColors
import dev.housepoints.app.ui.theme.LightColors
import androidx.glance.color.ColorProvider as dayNight

/**
 * Every colour and shape the widget draws, chosen together so the widget is never half light and half dark.
 *
 * From API 31 the launcher resolves both at draw time, through day/night colours and night-qualified drawables, so
 * the widget follows the system theme by itself. Below 31, Glance bakes text colours in when it renders while
 * the launcher would still resolve night-qualified drawables when it redraws; so there, everything is resolved
 * once from [AppGraph.night][dev.housepoints.app.AppGraph.night], which the widget collects as state.
 */
internal class WidgetPalette(
    @DrawableRes val page: Int,
    @DrawableRes val record: Int,
    val ink: ColorProvider,
    val inkMuted: ColorProvider,
    val interest: ColorProvider,
    val rule: ColorProvider,
    val onRecord: ColorProvider,
) {
    companion object {
        fun of(night: Boolean): WidgetPalette = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> WidgetPalette(
                R.drawable.widget_page, R.drawable.widget_record,
                ink = adaptive { it.ink }, inkMuted = adaptive { it.inkMuted }, interest = adaptive { it.interest },
                rule = adaptive { it.rule }, onRecord = adaptive { it.onAction },
            )
            night -> fixed(DarkColors, R.drawable.widget_page_dark, R.drawable.widget_record_dark)
            else -> LIGHT
        }

        /** The palette before anything has been resolved: light, as the app's own default. */
        val LIGHT: WidgetPalette = fixed(LightColors, R.drawable.widget_page_light, R.drawable.widget_record_light)

        fun isNight(configuration: Configuration): Boolean =
            (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        private fun adaptive(token: (HpColors) -> Color): ColorProvider = dayNight(day = token(LightColors), night = token(DarkColors))

        private fun fixed(c: HpColors, @DrawableRes page: Int, @DrawableRes record: Int) = WidgetPalette(
            page, record, ColorProvider(c.ink), ColorProvider(c.inkMuted), ColorProvider(c.interest), ColorProvider(c.rule), ColorProvider(c.onAction),
        )
    }
}

internal val LocalWidgetPalette = staticCompositionLocalOf { WidgetPalette.LIGHT }
