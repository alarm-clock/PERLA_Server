/**
 * @file: ChatCreationMessage.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing ChatCreationMessage class
 */
package com.jmb_bms_server.MessagesJsons

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Chat creation message that can be serialized into JSON string and sent to client.
 *
 * @property _id Chat room id
 * @property name Chat room name
 * @property ownerId UserId of user that owns chat room
 * @property members [MutableList] of UserIds that are members of this chat room
 * @property opCode Message opCode. Don't change unless it is used in another message!!
 */
@Serializable
data class ChatCreationMessage @OptIn(ExperimentalSerializationApi::class) constructor(
    var _id: String,
    var name: String,
    var ownerId: String,
    var members: MutableList<String>, //id
    @EncodeDefault val opCode: Long = 60
)