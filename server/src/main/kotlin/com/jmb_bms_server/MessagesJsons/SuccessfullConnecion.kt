/**
 * @file: SuccessfullConnecion.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing SuccessfullConnecion class
 */
package com.jmb_bms_server.MessagesJsons

import kotlinx.serialization.Contextual
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

/**
 * Successful connection message
 *
 * @property opCode Message opCode. Don't change unless it is used in another message!!
 * @property _id Users ID
 * @property teamEntry [List] of teamIds in whose member is user
 */
@Serializable
data class SuccessfullConnecion @OptIn(ExperimentalSerializationApi::class) constructor(
    @EncodeDefault val opCode: Int = 1,
    val _id: String,
    val teamEntry: HashSet<@Contextual ObjectId>
)