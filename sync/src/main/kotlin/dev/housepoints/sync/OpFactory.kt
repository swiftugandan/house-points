package dev.housepoints.sync

import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.FamilyId
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.Op
import dev.housepoints.contracts.Payload

/** Creates this device's ops: next sequence numbers, Lamport values above anything seen, v7 ids. */
public class OpFactory(
    private val familyId: FamilyId,
    private val self: DeviceId,
    private val log: OpLog,
    private val clock: () -> InstantMs,
) {
    public suspend fun record(payloads: List<Payload>): List<Op> = TODO("red")
}
