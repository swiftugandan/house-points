package dev.housepoints.lan

import dev.housepoints.contracts.FamilyId
import dev.housepoints.sync.Transport
import dev.housepoints.sync.TransportClosedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Socket
import java.security.MessageDigest

/**
 * A [Transport] over one TCP connection: each frame is `u32 length ‖ bytes`. Frames larger than
 * [MAX_FRAME_BYTES] are refused by closing the connection, so a misbehaving peer cannot make this phone
 * allocate without bound. Confidentiality and integrity come from the sync session above, not from here.
 */
public class SocketTransport(private val socket: Socket) : Transport {
    private val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
    private val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
    private val sendLock = Mutex()

    init {
        socket.tcpNoDelay = true
    }

    override suspend fun send(frame: ByteArray): Unit = sendLock.withLock {
        withContext(Dispatchers.IO) {
            try {
                output.writeInt(frame.size)
                output.write(frame)
                output.flush()
            } catch (e: IOException) {
                throw TransportClosedException("connection closed: ${e.message}")
            }
        }
    }

    override suspend fun receive(): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val length = input.readInt()
            if (length < 0 || length > MAX_FRAME_BYTES) {
                socket.close()
                null
            } else {
                ByteArray(length).also(input::readFully)
            }
        } catch (e: IOException) {
            // End of stream or a reset: either way the transport is closed (Transport contract).
            null
        }
    }

    override suspend fun close(): Unit = withContext(Dispatchers.IO) {
        try {
            output.flush()
        } catch (e: IOException) {
            // Already closed by the peer; nothing left to flush.
        }
        socket.close()
    }

    public companion object {
        public const val MAX_FRAME_BYTES: Int = 1024 * 1024
    }
}

/** The home-network rules shared by discovery, accepting and connecting (SAD ADR-9). */
public object LanAddresses {
    public const val FAMILY_TAG_CHARS: Int = 16
    private const val ULA_MASK = 0xFE
    private const val ULA_PREFIX = 0xFC

    /** Private IPv4 ranges, link-local, loopback and IPv6 unique-local: never a public address. */
    public fun isLocal(address: InetAddress): Boolean =
        address.isSiteLocalAddress || address.isLinkLocalAddress || address.isLoopbackAddress ||
            (address is Inet6Address && (address.address[0].toInt() and ULA_MASK) == ULA_PREFIX)

    /**
     * What a phone advertises on the network to say which family it belongs to: a hash, so the family id
     * itself is never broadcast. Finding a tag proves nothing; the session's key check does that.
     */
    public fun familyTag(family: FamilyId): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("hp-family:$family".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(FAMILY_TAG_CHARS)
    }
}
