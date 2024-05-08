/**
 * @file: PointEntry.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing PointEntry class
 */
package com.jmb_bms_server.data.point

import com.jmb_bms_server.customSerializers.AtomicReferenceSerializer
import com.jmb_bms_server.customSerializers.CopyOnWriteArrSerializer
import com.jmb_bms_server.data.location.Location
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Class that represents point on a map.
 *
 * @property _id ID of a point
 * @property name Points name
 * @property ownerId UserId of user that created and owns points or ALL if anyone can edit point.
 * @property ownerName Point owners name
 * @property location [Location] where is point located
 * @property description Points description
 * @property symbol Symbol code of symbol that is representing given point
 * @property files [CopyOnWriteArrayList] with attached file names
 * @property menusString Formatted string used in client to initialize symbol creation menu
 */
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

    /**
     * Method that creates new instance of [StorablePointEntry] initialized with values from [PointEntry] instance.
     * [StorablePointEntry] can be stored in mongo collection
     *
     * @return Initialized [StorablePointEntry] instance
     */
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

    /**
     * Update values of current [PointEntry] instance with values from [entry]
     *
     * @param entry [StorablePointEntry] that will update values of current [PointEntry] instance
     * @return Reference on updated [PointEntry] instance
     */
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