package dev.housepoints.data

import android.content.Context
import dev.housepoints.contracts.DeviceId

/** This installation's permanent identity, kept where Auto Backup never copies it (SPEC FR-2). */
public class DeviceIdentityStore(context: Context) {
    public fun deviceId(): DeviceId = TODO("red")
}
