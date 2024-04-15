package com.jmb_bms_server.data.location

import com.jmb_bms_server.customSerializers.AtomicReferenceSerializer
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicReference
@Serializable
data class Location(
    @Serializable(with = AtomicReferenceSerializer::class)
    val lat: AtomicReference<Double>,
    @Serializable(with = AtomicReferenceSerializer::class)
    val long: AtomicReference<Double>
){
    fun getStorableLocation() = StorableLocation(lat.get(),long.get())
}