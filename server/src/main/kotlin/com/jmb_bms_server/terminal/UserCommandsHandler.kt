package com.jmb_bms_server.terminal

import com.jmb_bms_server.data.location.Location
import com.jmb_bms_server.MessagesJsons.Messages
import com.jmb_bms_server.TmpServerModel
import com.jmb_bms_server.data.user.UserProfile
import com.jmb_bms_server.data.user.UserSession
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import java.lang.NumberFormatException
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.random.Random

class UserCommandsHandler(private val model: TmpServerModel, private val server: NettyApplicationEngine) {

    private fun printUser(userProfile: UserProfile, onlyThing: Boolean = true)
    {
        var entries = ""

        userProfile.teamEntry.get().forEachIndexed { index, objectId ->
            val team = model.teamsSet.find { it._id.get() == objectId }
            entries += "    ID: $objectId - Team Name: ${team?.teamName?.get()}\n"
        }

        println("+---------------------------------------------------+\n" +
                "Username: ${userProfile.userName}\nServer Id: ${userProfile._id}\n" +
                "Symbol: ${userProfile.symbolCode}\n" +
                "Current Location: ${if(userProfile.location.get() != null) "Latitude: ${userProfile.location.get()?.lat?.get()} Longitude: ${userProfile.location.get()?.long?.get()}\n" else "User is not sharing location\n"}" +
                "Is connected: ${userProfile.connected}\n" +
                "Teams:\n" + entries +
                if(onlyThing)"+---------------------------------------------------+" else "")
    }
    private fun printSesionStatus(userSession: UserSession, onlyThing: Boolean = true)
    {
        println( (if(onlyThing)"+---------------------------------------------------+\n" else "") +
                (if(userSession.session.get() == null) "Session: No session associated with this user\n" else userSession.session.get()?.isActive.toString() + "\n") +
                "+---------------------------------------------------+")
    }

    fun printAllUserInfo(params: List<String>?)
    {
        if(params == null)
        {
            println("Wrong number of parameters for printUser command. Expecting: 1")
            return
        }
        val profile = model.userSet.find { it.userName.get() == params[1] }
        if(profile == null)
        {
            println("No user with username \"${params[1]}\" exists nor has active connection")
            return
        }
        printUser(profile,false)
        val userSession = model.userSessionsSet.find { it.userId.get().toString() == profile._id.get().toString() }
        if(userSession == null)
        {
            println("No session entry for user ${params[1]}\n" +
                    "+---------------------------------------------------+")
            return
        }
        printSesionStatus(userSession,false)
    }
    fun printAllUserInfo(userProfile: UserProfile)
    {
        printUser(userProfile,false)
        val userSession = model.userSessionsSet.find { it.userId.get().toString() == userProfile._id.get().toString() }
        if(userSession == null)
        {
            println("No session entry for user ${userProfile.userName}\n" +
                    "+---------------------------------------------------+")
            return
        }
        printSesionStatus(userSession,false)
    }

    fun printAllUsers()
    {
        model.userSet.forEach{
            printAllUserInfo(it)
        }
    }

    fun createUser(params: List<String>?)
    {
        if(params == null)
        {
            println("Wrong number of parameters for createUser command. Expecting: 4 with location 2 without location")
            return
        }
        val lat: Double; val long: Double; val newProfile: UserProfile

        if(params.size == 5) {
            try {
                lat = params[3].toDouble()
                long = params[4].toDouble()
            } catch (_: NumberFormatException) {
                println("Lat or long parameter is not valid number. Got: ${params[3]} ${params[4]}")
                return
            }
            newProfile = UserProfile(
                userName = AtomicReference(params[1]),
                symbolCode = AtomicReference(params[2]),
                location = AtomicReference(Location(AtomicReference(lat), AtomicReference(long))),
                connected = AtomicReference(true)
            )
        } else
        {
            newProfile = UserProfile(
                userName = AtomicReference(params[1]),
                symbolCode = AtomicReference(params[2]),
                location = AtomicReference(null),
                connected = AtomicReference(true)
            )
        }
        val result = runBlocking { model.addNewUser(newProfile,null) }

        val p = model.userSet.find { it.userName.get() == params[1] } ?: return

        runBlocking {
            model.userSessionsSet.forEach {
                println(it)
                it.session.get()?.send(Frame.Text(Messages.sendUserProfile(p)))
            }
        }
        if(!result) println("Username already exists or mongo did not give id to user, try other username or debug server")
        else printUser(newProfile)
    }

    fun deleteUser(params: List<String>?)
    {
        if(params == null)
        {
            println("Wrong number of parameters for deleteUser command. Expecting: 1 }")
            return
        }
        val profile = model.userSet.find { it.userName.get() == params[1] }
        if(profile == null)
        {
            println("No user with username \"${params[1]}\" exists nor has active connection")
            return
        }
        runBlocking{model.removeUser(profile)}
        println("Deleted user ${params[1]}")

    }

