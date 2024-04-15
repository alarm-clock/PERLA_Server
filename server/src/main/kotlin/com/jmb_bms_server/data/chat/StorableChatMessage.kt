package com.jmb_bms_server.data.chat

import com.jmb_bms_server.MessagesJsons.ChatMessage
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

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
    fun getChatMessage() = ChatMessage(_id!!, chatId,userName,userSymbol,text, files, points)
}
