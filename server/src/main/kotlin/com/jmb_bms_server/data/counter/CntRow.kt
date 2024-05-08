/**
 * @file: CntRow.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing CntRow class
 */
package com.jmb_bms_server.data.counter

import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId

/**
 * Class representing [Serializable] collection row for auto incrementing, atomic database counter
 *
 * @property _id ID of given counter
 * @property seq Counters current value
 * @constructor Create [CntRow] instance
 */
@Serializable
data class CntRow(
    @BsonId val _id: String,
    val seq: Long
)