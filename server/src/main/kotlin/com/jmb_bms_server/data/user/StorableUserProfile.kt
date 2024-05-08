/**
 * @file: StorableUserProfile.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing StorableUserProfile class
 */
package com.jmb_bms_server.data.user

import com.jmb_bms_server.data.location.Location
import com.jmb_bms_server.data.location.StorableLocation
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

/**
 * User profile that can be stored in mongo collection. Not suitable for multithreaded access.
 *
 * @property _id Users ID
 * @property userName Users name showed on screen
 * @property symbolCode Symbol code string of symbol showed on map.
 * @property location Users [Location]
 * @property connected Flag indicating if user is connected to server
 * @property teamEntry [HashSet] with IDs of all teams whose member user is
 */
@Serializable
data class StorableUserProfile(@SerialName("_id") @Contextual @BsonId var _id: ObjectId? = null,
                               var userName: String,
                               var symbolCode: String,
                               var location: StorableLocation?,
                               var connected: Boolean,
                               var teamEntry: HashSet<@Contextual ObjectId>,
)
