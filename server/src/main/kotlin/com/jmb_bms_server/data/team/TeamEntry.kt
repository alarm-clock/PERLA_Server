/**
 * @file: TeamEntry.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing TeamEntry class
 */
package com.jmb_bms_server.data.team

import com.jmb_bms_server.customSerializers.AtomicReferenceSerializer
import com.jmb_bms_server.data.location.Location
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import java.util.concurrent.atomic.AtomicReference

/**
 * Team profile
 *
 * @property _id Teams ID
 * @property teamName Teams name
 * @property teamIcon Symbol code string
 * @property teamLocation Teams [Location] on map if team is sharing location
 * @property teamLead UserId of team leader
 * @property teamEntry IDs of teams in which this team is (Currently not supported)
 */
@Serializable
data class TeamEntry(
    @Serializable(with = AtomicReferenceSerializer::class)
    var _id: AtomicReference<@Contextual ObjectId?> = AtomicReference(null),

    @Serializable(with = AtomicReferenceSerializer::class)
    var teamName: AtomicReference<String> = AtomicReference(""),

    @Serializable(with = AtomicReferenceSerializer::class)
    var teamIcon: AtomicReference<String> = AtomicReference(""),

    @Serializable(with = AtomicReferenceSerializer::class)
    var teamLocation: AtomicReference<Location?> = AtomicReference(null),

    @Serializable(with = AtomicReferenceSerializer::class)
    var teamLead: AtomicReference<@Contextual ObjectId> = AtomicReference(ObjectId()),

    @Serializable(with = AtomicReferenceSerializer::class)
    var teamEntry: AtomicReference<HashSet<@Contextual ObjectId>> = AtomicReference(HashSet()),
){

    /**
     * Method that creates new [StorableTeamEntry] instance initialized with values from current [TeamEntry]. [StorableTeamEntry]
     * can be stored in mongo collection
     *
     * @return Initialized [StorableTeamEntry]
     */
    fun getStorableTeamEntry() = StorableTeamEntry(_id.get(),teamName.get(),teamIcon.get(),teamLocation.get()?.getStorableLocation(),teamLead.get(),teamEntry.get())

    /**
     * Method that updates current [TeamEntry] instance with values from [storableTeamEntry]
     *
     * @param storableTeamEntry Used to update current [TeamEntry]
     * @return Reference to updated [TeamEntry]
     */
    fun updateValuesFromStorableTeamEntry(storableTeamEntry: StorableTeamEntry): TeamEntry
    {
        _id.set(storableTeamEntry._id)
        teamName.set(storableTeamEntry.teamName)
        teamIcon.set(storableTeamEntry.teamIcon)
        teamLocation.set(storableTeamEntry.teamLocation?.getLocation())
        teamLead.set(storableTeamEntry.teamLead)
        teamEntry.set(storableTeamEntry.teamEntry)
        return this
    }
}