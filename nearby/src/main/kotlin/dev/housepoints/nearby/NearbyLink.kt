package dev.housepoints.nearby

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.FamilyId
import dev.housepoints.contracts.Uuids
import dev.housepoints.sync.Transport
import dev.housepoints.sync.TransportClosedException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Another phone of the same family, discovered nearby. */
public data class Peer(val endpointId: String, val deviceId: DeviceId, val name: String)

/** Nearby Connections refused to do something; [statusCode] is a `ConnectionsStatusCodes` value. */
public class NearbyException(message: String, public val statusCode: Int?) : IOException(message)

/**
 * Discovery, advertising and connections over Nearby Connections (SAD §3.5, ADR-5).
 *
 * - `Strategy.P2P_POINT_TO_POINT`; the service id embeds the family id, so only phones of the same family
 *   find each other. The endpoint name is `deviceId|display name`.
 * - Both phones advertise and discover while [peers] is collected. To avoid both requesting at once,
 *   the phone with the lower [DeviceId] requests and the other waits; both call [connect].
 * - Nearby's 4-digit authentication token is accepted automatically: membership is proven by the family
 *   key inside the sync session (SAD ADR-4), not by Nearby.
 * - Frames travel as chunked `BYTES` payloads through [FramedTransport]. Closing a transport waits, for
 *   a bounded time, until Nearby reports every outgoing chunk transferred, then disconnects, so the last
 *   DONE frame of a session is not lost to an early disconnect.
 */
public class NearbyLink(context: Context, familyId: FamilyId, private val self: DeviceId, selfName: String) {
    private val client: ConnectionsClient = Nearby.getConnectionsClient(context.applicationContext)
    private val serviceId = SERVICE_PREFIX + familyId
    private val endpointName = "$self$NAME_SEPARATOR${selfName.take(MAX_NAME_CHARS)}"

    private val found = MutableStateFlow<Map<String, Peer>>(emptyMap())
    private val connections = ConcurrentHashMap<String, Connection>()
    private val awaitingConnection = ConcurrentHashMap<String, CompletableDeferred<Transport>>()
    private val incoming = Channel<Transport>(Channel.UNLIMITED)

    /** Advertises and discovers while collected; emits the family's phones currently in range. */
    public fun peers(): Flow<List<Peer>> = callbackFlow {
        try {
            client.startAdvertising(endpointName, serviceId, lifecycle, AdvertisingOptions.Builder().setStrategy(STRATEGY).build()).await()
            client.startDiscovery(serviceId, discovery, DiscoveryOptions.Builder().setStrategy(STRATEGY).build()).await()
        } catch (e: ApiException) {
            close(NearbyException("could not start Nearby: ${e.message}", e.statusCode))
        }
        launch { found.collectLatest { send(it.values.sortedBy(Peer::name)) } }
        awaitClose {
            client.stopAdvertising()
            client.stopDiscovery()
            found.value = emptyMap()
        }
    }

    /** Connects to [peer]; throws [IOException] if Nearby refuses or nothing happens within the timeout. */
    public suspend fun connect(peer: Peer): Transport {
        val ready = awaitingConnection.getOrPut(peer.endpointId) { CompletableDeferred() }
        if (Uuids.compare(self.uuid, peer.deviceId.uuid) < 0) {
            try {
                client.requestConnection(endpointName, peer.endpointId, lifecycle).await()
            } catch (e: ApiException) {
                awaitingConnection.remove(peer.endpointId)
                throw NearbyException("connection request refused: ${e.message}", e.statusCode)
            }
        }
        return withTimeoutOrNull(CONNECT_TIMEOUT_MS) { ready.await() }
            ?: throw NearbyException("no connection with ${peer.name} within ${CONNECT_TIMEOUT_MS / MILLIS_PER_SECOND} s", null)
    }

    /** The next connection a peer opened that nobody was waiting for in [connect]; null once [stop] has been called. */
    public suspend fun awaitIncoming(): Transport? = incoming.receiveCatching().getOrNull()

    /** Stops advertising and discovery and drops every connection; a link is not reused after this. */
    public fun stop() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        connections.values.forEach { it.transport.onRemoteClosed() }
        connections.clear()
        awaitingConnection.values.forEach { it.completeExceptionally(NearbyException("link stopped", null)) }
        awaitingConnection.clear()
        incoming.close()
    }

    private inner class Connection(val endpointId: String) {
        val outgoing = MutableStateFlow<Set<Long>>(emptySet())
        val transport = FramedTransport(sink = ::deliver, onClose = ::disconnect)

        private suspend fun deliver(chunk: ByteArray) {
            val payload = Payload.fromBytes(chunk)
            outgoing.update { it + payload.id }
            try {
                client.sendPayload(endpointId, payload).await()
            } catch (e: ApiException) {
                outgoing.update { it - payload.id }
                throw TransportClosedException("Nearby could not send: ${e.message}")
            }
        }

        private suspend fun disconnect() {
            withTimeoutOrNull(DRAIN_TIMEOUT_MS) { outgoing.first { it.isEmpty() } }
            client.disconnectFromEndpoint(endpointId)
            connections.remove(endpointId)
        }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            if (payload.type == Payload.Type.BYTES) connections[endpointId]?.transport?.onChunk(bytes)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.IN_PROGRESS) return
            connections[endpointId]?.outgoing?.update { it - update.payloadId }
            if (update.status != PayloadTransferUpdate.Status.SUCCESS) connections[endpointId]?.transport?.onRemoteClosed()
        }
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            connections[endpointId] = Connection(endpointId)
            client.acceptConnection(endpointId, payloads)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            val waiting = awaitingConnection.remove(endpointId)
            val connection = connections[endpointId]
            if (resolution.status.isSuccess && connection != null) {
                if (waiting != null) waiting.complete(connection.transport) else incoming.trySend(connection.transport)
            } else {
                connections.remove(endpointId)
                waiting?.completeExceptionally(NearbyException("connection failed: ${resolution.status.statusMessage}", resolution.status.statusCode))
            }
        }

        override fun onDisconnected(endpointId: String) {
            connections.remove(endpointId)?.transport?.onRemoteClosed()
        }
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val peer = parsePeer(endpointId, info.endpointName) ?: return
            if (peer.deviceId != self) found.update { it + (endpointId to peer) }
        }

        override fun onEndpointLost(endpointId: String) {
            found.update { it - endpointId }
        }
    }

    private fun parsePeer(endpointId: String, endpointName: String): Peer? {
        val separator = endpointName.indexOf(NAME_SEPARATOR)
        if (separator < 0) return null
        val device = try {
            DeviceId(UUID.fromString(endpointName.substring(0, separator)))
        } catch (e: IllegalArgumentException) {
            return null
        }
        return Peer(endpointId, device, endpointName.substring(separator + 1))
    }

    private companion object {
        val STRATEGY: Strategy = Strategy.P2P_POINT_TO_POINT
        const val SERVICE_PREFIX = "dev.housepoints.sync."
        const val NAME_SEPARATOR = '|'
        const val MAX_NAME_CHARS = 40
        const val CONNECT_TIMEOUT_MS = 30_000L
        const val DRAIN_TIMEOUT_MS = 3_000L
        const val MILLIS_PER_SECOND = 1_000L
    }
}
