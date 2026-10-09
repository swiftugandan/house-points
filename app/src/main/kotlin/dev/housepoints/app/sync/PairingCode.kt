package dev.housepoints.app.sync

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dev.housepoints.contracts.FamilyId
import dev.housepoints.contracts.Uuids
import dev.housepoints.sync.FamilyKey
import java.nio.ByteBuffer
import java.util.Base64

/**
 * SAD ADR-8: `hp1:` + base64url(familyId ‖ familyKey). Shown as a QR code on a phone already in the family;
 * the same text can be sent by hand if the scanner is unavailable.
 */
object PairingCode {
    private const val PREFIX = "hp1:"
    private const val KEY_BYTES = 32

    fun encode(family: FamilyId, key: FamilyKey): String {
        val bytes = ByteBuffer.allocate(Uuids.BYTES + KEY_BYTES).apply {
            Uuids.write(this, family.uuid)
            put(key.bytes())
        }.array()
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun decode(text: String): Pair<FamilyId, FamilyKey>? {
        val trimmed = text.trim()
        if (!trimmed.startsWith(PREFIX)) return null
        val bytes = try {
            Base64.getUrlDecoder().decode(trimmed.removePrefix(PREFIX))
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (bytes.size != Uuids.BYTES + KEY_BYTES) return null
        val buffer = ByteBuffer.wrap(bytes)
        val family = FamilyId(Uuids.read(buffer))
        val key = FamilyKey.of(ByteArray(KEY_BYTES).also { buffer.get(it) }) ?: return null
        return family to key
    }

    /** A square QR code, black on white whatever the theme, because scanners expect dark modules on light. */
    fun qr(text: String, sizePx: Int): ImageBitmap {
        val matrix = QRCodeWriter().encode(
            text, BarcodeFormat.QR_CODE, sizePx, sizePx,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2),
        )
        val pixels = IntArray(sizePx * sizePx) { i -> if (matrix.get(i % sizePx, i / sizePx)) DARK else LIGHT }
        return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    private const val DARK = 0xFF18232B.toInt()
    private const val LIGHT = 0xFFFFFFFF.toInt()
}
