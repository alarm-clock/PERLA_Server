/**
 * @file: UserProfile.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing UserProfile class
 */
package com.jmb_bms_server.data.user

import com.jmb_bms_server.customSerializers.AtomicReferenceSerializer
import com.jmb_bms_server.data.location.Location
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import java.util.concurrent.atomic.AtomicReference

/**
 * User profile
 *
 * @property _id Users ID
 * @property userName Users name showed on screen
 * @property symbolCode Symbol code string of symbol showed on map.
 * @property location Users [Location]
 * @property connected Flag indicating if user is connected to server
 * @property teamEntry [HashSet] with IDs of all teams whose member user is
 */
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

    /**
     * Method that initializes new [StorableUserProfile] instance with values from current [UserProfile]. Returned
     * [StorableUserProfile] can be stored in mongo collection.
     *
     * @return Initialized [StorableUserProfile] instance
     */
    fun getStorableUserProfile() = StorableUserProfile(null,userName.get(),symbolCode.get(), location.get()?.getStorableLocation(),connected.get(),teamEntry.get())

    /**
     * Method that updates current [UserProfile] with values from [profile]
     *
     * @param profile [StorableUserProfile] that will update current [UserProfile] instance
     * @return Reference on updated [UserProfile]. Use it like this when creating [UserProfile] instance from database entry:
     *
     * val user profile = UserProfile().updateValuesFromStorableUserProfile(profile)
     *
     * Use it like this when updating existing profile:
     *
     * existing profile.updateValuesFromStorableUserProfile(profile)
     *
     */
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