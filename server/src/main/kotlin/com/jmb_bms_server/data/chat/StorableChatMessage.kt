/**
 * @file: StorableChatMessage.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing StorableChatMessage class
 */
package com.jmb_bms_server.data.chat

import com.jmb_bms_server.MessagesJsons.ChatMessage
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

/**
 * Class representing [Serializable] collection entry for chat message
 *
 * @property _id ID of message unique to for given [chatId]. Also, messages are ordered by this property
 * @property chatId ID of chat room in which this message was sent
 * @property userName Username of user who sent this message
 * @property userSymbol Symbol code that user had when he sent this message
 * @property text Message text
 * @property files [List] of attached file names
 * @property points [List] of attached points
 * @constructor Create Storable chat message
 */
@Serializable
data class StorableChatMessage(
    @BsonId var _id: Long? = null,
    val chatId: String,
    val userName: String,
    val userSymbol: String,
    val text: String,
    val files: List<String>,
    val points: List<String>
){
    /**
     * Method that creates [ChatMessage] instance initialized by values from [StorableChatMessage]
     *
     * @return Initialized [ChatMessage] instance
     */
    fun getChatMessage() = ChatMessage(_id!!, chatId,userName,userSymbol,text, files, points)
}
