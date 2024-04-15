package com.jmb_bms_server.data.chat

import com.jmb_bms_server.MessagesJsons.ChatCreationMessage
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId


@Serializable
data class StorableChat(
    @Contextual @BsonId var _id: ObjectId? = null,
    var name: String,
    var ownerId: String,
    var members: MutableList<String> //id
){
    fun getCreationMessage() = ChatCreationMessage(_id.toString(),name,ownerId, members)
}