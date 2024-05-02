package com.jmb_bms_server.customSerializers

import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.CopyOnWriteArrayList

@Serializer(forClass = CopyOnWriteArrayList::class)
class CopyOnWriteArrSerializer<T>(private val valueSerializer: KSerializer<List<T>>
) : KSerializer<CopyOnWriteArrayList<T>>
{

    override val descriptor = valueSerializer.descriptor
    override fun serialize(encoder: Encoder, value: CopyOnWriteArrayList<T>) {

        valueSerializer.serialize(encoder,value.toList())
    }

    override fun deserialize(decoder: Decoder): CopyOnWriteArrayList<T> {
        val res = valueSerializer.deserialize(decoder)
        return CopyOnWriteArrayList(res)
    }
}