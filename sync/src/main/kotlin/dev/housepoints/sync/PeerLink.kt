package dev.housepoints.sync

import dev.housepoints.contracts.DeviceId
import kotlinx.coroutines.flow.Flow

/** Another phone of the family that a [PeerLink] can currently reach; [address] is link-specific. */
public data class LinkPeer(val address: String, val device: DeviceId, val name: String)

/**
 * A way of finding family phones and opening [Transport]s to them (SAD ADR-9): the local network when both
 * phones share Wi-Fi, Nearby Connections otherwise. Security never depends on the link: every session is
 * authenticated and encrypted with the family key by [SyncSession].
 */
public interface PeerLink {
    /** Phones of the family currently reachable; discovery runs while this is collected. */
    public fun peers(): Flow<List<LinkPeer>>

    /** Opens a transport to [peer]; throws [java.io.IOException] when it cannot. */
    public suspend fun connect(peer: LinkPeer): Transport

    /** The next transport a peer opened to this phone; null once [stop] has been called. */
    public suspend fun awaitIncoming(): Transport?

    /** Stops discovery and closes every open transport; anyone waiting in [awaitIncoming] gets null. */
    public fun stop()
}
