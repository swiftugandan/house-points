package dev.housepoints.app.sync

import dev.housepoints.nearby.NearbyLink
import dev.housepoints.nearby.Peer
import dev.housepoints.sync.LinkPeer
import dev.housepoints.sync.PeerLink
import dev.housepoints.sync.Transport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Nearby Connections as a [PeerLink]: the Bluetooth fallback when the phones don't share Wi-Fi. */
class NearbyPeerLink(private val nearby: NearbyLink) : PeerLink {
    override fun peers(): Flow<List<LinkPeer>> =
        nearby.peers().map { peers -> peers.map { LinkPeer(it.endpointId, it.deviceId, it.name) } }

    override suspend fun connect(peer: LinkPeer): Transport = nearby.connect(Peer(peer.address, peer.device, peer.name))

    override suspend fun awaitIncoming(): Transport = nearby.awaitIncoming()

    override fun stop() = nearby.stop()
}
