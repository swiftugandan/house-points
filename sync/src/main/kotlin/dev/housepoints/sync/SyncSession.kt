package dev.housepoints.sync

import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.FamilyId
import dev.housepoints.contracts.InstantMs

/** One authenticated, encrypted exchange of missing ops with one peer (SAD §4.3). */
public class SyncSession(
    private val familyId: FamilyId,
    private val key: FamilyKey,
    private val self: DeviceId,
    private val log: OpLog,
    private val transport: Transport,
    private val clock: () -> InstantMs,
    private val receiveTimeoutMs: Long = DEFAULT_RECEIVE_TIMEOUT_MS,
) {
    public suspend fun run(): SyncOutcome = TODO("red")

    public companion object {
        public const val DEFAULT_RECEIVE_TIMEOUT_MS: Long = 30_000
    }
}
