package dev.housepoints.app

import dev.housepoints.app.family.Clock
import dev.housepoints.app.family.FamilyRepository
import dev.housepoints.app.sync.LinkFactory
import dev.housepoints.app.sync.SyncController
import dev.housepoints.app.sync.SyncState
import dev.housepoints.contracts.InstantMs
import dev.housepoints.data.SyncHistoryStore
import dev.housepoints.sync.FamilyKey
import dev.housepoints.sync.InMemoryOpLog
import dev.housepoints.sync.LinkPeer
import dev.housepoints.sync.LoopbackTransport
import dev.housepoints.sync.PeerLink
import dev.housepoints.sync.SimDevice
import dev.housepoints.sync.SyncOutcome
import dev.housepoints.sync.TestOps
import dev.housepoints.sync.Transport
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Collections

/**
 * The Sync screen's one manual sync, run against a link that behaves like the local-network one when it is
 * stopped. Real dispatchers: the history store writes a real file.
 */
class SyncControllerTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `finishing a manual sync stops the link without crashing the app`() = runBlocking {
        val uncaught = Collections.synchronizedList(mutableListOf<Throwable>())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> uncaught += e })
        val key = FamilyKey.generate()
        val self = TestOps.device(2)
        val other = SimDevice(TestOps.device(1), key)
        val clock = Clock { InstantMs(1_760_000_000_000) }
        val log = InMemoryOpLog()
        val controller = SyncController(
            scope, log, FamilyRepository(log, self, clock, Dispatchers.Default), SyncHistoryStore(temp.root), self, clock,
        ) {}
        val link = AcceptingLink(LinkPeer("other", other.id, "Other phone"))

        controller.startManual(LinkFactory { _, _ -> link }, TestOps.FAMILY, key, "This phone")
        val (mine, theirs) = LoopbackTransport.pair()
        val peerSide = async(Dispatchers.Default) { other.session(theirs).run() }
        link.incoming.send(mine)

        val finished = withTimeout(TIMEOUT_MS) { controller.state.first { it is SyncState.Finished } } as SyncState.Finished
        assertTrue(finished.outcome is SyncOutcome.Completed)
        assertTrue(peerSide.await() is SyncOutcome.Completed)
        withTimeout(TIMEOUT_MS) { scope.coroutineContext.job.children.forEach { it.join() } }
        assertEquals(emptyList<Throwable>(), uncaught.toList())
    }

    /** The other phone is visible and dials this one (its id sorts lower); stopping closes the incoming queue, as LanLink does. */
    private class AcceptingLink(private val peer: LinkPeer) : PeerLink {
        val incoming = Channel<Transport>(Channel.UNLIMITED)

        override fun peers(): Flow<List<LinkPeer>> = flow {
            emit(listOf(peer))
            awaitCancellation()
        }

        override suspend fun connect(peer: LinkPeer): Transport = error("this phone only accepts")

        override suspend fun awaitIncoming(): Transport = incoming.receive()

        override fun stop() {
            incoming.close()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
