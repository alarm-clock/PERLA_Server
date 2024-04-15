package com.jmb_bms_server.data.team

import com.jmb_bms_server.customSerializers.AtomicReferenceSerializer
import com.jmb_bms_server.data.location.Location
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import java.util.concurrent.atomic.AtomicReference

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

    fun getStorableTeamEntry() = StorableTeamEntry(_id.get(),teamName.get(),teamIcon.get(),teamLocation.get()?.getStorableLocation(),teamLead.get(),teamEntry.get())

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