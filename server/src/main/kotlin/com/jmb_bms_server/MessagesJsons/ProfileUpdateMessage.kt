package com.jmb_bms_server.MessagesJsons

import kotlinx.serialization.Contextual
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class ProfileUpdateMessage @OptIn(ExperimentalSerializationApi::class) constructor(
    @EncodeDefault val opCode: Int = 8,
    @Contextual val _id: ObjectId,
    val userName: String,
    val symbolCode: String
)
