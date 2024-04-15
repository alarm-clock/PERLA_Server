package com.jmb_bms_server.data.user

import com.jmb_bms_server.customSerializers.AtomicReferenceSerializer
import com.jmb_bms_server.data.location.Location
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import java.util.concurrent.atomic.AtomicReference


@Serializable
data class UserProfile(
    @Serializable(with = AtomicReferenceSerializer::class)
    var _id: AtomicReference< @Contextual ObjectId?> = AtomicReference(null),
    @Serializable(with = AtomicReferenceSerializer::class)
    var userName: AtomicReference<String> = AtomicReference(""),
    @Serializable(with = AtomicReferenceSerializer::class)
    var symbolCode: AtomicReference<String> = AtomicReference(""),
    @Serializable(with = AtomicReferenceSerializer::class)
    var location: AtomicReference<Location?> = AtomicReference(null),
    @Serializable(with = AtomicReferenceSerializer::class)
    var connected: AtomicReference<Boolean> = AtomicReference(false),
    @Serializable(with = AtomicReferenceSerializer::class)
    var teamEntry: AtomicReference<HashSet<@Contextual ObjectId>> = AtomicReference(HashSet()),

){

    fun getStorableUserProfile() = StorableUserProfile(null,userName.get(),symbolCode.get(), location.get()?.getStorableLocation(),connected.get(),teamEntry.get())

    fun updateValuesFromStorableUserProfile(profile: StorableUserProfile): UserProfile
    {
        _id.set(profile._id)
        userName.set(profile.userName)
        symbolCode.set(profile.symbolCode)
        location.set(profile.location?.getLocation())
        connected.set(profile.connected)
        teamEntry.set(profile.teamEntry)
        return this
    }

}




/*
data class UserProfile(@SerialName("_id") @BsonId var _id: ObjectId? = null,
                       var userName: AtomicReference<String>,
                       var symbolCode: AtomicReference<String>,
                       var location: AtomicReference<Location?>,
                       var connected: AtomicReference<Boolean>)

 */