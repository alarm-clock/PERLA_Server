/**
 * @file: StorableLocation.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing StorableLocation class
 */
package com.jmb_bms_server.data.location

import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicReference

/**
 * Class representing location on the map that can be stored in mongo collection but can not be used in multithreaded
 * access.
 *
 * @property lat Latitude
 * @property long Longitude
 */
@Serializable
data class StorableLocation(val lat: Double, val long: Double)
{
    /**
     * Method that creates [Location] instance with values initialized from [StorableLocation] instance. [Location]
     * instance can be used for multithreaded access.
     *
     */
    fun getLocation() = Location(AtomicReference(lat),AtomicReference(long))
}
