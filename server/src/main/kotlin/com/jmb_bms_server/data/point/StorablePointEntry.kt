package com.jmb_bms_server.data.point

import com.jmb_bms_server.data.location.StorableLocation
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId


@Serializable
data class StorablePointEntry(
    @SerialName("_id") @BsonId var _id: String? = null,
    var name: String,
    var ownerId: String,
    var location: StorableLocation,
    var description: String,
    var symbol: String,
    var files: List<String>,
    var menuString: String,
    var ownerName: String
)
