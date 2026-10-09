package dev.housepoints.contracts

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Canonical lower-case hyphenated form. */
public object UuidSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("dev.housepoints.UUID", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: UUID): Unit = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

/** ISO-8601 calendar date, e.g. `2026-10-05`. */
public object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("dev.housepoints.LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate): Unit = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

/** IANA tz identifier [SPEC R3], e.g. `Europe/London`. */
public object ZoneIdSerializer : KSerializer<ZoneId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("dev.housepoints.ZoneId", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ZoneId): Unit = encoder.encodeString(value.id)
    override fun deserialize(decoder: Decoder): ZoneId = ZoneId.of(decoder.decodeString())
}

/** `MONDAY` … `SUNDAY`. */
public object DayOfWeekSerializer : KSerializer<DayOfWeek> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("dev.housepoints.DayOfWeek", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: DayOfWeek): Unit = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): DayOfWeek = DayOfWeek.valueOf(decoder.decodeString())
}
