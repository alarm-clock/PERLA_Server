/**
 * @file: StorablePointEntry.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing StorablePointEntry class
 */
package com.jmb_bms_server.data.point

import com.jmb_bms_server.data.location.Location
import com.jmb_bms_server.data.location.StorableLocation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import java.util.concurrent.CopyOnWriteArrayList


/**
 * Class representing point on map that can be stored in mongo collection. It is not suitable for multithreaded
 * access.
 *
 * @property _id ID of a point
 * @property name Points name
 * @property ownerId UserId of user that created and owns points or ALL if anyone can edit point.
 * @property ownerName Point owners name
 * @property location [Location] where is point located
 * @property description Points description
 * @property symbol Symbol code of symbol that is representing given point
 * @property files [CopyOnWriteArrayList] with attached file names
 * @property menuString Formatted string used in client to initialize symbol creation menu
 */
@Serializable
data class StorablePointEntry(
    @SerialName("_id") @BsonId var _id: String? = null,
    var name: String,
    var ownerId: String,
    var location: StorableLocation,
    var description: String,
    var symbol: String,
    var files: List<String>,
    var menuString: String,
    var ownerName: String
)
