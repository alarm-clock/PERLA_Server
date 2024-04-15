package com.jmb_bms_server.data.user

import com.jmb_bms_server.data.location.StorableLocation
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

@Serializable
data class StorableUserProfile(@SerialName("_id") @Contextual @BsonId var _id: ObjectId? = null,
                               var userName: String,
                               var symbolCode: String,
                               var location: StorableLocation?,
                               var connected: Boolean,
                               var teamEntry: HashSet<@Contextual ObjectId>,
)
