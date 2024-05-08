/**
 * @file: CopyOnWriteArrSerializer.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing CopyOnWriteArrSerializer class
 */
package com.jmb_bms_server.customSerializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Class for serializing [CopyOnWriteArrayList] class
 *
 * @param T Data type of values stored in [CopyOnWriteArrayList]
 * @property valueSerializer Serializer
 * @constructor Create empty Copy on write arr serializer
 */
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