package dev.housepoints.nearby

import org.junit.Assert.assertEquals
import org.junit.Test

class NearbyPermissionsTest {
    private val fine = "android.permission.ACCESS_FINE_LOCATION"
    private val coarse = "android.permission.ACCESS_COARSE_LOCATION"
    private val bluetooth = listOf(
        "android.permission.BLUETOOTH_ADVERTISE",
        "android.permission.BLUETOOTH_CONNECT",
        "android.permission.BLUETOOTH_SCAN",
    )
    private val wifi = "android.permission.NEARBY_WIFI_DEVICES"

    @Test
    fun `runtime permissions follow the Nearby table per API level`() {
        assertEquals(listOf(coarse), NearbyPermissions.required(28))
        assertEquals(listOf(fine), NearbyPermissions.required(29))
        assertEquals(listOf(fine), NearbyPermissions.required(30))
        assertEquals(listOf(fine) + bluetooth, NearbyPermissions.required(31))
        assertEquals(bluetooth, NearbyPermissions.required(32))
        assertEquals(bluetooth + wifi, NearbyPermissions.required(33))
        assertEquals(bluetooth + wifi, NearbyPermissions.required(35))
    }
}
