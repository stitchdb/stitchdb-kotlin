package com.stitchdb.serializer

import com.stitchdb.annotations.StitchInternal
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

@StitchInternal
object UnixTimestampSerializer: KSerializer<Instant> {

    override val descriptor: SerialDescriptor = Instant.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeLong(value.epochSeconds)
    }

    override fun deserialize(decoder: Decoder): Instant {
        return Instant.fromEpochSeconds(decoder.decodeLong())
    }

}