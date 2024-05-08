/**
 * @file: UserProfileMessage.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing UserProfileMessage class
 */
package com.jmb_bms_server.MessagesJsons

import com.jmb_bms_server.data.location.Location
import kotlinx.serialization.Contextual
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

/**
 * User profile message
 *
 * @property opCode Message opCode. Don't change unless it is used in another message!!
 * @property _id UserID
 * @property userName Username
 * @property symbolCode Symbol code
 * @property location [Location]
 * @property teamEntry [List] of teamIDs in which user is member
 */
@Serializable
data class UserProfileMessage @OptIn(ExperimentalSerializationApi::class) constructor(
    @EncodeDefault val opCode: Int = 2,
    @Contextual val _id: ObjectId?,
    val userName: String,
    val symbolCode: String,
    val location: Location?,
    val teamEntry: HashSet<@Contextual ObjectId>
)
