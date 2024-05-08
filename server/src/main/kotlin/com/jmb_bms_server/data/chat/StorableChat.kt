/**
 * @file: StorableChat.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing StorableChat class
 */
package com.jmb_bms_server.data.chat

import com.jmb_bms_server.MessagesJsons.ChatCreationMessage
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

/**
 * Class representing [Serializable] collection entry for chat rooms
 *
 * @property _id ID of chat room
 * @property name Chat rooms name
 * @property ownerId ID of user who owns the chat room
 * @property members List of users that are in chat room
 * @constructor Create empty Storable chat
 */
@Serializable
data class StorableChat(
    @Contextual @BsonId var _id: ObjectId? = null,
    var name: String,
    var ownerId: String,
    var members: MutableList<String> //id
){
    /**
     * Method that creates [ChatCreationMessage] instance initialized by values from [StorableChat] instance
     *
     * @return Initialized [ChatCreationMessage] instance
     */
    fun getCreationMessage() = ChatCreationMessage(_id.toString(),name,ownerId, members)
}