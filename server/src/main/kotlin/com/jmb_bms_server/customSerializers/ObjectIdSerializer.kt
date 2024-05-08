/**
 * @file: ObjectIdSerializer.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing ObjectIdSerializer object
 */
package com.jmb_bms_server.customSerializers

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.bson.types.ObjectId

/**
 * Object for serializing [ObjectId] class
 *
 * @constructor Create empty Object id serializer
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = ObjectId::class)
object ObjectIdSerializer : KSerializer<ObjectId> {
    override fun serialize(encoder: Encoder, value: ObjectId) {
        encoder.encodeString(value.toHexString())
    }

    override fun deserialize(decoder: Decoder): ObjectId {
        return ObjectId(decoder.decodeString())
    }
}