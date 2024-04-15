package com.jmb_bms_server.MessagesJsons

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class ChatCreationMessage @OptIn(ExperimentalSerializationApi::class) constructor(
    var _id: String,
    var name: String,
    var ownerId: String,
    var members: MutableList<String>, //id
    @EncodeDefault val opCode: Long = 60
)