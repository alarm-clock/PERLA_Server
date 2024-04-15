package com.jmb_bms_server.data.point

import com.jmb_bms_server.customSerializers.AtomicReferenceSerializer
import com.jmb_bms_server.customSerializers.CopyOnWriteArrSerializer
import com.jmb_bms_server.data.location.Location
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

@Serializable
class PointEntry(

    @Serializable(with = AtomicReferenceSerializer::class)
    val _id: AtomicReference<@Contextual String?> = AtomicReference(null),

    @Serializable(with = AtomicReferenceSerializer::class)
    val name: AtomicReference<String> = AtomicReference(""),

    @Serializable(with = AtomicReferenceSerializer::class)
    val ownerId: AtomicReference<String> = AtomicReference(""),

    @Serializable(with = AtomicReferenceSerializer::class)
    val ownerName: AtomicReference<String> = AtomicReference(""),

    @Serializable(with = AtomicReferenceSerializer::class)
    val location: AtomicReference<Location> = AtomicReference(Location(AtomicReference(0.0), AtomicReference(0.0))),

    @Serializable(with = AtomicReferenceSerializer::class)
    val description: AtomicReference<String> = AtomicReference(""),

    @Serializable(with = AtomicReferenceSerializer::class)
    val symbol: AtomicReference<String> = AtomicReference(""),

    @Serializable(with = CopyOnWriteArrSerializer::class)
    val files: CopyOnWriteArrayList<String> = CopyOnWriteArrayList(),

    @Serializable(with = AtomicReferenceSerializer::class)
    val menusString: AtomicReference<String> = AtomicReference("")
){

    fun getStorablePointEntry() = StorablePointEntry(
        _id.get(),
        name.get(),
        ownerId.get(),
        location.get().getStorableLocation(),
        description.get(),
        symbol.get(),
        files,
        menusString.get(),
        ownerName.get()
    )

    fun updateValuesFromStorablePointEntry(entry: StorablePointEntry): PointEntry
    {
        _id.set(entry._id)
        name.set(entry.name)
        ownerId.set(entry.ownerId)
        location.set(entry.location.getLocation())
        description.set(entry.description)
        symbol.set(entry.symbol)
        files.addAll(entry.files)
        menusString.set(entry.menuString)
        ownerName.set(entry.ownerName)
        return this
    }
}