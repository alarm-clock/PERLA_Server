/**
 * @file: LocationUpdateMessage.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing LocationUpdateMessage class
 */
package com.jmb_bms_server.MessagesJsons

import kotlinx.serialization.Contextual
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

/**
 * Location update message
 *
 * @property opCode Message opCode. Don't change unless it is used in another message!!
 * @property _id ID of user whose location was updated
 * @property lat New latitude
 * @property long New longitude
 */
@Serializable
data class LocationUpdateMessage @OptIn(ExperimentalSerializationApi::class) constructor(
    @EncodeDefault val opCode: Int = 3,
    @Contextual val _id: ObjectId?,
    val lat: Double,
    val long: Double
)