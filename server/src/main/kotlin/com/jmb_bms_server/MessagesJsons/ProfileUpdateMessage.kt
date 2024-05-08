/**
 * @file: ProfileUpdateMessage.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing ProfileUpdateMessage class
 */
package com.jmb_bms_server.MessagesJsons

import kotlinx.serialization.Contextual
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

/**
 * Profile update message
 *
 * @property opCode Message opCode. Don't change unless it is used in another message!!
 * @property _id Profile's ID
 * @property userName New username
 * @property symbolCode new symbol code
 */
@Serializable
data class ProfileUpdateMessage @OptIn(ExperimentalSerializationApi::class) constructor(
    @EncodeDefault val opCode: Int = 8,
    @Contextual val _id: ObjectId,
    val userName: String,
    val symbolCode: String
)
