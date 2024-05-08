/**
 * @file: StorableTeamEntry.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing StorableTeamEntry class
 */
package com.jmb_bms_server.data.team

import com.jmb_bms_server.data.location.Location
import com.jmb_bms_server.data.location.StorableLocation
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

/**
 * Class representing team
 *
 * @property _id Teams ID
 * @property teamName Teams name
 * @property teamIcon Symbol code string
 * @property teamLocation Teams [Location] on map if team is sharing location
 * @property teamLead UserId of team leader
 * @property teamEntry IDs of teams in which this team is (Currently not supported)
 */
@Serializable
data class StorableTeamEntry(
    @SerialName("_id") @Contextual @BsonId var _id: ObjectId? = null,
    var teamName: String,
    var teamIcon: String,
    var teamLocation: StorableLocation?,
    @Contextual var teamLead: ObjectId,
    var teamEntry: HashSet<@Contextual ObjectId>
)