package com.jmb_bms_server.data.counter

import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId

@Serializable
data class CntRow(
    @BsonId val _id: String,
    val seq: Long
)