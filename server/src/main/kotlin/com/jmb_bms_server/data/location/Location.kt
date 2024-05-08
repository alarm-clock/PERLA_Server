/**
 * @file: Location.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing Location class
 */
package com.jmb_bms_server.data.location

import com.jmb_bms_server.customSerializers.AtomicReferenceSerializer
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicReference

/**
 * Class representing location on map and implements [Serializable] interface.
 *
 * @property lat Latitude
 * @property long Longitude
 */
@Serializable
data class Location(
    @Serializable(with = AtomicReferenceSerializer::class)
    val lat: AtomicReference<Double>,
    @Serializable(with = AtomicReferenceSerializer::class)
    val long: AtomicReference<Double>
){
    /**
     * Method that creates [StorableLocation] instance that can be stored in mongo collection with data initialized
     * from [Location] instance.
     *
     * @return Initialized [StorableLocation] instance
     */
    fun getStorableLocation() = StorableLocation(lat.get(),long.get())
}