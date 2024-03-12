package com.jmb_bms_server.data

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

@Serializable
data class StorableTeamEntry(
    @SerialName("_id") @Contextual @BsonId var _id: ObjectId? = null,
    var teamName: String,
    var teamIcon: String,
    var teamLocation: StorableLocation?,
    @Contextual var teamLead: ObjectId,
    var teamEntry: HashSet<@Contextual ObjectId>
)