package dev.housepoints.app

import android.app.Application
import android.content.res.Configuration

class HousePointsApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        graph.onConfigurationChanged(newConfig)
    }
}