    suspend fun editVal(key: String, value: String, profile: UserProfile){
        when(key)
        {
            "userName" -> {
                if( profile.userName.get() != value )
                {

                    if(model.userSet.find { it.userName.get() == value } == null)
                    {
                        profile.userName.set(value)
                        model.collection.findOneAndUpdate(
                            Filters.eq(UserProfile::_id.name,profile._id.get()),
                            Updates.set(UserProfile::userName.name,value)
                        )
                    }
                    else println("Username $value already exists")
                }
            }
            "symbolString" -> {
                profile.symbolCode.set(value)
                model.collection.findOneAndUpdate(
                    Filters.eq(UserProfile::_id.name,profile._id.get()),
                    Updates.set(UserProfile::symbolCode.name,value)
                )
            }
            "lat" -> {
                if(profile.location.get() == null) profile.location.set(
                    Location(
                        AtomicReference(value.toDouble()),
                        AtomicReference(0.0)
                    )
                )
                else profile.location.get()?.lat?.set(value.toDouble())
                model.collection.findOneAndUpdate(
                    Filters.eq(UserProfile::_id.name,profile._id.get()),
                    Updates.set(UserProfile::location.name,profile.location.get()?.getStorableLocation())
                )
            }
            "long" -> {
                if(profile.location.get() == null) profile.location.set(
                    Location(
                        AtomicReference(0.0),
                        AtomicReference(value.toDouble())
                    )
                )
                else profile.location.get()?.long?.set(value.toDouble())
                model.collection.findOneAndUpdate(
                    Filters.eq(UserProfile::_id.name,profile._id.get()),
                    Updates.set(UserProfile::location.name,profile.location.get()?.getStorableLocation())
                )
            }
            else -> println("Unknown key $key")
        }
    }

    fun updateUser(params: List<String>?)
    {
        if(params == null)
        {
            println("Too few parameters for updateUser command. Expecting at least 3 and even")
            return
        }
        if(params.size % 2 != 0)
        {
            println("Not even number of arguments for updateUser command")
            return
        }
        val profile = model.userSet.find { it.userName.get() == params[1] }
        if(profile == null)
        {
            println("No user with username \"${params[1]}\" exists nor has active connection")
            return
        }
        for(cnt in 2 until params.size step 2)
        {
            val key = params[cnt]
            val value = params[cnt+1]
           // println("$key $value")
            runBlocking {
                editVal(key,value, profile)
            }
        }
    }

    fun simTurnOffLocSh(params: List<String>?)
    {
        if( params == null)
        {
            println("Incorrect number of parameters for updateUser command. Expecting 1")
            return
        }
        val profile = model.userSet.find { it.userName.get() == params[1] }
        if(profile == null)
        {
            println("No user with username \"${params[1]}\" exists nor has active connection")
            return
        }
        profile.location.set(null)
        model.userSessionsSet.forEach {
            runBlocking {
                it.session.get()?.send(Frame.Text(Messages.sendLocationUpdate(profile)))
            }
        }
        println("Deleted user location and sent update to all connected users")
    }

    fun termConnection(params: List<String>?)
    {
        if( params == null)
        {
            println("Incorrect number of parameters for updateUser command. Expecting 1 ")
            return
        }
        val profile = model.userSet.find { it.userName.get() == params[1] }
        if(profile == null)
        {
            println("No user with username \"${params[1]}\" exists nor has active connection")
            return
        }
        val sessionEntry = model.userSessionsSet.find { it.userId.get() == profile._id.get() } ?: return

        thread {
            runBlocking {
               // println(sessionEntry.session.get())
                sessionEntry.session.get()?.send(Frame.Text(Messages.bye("Admin kicked you")))
                sessionEntry.session.get()?.close(CloseReason(CloseReason.Codes.NORMAL,"Admin kicked you"))

                model.userSessionsSet.forEach {
                    if(it.userId.get() != sessionEntry.userId.get())
                        it.session.get()?.send(Frame.Text(Messages.userDisconnected(sessionEntry.userId.get().toString())))
                }
            }
            sessionEntry.session.set(null)
            println("Terminated connection with user ${profile.userName}")
        }
    }

    fun simMoveRandom(params: List<String>?) {

        if (params == null)
        {
            println("Incorrect number of parameters for updateUser command. Expecting 1 ")
            return
        }
        val profile = model.userSet.find { it.userName.get() == params[1] }
        if(profile == null)
        {
            println("No user with username \"${params[1]}\" exists nor has active connection")
            return
        }
        //val sessionEntry = model.userSessionsSet.find { it.userId == profile._id } ?: return

        val iterations: Int; val period: Int
        try {
            iterations = params[2].toInt()
            period = params[3].toInt()
        } catch (_: NumberFormatException)
        {
            println("Iterations or period was not valid number")
            return
        }
       // println(iterations)
        thread {
            for(cnt in 0..iterations)
            {
                runBlocking {
                    delay(period.toLong())
                    val randLat = Random.nextDouble(-0.00005,0.00006)
                    val randLong = Random.nextDouble(-0.00005,0.00006)
                    if(profile.location.get() == null) profile.location.set( Location(
                        AtomicReference(49.26598),
                        AtomicReference(19.5689)
                    )
                    )

                    profile.location.get()?.lat?.updateAndGet{
                        it + randLat
                    }
                    profile.location.get()?.long?.updateAndGet {
                        it + randLong
                    }
                    //sessionEntry.session.get()?.send(Frame.Text(Messages.sendLocationUpdate(profile)))
                    model.userSessionsSet.forEach {
                        it.session.get()?.send(Frame.Text(Messages.sendLocationUpdate(profile)))
                    }
                    model.updateProfile(profile._id.get()!!, Updates.combine(
                        Updates.set(UserProfile::location.name,profile.location.get()?.getStorableLocation())
                    ))
                }
            }
        }
    }
}