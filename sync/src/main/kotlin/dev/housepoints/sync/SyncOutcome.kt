package dev.housepoints.sync

import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.InstantMs

public enum class RejectReason { WRONG_FAMILY, AUTHENTICATION_FAILED, PROTOCOL_VERSION, PROTOCOL_ERROR, TAMPERED }

/** How a [SyncSession] ended. */
public sealed interface SyncOutcome {
    public data class Completed(
        val peer: DeviceId,
        val sent: Int,
        val received: Int,
        val startedAt: InstantMs,
        val endedAt: InstantMs,
    ) : SyncOutcome

    /** Nothing after the failed check was applied. */
    public data class Rejected(val reason: RejectReason, val detail: String) : SyncOutcome

    /** The channel failed mid-session; every batch received before the failure is stored. */
    public data class Interrupted(val detail: String, val received: Int) : SyncOutcome
}
