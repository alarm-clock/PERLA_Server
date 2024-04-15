package com.jmb_bms_server.data.location

import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicReference

@Serializable
data class StorableLocation(val lat: Double, val long: Double)
{
    fun getLocation() = Location(AtomicReference(lat),AtomicReference(long))
}
