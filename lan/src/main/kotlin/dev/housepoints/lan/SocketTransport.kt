package dev.housepoints.lan

import dev.housepoints.contracts.FamilyId
import dev.housepoints.sync.Transport
import java.net.InetAddress
import java.net.Socket

public class SocketTransport(private val socket: Socket) : Transport {
    override suspend fun send(frame: ByteArray): Unit = TODO("green")
    override suspend fun receive(): ByteArray? = TODO("green")
    override suspend fun close(): Unit = TODO("green")
}

public object LanAddresses {
    public const val FAMILY_TAG_CHARS: Int = 16
    public fun isLocal(address: InetAddress): Boolean = TODO("green")
    public fun familyTag(family: FamilyId): String = TODO("green")
}
