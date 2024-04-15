package com.jmb_bms_server.MessagesJsons

import com.jmb_bms_server.data.location.Location
import com.jmb_bms_server.data.location.StorableLocation
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class PointEntryMessage @OptIn(ExperimentalSerializationApi::class) constructor(

    @EncodeDefault val opCode: Int = 40,
    val serverId: String,
    val name: String,
    val description: String = "",
    val symbol: String,
    val menuString: String,
    val ownerId: String,
    val ownerName: String,
    val files: List<String>,
    val location: StorableLocation
)
