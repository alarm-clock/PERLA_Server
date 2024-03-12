package com.jmb_bms_server.MessagesJsons

import kotlinx.serialization.Contextual
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class LocationUpdateMessage @OptIn(ExperimentalSerializationApi::class) constructor(
    @EncodeDefault val opCode: Int = 3,
    @Contextual val _id: ObjectId?,
    val lat: Double,
    val long: Double
)