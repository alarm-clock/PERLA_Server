package com.jmb_bms_server.MessagesJsons

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage @OptIn(ExperimentalSerializationApi::class) constructor(
    val _id: Long,
    val chatId: String,
    val userName: String,
    val userSymbol: String,
    val text: String,
    val files: List<String>,
    val points: List<String>,
    @EncodeDefault val opCode: Long = 64
)
