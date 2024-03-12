package com.jmb_bms_server.customSerializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import java.util.concurrent.atomic.AtomicReference

@Serializer(forClass = AtomicReference::class)
class AtomicReferenceSerializer<T>(private val valueSerializer: KSerializer<T>) : KSerializer<AtomicReference<T>> {
    override val descriptor = valueSerializer.descriptor

    override fun serialize(encoder: Encoder, value: AtomicReference<T>) {
        valueSerializer.serialize(encoder, value.get())
    }

    override fun deserialize(decoder: Decoder): AtomicReference<T> {
        val value = valueSerializer.deserialize(decoder)
        return AtomicReference(value)
    }
}
inline fun <reified T> atomicReferenceSerializer(): KSerializer<AtomicReference<T>> {
    return AtomicReferenceSerializer(serializer())
}