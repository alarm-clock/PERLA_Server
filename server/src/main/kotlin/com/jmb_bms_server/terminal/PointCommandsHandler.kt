package com.jmb_bms_server.terminal

import com.jmb_bms_server.MessagesJsons.Messages
import com.jmb_bms_server.TmpServerModel
import com.jmb_bms_server.data.point.PointEntry
import com.jmb_bms_server.utils.GetJarPath
import com.jmb_bms_server.utils.MissingParameter
import com.jmb_bms_server.utils.checkIfAllFilesAreUploaded
import com.mongodb.client.model.Updates
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import java.io.File
import java.nio.file.Files

class PointCommandsHandler(private val model: TmpServerModel) {


    fun printPoint(pointEntry: PointEntry)
    {
        println("+-----------------------------------------+\n" +
                "id: ${pointEntry._id.get().toString()}\n" +
                "name: ${pointEntry.name.get()}\n" +
                "description: ${pointEntry.description.get()}\n" +
                "symbol: ${pointEntry.symbol.get()}\n" +
                "ownerId: ${pointEntry.ownerId.get()}\n" +
                "attached files:\n")
        pointEntry.files.forEach {
            println("     ${GetJarPath.currentWorkingDirectory}/files/$it")
        }
        println("+-----------------------------------------+")
    }

    fun printAllPoints()
    {
        model.pointSet.forEach {
            printPoint(it)
        }
    }

    fun printPoint(name: String)
    {
        val points = model.pointSet.filter { it.name.get() == name }

        points.forEach {
            printPoint(it)
        }
    }

    private fun deleteFilesOnUpdate(oldFiles: List<String>, newFiles: List<String>?)
    {
        if(newFiles.isNullOrEmpty())
        {
            oldFiles.forEach {
                File("${GetJarPath.currentWorkingDirectory}/files/$it").delete()
            }
            return
        }

        val filesForDelete = oldFiles.filterNot { it in newFiles }
        filesForDelete.forEach {
            File("${GetJarPath.currentWorkingDirectory}/files/$it").delete()
        }
    }



    @Throws(MissingParameter::class)
    suspend fun addPoint(params: Map<String, Any?>, userId: String, ownerName: String): String?
    {
        val id = params["serverId"] as? String ?: return "NoId"
        val name = params["name"] as? String ?: throw MissingParameter("NoName")
        val descr = params["descr"] as? String
        val symbol = params["symbol"] as? String ?: throw MissingParameter("NoSymbol")
        val menuString = params["menuStr"] as? String ?: throw MissingParameter("NoMenuString")
        var files = params["files"] as? List<String>

        files = files?.map { "$userId-$it" }

        val owner = params["owner"] as? String
        val lat = params["lat"] as? Double
        val long = params["long"]  as? Double


        return addPoint(
            PointEntry().apply {
                this._id.set(id)
                this.name.set(name)
                this.description.set(descr)
                this.symbol.set(symbol)
                this.menusString.set(menuString)
                files?.let {
                    this.files.addAll(it)
                }
                this.ownerId.set(owner ?: userId)
                this.location.get().lat.set(lat)
                this.location.get().long.set(long)
                this.ownerName.set(if(owner == "All") "All" else ownerName)
            })
    }

    private suspend fun addPoint(newPointEntry: PointEntry): String?
    {
        if( !checkIfAllFilesAreUploaded(newPointEntry.files) )
        {
            println("Not all files where uploaded")
            return null
        }

        return model.addPoint(newPointEntry)
    }

    fun updatePoint(list: List<String>?)
    {
        runBlocking {
            if(list == null)
            {
                println("Wrong number of parameters")
                return@runBlocking
            }
            if( list.size % 2 == 1)
            {
                println("Wrong number of parameters")
                return@runBlocking
            }
            val params = mutableMapOf<String, String>()
            params["serverId"] = list[1]

            for(cnt in 2 until list.size step 2)
            {
                val key = list[cnt]
                val value = list[cnt + 1]
                params[key] = value
            }
            updatePoint(params,"admin")
        }
    }


    suspend fun updatePoint(params: Map<String, Any?>, userId: String) : Boolean
    {
        val id = params["serverId"] as? String ?: throw MissingParameter("NoId")

        val entry = model.pointSet.find { it._id.get().toString() == id } ?: return false

        if(entry.ownerId.get() != "All" && entry.ownerId.get() != userId && userId != "admin") return false

        val name = params["name"] as? String
        val descr = params["descr"] as? String
        val symbol = params["symbol"] as? String
        val menuString = params["menuStr"] as? String
        val files = params["files"] as? List<String>
        val owner = params["owner"] as? String
        val lat = params["lat"]  as? Double
        val long = params["long"] as? Double

        var locBson: Bson? = null

        if(lat != null && long != null)
        {
            entry.location.get().lat.set(lat)
            entry.location.get().long.set(long)
            locBson = Updates.set(PointEntry::location.name,entry.location.get().getStorableLocation())
        }


        val updates = Updates.combine(
            name?.let {
                entry.name.set(it)
                Updates.set(PointEntry::name.name,it)
                      },
            descr?.let {
                entry.description.set(it)
                Updates.set(PointEntry::description.name,it)
                       },
            symbol?.let {
                entry.symbol.set(it)
                Updates.set(PointEntry::symbol.name,it)
                        },
            menuString?.let {
                entry.menusString.set(it)
                Updates.set(PointEntry::menusString.name,it)
                            },
            (files?.let {
                val mappedIt = it.map { name -> "$userId-$name" }
                println(mappedIt)
                if( !checkIfAllFilesAreUploaded(mappedIt) ) return false
                deleteFilesOnUpdate(entry.files,mappedIt)
                entry.files.clear()
                entry.files.addAll(mappedIt)
                Updates.set(PointEntry::files.name,mappedIt)
            } ?: {
                deleteFilesOnUpdate(entry.files,null)
                entry.files.clear()
                Updates.set(PointEntry::files.name, listOf<String>())
            }) as Bson?,
            owner?.let {
                entry.ownerId.set(it)
                val ownerName = if(owner == "All") "All" else model.userSet.find { user -> user._id.get().toString() == entry._id.get().toString() }?.userName?.get()
                entry.ownerName.set(ownerName ?: "No name")
                Updates.set(PointEntry::ownerId.name,it)
            },
            locBson
        )

        model.updatePoint(id, updates)
        return true
    }

    suspend fun deletePoint(params: Map<String, Any?>, userId: String): Boolean
    {
        val id = params["serverId"] as? String ?: throw MissingParameter("NoId")
        return deletePoint(id,userId)
    }

    suspend fun deletePoint(id: String, userId: String): Boolean
    {

        val point = model.pointSet.find { it._id.get().toString() == id } ?: return false

        if( point.ownerId.get() != "all" && point.ownerId.get() != userId && userId != "admin") return false

        point.files.forEach {
            File("${GetJarPath.currentWorkingDirectory}/files/$it").delete()
        }
        model.pointSet.remove(point)
        model.removePoint(id)

        model.userSessionsSet.forEach {
            it.session.get()?.send(Frame.Text(Messages.pointDeltion(id)))
        }

        return true
    }

    suspend fun syncWithClient(params: Map<String, Any?>, userId: String)
    {
        val clientsPoints = params["ids"] as? List<String> ?: return
        val serverPointsOwnedByClient = model.pointSet.filter { it.ownerId.get().toString() == userId }

        val pointsForDeletion = serverPointsOwnedByClient.filterNot { it._id.get().toString() in clientsPoints }

        pointsForDeletion.forEach {
            deletePoint(it._id.get().toString(), userId)
        }
    }
}