package dev.housepoints.sync

import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.ChildUpsert
import dev.housepoints.contracts.DeviceRemoved
import dev.housepoints.contracts.Payload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Random
import java.util.UUID

/**
 * NFR-DET-2: whatever order three phones record and sync in, a final round of pairwise syncs leaves
 * them with identical op sets and identical version vectors.
 */
class ConvergencePropertyTest {
    private val iterations = 200
    private val stepsPerIteration = 25

    @Test
    fun `three devices converge after any interleaving of recording and syncing`() = runBlocking(Dispatchers.Default) {
        val baseSeed = System.getProperty("hp.seed")?.toLong() ?: 20261009L
        repeat(iterations) { iteration ->
            val seed = baseSeed + iteration
            val random = Random(seed)
            val key = FamilyKey.generate()
            val devices = (1..3).map { SimDevice(TestOps.device(it), key) }

            repeat(stepsPerIteration) {
                if (random.nextInt(3) == 0) {
                    val (x, y) = distinctPair(random)
                    SimDevice.sync(devices[x], devices[y])
                } else {
                    val device = devices[random.nextInt(3)]
                    device.factory.record(List(1 + random.nextInt(3)) { randomPayload(random) })
                }
            }
            SimDevice.sync(devices[0], devices[1])
            SimDevice.sync(devices[1], devices[2])
            SimDevice.sync(devices[0], devices[1])

            val reference = devices[0].log.all().toSet()
            devices.forEach { device ->
                assertEquals("seed $seed: op sets differ", reference, device.log.all().toSet())
                assertEquals("seed $seed: vectors differ", devices[0].log.vector(), device.log.vector())
            }
        }
    }

    private fun distinctPair(random: Random): Pair<Int, Int> {
        val x = random.nextInt(3)
        val y = (x + 1 + random.nextInt(2)) % 3
        return x to y
    }

    private fun randomPayload(random: Random): Payload =
        if (random.nextBoolean()) {
            ChildUpsert(ChildId(UUID(random.nextLong(), random.nextLong())), name = "child ${random.nextInt(100)}")
        } else {
            DeviceRemoved(TestOps.device(random.nextInt(10)))
        }
}
