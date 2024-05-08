/**
 * @file: PointEntryMessage.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing PointEntryMessage class
 */
package com.jmb_bms_server.MessagesJsons

import com.jmb_bms_server.data.location.StorableLocation
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Point entry message
 *
 * @property opCode Message opCode. Don't change unless it is used in another message!!
 * @property serverId Points ServerID
 * @property name Point name
 * @property description Point description
 * @property symbol Symbol code of points symbol
 * @property menuString Formatted string used to initialize symbol creation menu in client
 * @property ownerId UserId of user that owns this point or ALL if all users can update this point
 * @property ownerName Owners name at time he created this point
 * @property files [List] of file names attached to this point
 * @property location Points [StorableLocation]
 */
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
