package dev.housepoints.nearby

/**
 * Runtime ("dangerous") permissions Nearby Connections needs on a given Android API level, following
 * the Nearby "get started" table [SPEC R8; SAD §3.5]. The manifest declares the full set with the same
 * API-level bounds; this is the subset the app must ask the parent for.
 */
public object NearbyPermissions {
    private const val COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
    private const val FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    private const val BLUETOOTH_ADVERTISE = "android.permission.BLUETOOTH_ADVERTISE"
    private const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
    private const val BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
    private const val NEARBY_WIFI_DEVICES = "android.permission.NEARBY_WIFI_DEVICES"

    private const val LAST_COARSE_LOCATION_SDK = 28
    private const val LAST_FINE_LOCATION_SDK = 31
    private const val FIRST_BLUETOOTH_RUNTIME_SDK = 31
    private const val FIRST_NEARBY_WIFI_RUNTIME_SDK = 33

    public fun required(sdkInt: Int): List<String> = buildList {
        if (sdkInt <= LAST_COARSE_LOCATION_SDK) add(COARSE_LOCATION)
        if (sdkInt in LAST_COARSE_LOCATION_SDK + 1..LAST_FINE_LOCATION_SDK) add(FINE_LOCATION)
        if (sdkInt >= FIRST_BLUETOOTH_RUNTIME_SDK) addAll(listOf(BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT, BLUETOOTH_SCAN))
        if (sdkInt >= FIRST_NEARBY_WIFI_RUNTIME_SDK) add(NEARBY_WIFI_DEVICES)
    }
}
