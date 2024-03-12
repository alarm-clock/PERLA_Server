package com.jmb_bms_server.MessagesJsons

import com.jmb_bms_server.Location
import kotlinx.serialization.Contextual
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class UserProfileMessage @OptIn(ExperimentalSerializationApi::class) constructor(
    @EncodeDefault val opCode: Int = 2,
    @Contextual val _id: ObjectId?,
    val userName: String,
    val symbolCode: String,
    val location: Location?,
    val teamEntry: HashSet<@Contextual ObjectId>
)
