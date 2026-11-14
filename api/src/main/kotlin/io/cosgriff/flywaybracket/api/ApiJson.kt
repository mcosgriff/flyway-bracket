package io.cosgriff.flywaybracket.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

/**
 * The one JSON format of every message, used by the app and the server alike. A key the reader does
 * not know is skipped, so a newer server can add a field without breaking an older app.
 */
val ApiJson: Json = Json { ignoreUnknownKeys = true }

/** A date with no time and no zone, as ISO-8601 text: `2027-03-01`. */
typealias ApiDate =
    @Serializable(with = LocalDateSerializer::class)
    LocalDate

/** A moment, as ISO-8601 text in UTC: `2027-03-01T07:00:00Z`. */
typealias ApiInstant =
    @Serializable(with = InstantSerializer::class)
    Instant

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.cosgriff.flywaybracket.api.LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

/** Written in UTC (`…Z`), whatever zone the caller thinks in. */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.cosgriff.flywaybracket.api.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
