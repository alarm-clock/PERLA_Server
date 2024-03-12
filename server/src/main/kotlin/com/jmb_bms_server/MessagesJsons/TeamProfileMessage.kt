package com.jmb_bms_server.MessagesJsons


import com.jmb_bms_server.Location
import kotlinx.serialization.Contextual
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class TeamProfileMessage @OptIn(ExperimentalSerializationApi::class) constructor(
    @EncodeDefault val opCode: Int = 20,
    @Contextual val _id: ObjectId,
    val teamName: String,
    val teamIcon: String,
    val teamLocation: Location?,
    @Contextual val teamLead: ObjectId,
    val teamEntry: HashSet<@Contextual ObjectId>
    )