package dev.housepoints.app

import android.app.Application

class HousePointsApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}
