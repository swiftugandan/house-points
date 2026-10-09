package dev.housepoints.data

import dev.housepoints.contracts.Op

public sealed interface ExportResult {
    public data class Ok(val ops: List<Op>) : ExportResult
    /** The passphrase is wrong, or the file was altered: AES-GCM cannot tell the two apart. */
    public data object WrongPassphrase : ExportResult
    public data class Corrupt(val reason: String) : ExportResult
}

/** Passphrase-encrypted export of the whole op log (SPEC FR-46, SAD §3.4). */
public object ExportCodec {
    public const val DEFAULT_ITERATIONS: Int = 600_000

    public fun encrypt(ops: List<Op>, passphrase: CharArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray = TODO("red")

    public fun decrypt(bytes: ByteArray, passphrase: CharArray): ExportResult = TODO("red")
}
