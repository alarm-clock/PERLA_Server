package com.jmb_bms_server.MessagesJsons

import kotlinx.serialization.Contextual
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class SuccessfullConnecion @OptIn(ExperimentalSerializationApi::class) constructor(
    @EncodeDefault val opCode: Int = 1,
    val _id: String,
    val teamEntry: HashSet<@Contextual ObjectId>
)