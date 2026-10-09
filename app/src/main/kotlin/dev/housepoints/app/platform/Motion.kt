package dev.housepoints.app.platform

import android.content.Context
import android.provider.Settings

/** NFR-A11Y-3: the system "remove animations" setting turns every decorative motion off. */
object Motion {
    fun enabled(context: Context): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
}
