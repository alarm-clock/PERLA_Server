/**
 * @file: TeamProfileMessage.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing TeamProfileMessage class
 */
package com.jmb_bms_server.MessagesJsons

import com.jmb_bms_server.data.location.Location
import kotlinx.serialization.Contextual
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

/**
 * Team profile message
 *
 * @property opCode Message opCode. Don't change unless it is used in another message!!
 * @property _id TeamID
 * @property teamName Team name
 * @property teamIcon Team icon
 * @property teamLocation Teams [Location]
 * @property teamLead UserID of team leader
 * @property teamEntry [List] of teamIDs whose member is team (currently not used)
 */
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