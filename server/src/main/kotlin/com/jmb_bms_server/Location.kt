package com.jmb_bms_server

import com.jmb_bms_server.customSerializers.AtomicReferenceSerializer
import com.jmb_bms_server.data.StorableLocation
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicReference

/*
@Serializable
data class Location(@Transient @BsonIgnore private var _lat: Double = 0.0, @Transient @BsonIgnore private var _long: Double = 0.0)
{

    @Transient @BsonIgnore private val latMut: Mutex = Mutex()
    @Transient @BsonIgnore private val longMut: Mutex = Mutex()

    @SerialName("lat")
    @get:BsonProperty("lat")
    @set:BsonProperty("lat")
    var lat: Double
        set(value) {
            runBlocking {
                latMut.lock()
                _lat = value
                latMut.unlock()
            }
        }
        get() {
            val ret: Double
            runBlocking {
                latMut.lock()
                ret = lat
                latMut.unlock()
            }
            return ret
        }

    @SerialName("long")
    @get:BsonProperty("long")
    @set:BsonProperty("long")
    var long: Double
        set(value) {
            runBlocking {
                longMut.lock()
                _long = value
                longMut.unlock()
            }
        }
        get() {
            val ret: Double
            runBlocking {
                longMut.lock()
                ret = long
                longMut.unlock()
            }
            return ret
        }
}
*/

@Serializable
data class Location(
    @Serializable(with = AtomicReferenceSerializer::class)
    val lat: AtomicReference<Double>,
    @Serializable(with = AtomicReferenceSerializer::class)
    val long: AtomicReference<Double>
){
    fun getStorableLocation() = StorableLocation(lat.get(),long.get())
}