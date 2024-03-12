package com.jmb_bms_server.terminal

import com.jmb_bms_server.Location
import com.jmb_bms_server.MessagesJsons.Messages
import com.jmb_bms_server.TmpServerModel
import com.jmb_bms_server.data.TeamEntry
import com.jmb_bms_server.data.UserProfile
import com.jmb_bms_server.data.UserSession
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import org.bson.types.ObjectId
import java.lang.NumberFormatException
import java.util.concurrent.atomic.AtomicReference

class TeamCommandsHandler(private val model: TmpServerModel, private val server: NettyApplicationEngine) {

    private suspend fun sendJsonToAllUsers(message: String)
    {
        model.userSessionsSet.forEach {
            it.session.get()?.send(Frame.Text(message))
        }
    }

    fun createTeam(leadersName: String,parameters: Map<String, Any?>): Pair<Boolean, String>
    {
        val name = parameters["teamName"] as? String ?: return Pair(false,"Could not extract team name")
        val icon = parameters["teamIcon"] as? String ?: return Pair(false,"Could not extract team icon")
        val topTeamIdString = parameters["_id"] as? String ?: return Pair(false,"Could not extract teamId")
        val topTeamId = model.teamsSet.find { it._id.get().toString() == topTeamIdString}?.teamName?.get() ?: return Pair(false,"Could not find top team")

        val members = parameters["teamMembers"] as? MutableList<String> ?: return Pair(false,"Could not extract members")
        val m = members.map { e -> model.userSet.find { it._id.get().toString() == e }?.userName?.get() ?: "E"}.toMutableList()

        var list = listOf("",name,icon,leadersName)
        if( !createTeam(list) ) return Pair(false,"Team name is already in use")

        list = listOf("",topTeamId,name)
        addUsersOrTeamsToTeam(list,true)

        m.add(0,"")
        m.add(1,name)

        addUsersOrTeamsToTeam(m,true)
        return Pair(true,"")
    }

    fun createTeam(params: List<String>?): Boolean
    {
        if(params == null)
        {
            println("Wrong number of parameters! Expecting 3 without location 5 with location")
            return false
        }
        val teamName = params[1]
        val symbolCode = if(params[2] == "def") "SFGPU----------" else params[2]
        val leadersName = model.userSet.find { it.userName.get() ==  params[3] }?._id?.get()

        if(leadersName == null)
        {
            println("User with name ${params[3]} doesn't exists")
            return false
        }
        //invertovane farby // invertovane farby  nerob cervene farby //sablony sprav aby sa dali pridavat
        val newEntry = TeamEntry().apply {
            this.teamName.set(teamName)
            this.teamIcon.set(symbolCode)
            this.teamLead.set(leadersName)
        }

        if ( params.size == 5)
        {
            val lat: Double; val long: Double

            try {
                lat = params[4].toDouble()
                long = params[5].toDouble()
            }catch (_: NumberFormatException)
            {
                println("Latitude or longitude is not valid number")
                return false
            }
            newEntry.apply {
                this.teamLocation.set(
                    Location(AtomicReference(lat),AtomicReference(long))
                )
            }
        }
        return runBlocking{
            if( !model.addNewTeam(newEntry) )
            {
                println("Team with username ${params[1]} already exists")
                return@runBlocking false
            }
            sendJsonToAllUsers(Messages.teamProfile(newEntry))
            return@runBlocking true
        }

    }

    fun deleteTeam(teamName: String)
    {
        deleteTeam(listOf("",teamName))
    }

    fun deleteTeam(params: List<String>?)
    {
        if( params == null)
        {
            println("Invalid number of parameters! Expecting 1")
            return
        }
        val entry = model.teamsSet.find{ it.teamName.get() == params[1]}

        if( entry == null)
        {
            println("No team with name ${params[1]} exists")
            return
        }
        runBlocking {
            model.removeTeam(entry)
            sendJsonToAllUsers(Messages.teamDeletion(entry._id.get().toString(),false))
        }
    }

    private suspend fun editTeamValues(key: String, value: String, teamEntry: TeamEntry): Boolean
    {
        when(key)
        {
            TeamEntry::teamName.name -> {
                if(teamEntry.teamName.get() == value) return true

                val existing = model.teamsSet.find { it.teamName.get() == value }
                if(existing != null)
                {
                    println("Another team already has name $value")
                    return false
                }
                teamEntry.teamName.set(value)
                //model.updateTeam(teamEntry.teamId.get() ?: ObjectId("E"),Updates.set(TeamEntry::teamName.name,value))
                model.teamCollection.findOneAndUpdate(
                    Filters.eq(TeamEntry::_id.name,teamEntry._id.get()),
                    Updates.set(TeamEntry::teamName.name,value)
                )
                sendJsonToAllUsers(Messages.updateTeam(teamEntry))
            }
            TeamEntry::teamIcon.name -> {
                teamEntry.teamIcon.set(value)
                //model.updateTeam(teamEntry.teamId.get() ?: ObjectId("E"),Updates.set(TeamEntry::teamIcon.name,value))

                model.teamCollection.findOneAndUpdate(
                    Filters.eq(TeamEntry::_id.name,teamEntry._id.get()),
                    Updates.set(TeamEntry::teamIcon.name,value)
                )
                sendJsonToAllUsers(Messages.updateTeam(teamEntry))
            }
            Location::lat.name -> {
                if( teamEntry.teamLocation.get() == null) teamEntry.teamLocation.set(
                    Location(AtomicReference(value.toDouble()), AtomicReference(0.0))
                )
                else teamEntry.teamLocation.get()?.lat?.set(value.toDouble())

                model.teamCollection.findOneAndUpdate(
                    Filters.eq(TeamEntry::_id.name,teamEntry._id.get()),
                    Updates.set(TeamEntry::teamLocation.name,teamEntry.teamLocation.get()?.getStorableLocation())
                )
            }
            Location::long.name -> {
                if(teamEntry.teamLocation.get() == null) teamEntry.teamLocation.set(
                    Location(AtomicReference(0.0),(AtomicReference(value.toDouble())))
                )
                else teamEntry.teamLocation.get()?.long?.set(value.toDouble())

                model.teamCollection.findOneAndUpdate(
                    Filters.eq(TeamEntry::_id.name,teamEntry._id.get()),
                    Updates.set(TeamEntry::teamLocation.name,teamEntry.teamLocation.get()?.getStorableLocation())
                )
            }
            TeamEntry::teamLead.name -> {
                val newLeader = model.userSet.find { it.userName.get() == value }

                if(newLeader == null)
                {
                    println("User with name $value doesn't exists")
                    return false
                }
                if( teamEntry.teamLead.get().toString() == newLeader._id.get().toString()) return true

                teamEntry.teamLead.set(newLeader._id.get())

                if(newLeader.teamEntry.get().find { it == teamEntry._id.get() } == null)
                    model.addUserToTeam(newLeader,teamEntry)

                sendJsonToAllUsers(Messages.changingTeamLeader(teamEntry,newLeader))
            }
            else -> {
                println("Unknown key $key")
                return false
            }
        }
        return true
    }

    fun updateLeader(teamName: String, userName: String)
    {
        updateTeam(listOf("",teamName,TeamEntry::teamLead.name,userName))
    }

    fun updateTeam(teamName: String, teamIcon: String, newTeamName: String)
    {
        updateTeam(listOf("",teamName,TeamEntry::teamName.name,newTeamName,TeamEntry::teamIcon.name,teamIcon))
    }

    fun updateTeam(params: List<String>?)
    {
        if(params == null)
        {
            println("Too few parameters for updateUser command. Expecting at least 3 and even")
            return
        }
        if(params.size % 2 != 0)
        {
            println("Not even number of arguments for updateTeam command")
            return
        }
        val entry = model.teamsSet.find { it.teamName.get() == params[1] }

        if(entry == null)
        {
            println("Team with name ${params[1]} doesn't exists")
            return
        }

        for (cnt in 2 until params.size step 2)
        {
            val key = params[cnt]
            val value = params[cnt + 1]
            runBlocking {
                editTeamValues(key,value,entry)
            }
        }
    }

    fun addUsersOrTeamsToTeam(userName: String, teamName: String, adding: Boolean)
    {
        addUsersOrTeamsToTeam(listOf("",teamName,userName),adding)
    }

    fun addUsersOrTeamsToTeam(params: List<String>?, adding: Boolean)
    {
        if(params == null)
        {
            println("Wrong number of arguments! Expecting at least 2")
            return
        }
        val team = model.teamsSet.find { it.teamName.get() == params[1] }

        if(team == null)
        {
            println("No team with name ${params[1]} exists")
            return
        }
        for(cnt in 2 until params.size)
        {
            val user = model.userSet.find { it.userName.get() == params[cnt] }
            var newTeam: TeamEntry? = null
            if( user == null)
            {
                newTeam = model.teamsSet.find { it.teamName.get() == params[cnt] }
                if(newTeam == null)
                {
                    println("No user with name ${params[cnt]} exists! Continuing...")
                    continue
                }
            }
            runBlocking {
                if(adding) {
                    if(user != null)
                    {
                        model.addUserToTeam(user, team)
                    } else
                    {
                        model.addTeamToTeam(team,newTeam!!)
                    }
                    sendJsonToAllUsers(Messages.manageTeamRoster(team._id.get()!!,if(user != null)user._id.get()!! else newTeam?._id?.get()!!,true))
                }
                else
                {
                    if(user != null)
                    {
                        model.removeUserFromTeam(user,team)
                    } else
                    {
                        model.removeTeamFromTeam(team,newTeam!!)
                    }
                    sendJsonToAllUsers(Messages.manageTeamRoster(team._id.get()!!,if(user != null)user._id.get()!! else newTeam?._id?.get()!!,false))
                }
            }
        }
    }

    private fun printTeamInfo(teamEntry: TeamEntry, max: Int = 0, index: Int = -1)
    {
        var entries = ""
        teamEntry.teamEntry.get().forEach {
            entries += " $it"
        }
        val users = model.findAllUserWithinTeam(teamEntry._id.get() ?: ObjectId(""))

        var usersString = ""

        users.forEachIndexed { indexIn, it ->
            usersString += ("     ID: ${it._id.get()} - UserName: ${it.userName.get()}" + "\n")
        }

        println("+----------------------------------------------+\n" +
                "TeamId: ${teamEntry._id.get().toString()}\n" +
                "TeamName: ${teamEntry.teamName.get()}\n" +
                "TeamIcon: ${teamEntry.teamIcon.get()}\n" +
                "TeamLocation: " + if(teamEntry.teamLocation.get() != null) "Lat ${teamEntry.teamLocation.get()?.lat?.get()} - Long ${teamEntry.teamLocation.get()?.long?.get()}\n" else "Team is not sharing location\n" +
                "TeamLeadId:  ${teamEntry.teamLead.get()}\n" +
                "TeamEntries: $entries\n" +
                "TeamUsers:\n" + usersString + if(index == max - 1) "+----------------------------------------------+" else ""
        )
    }

    fun printTeam(params: List<String>?)
    {
        if(params == null)
        {
            println("Incorrect number of arguments! Expecting one")
            return
        }
        val team = model.teamsSet.find { it.teamName.get() == params[1] }

        if( team == null)
        {
            println("No team with name ${params[1]} exists")
            return
        }
        printTeamInfo(team)
    }

    fun printAllTeams()
    {
        val max = model.teamsSet.size
        model.teamsSet.forEachIndexed { index, teamEntry ->
            printTeamInfo(teamEntry, max, index)
        }
    }

    fun teamLocSh(teamName: String, on: Boolean)
    {
        runBlocking {
            model.userSessionsSet.forEach {
                it.session.get()?.send(Frame.Text(Messages.requestUserForStartOrStopOfLocShare(teamName,on)))
            }
        }
    }

    fun teamLocSh(params: List<String>?)
    {
        if(params == null)
        {
            println("Wrong number of parameters! Expected 2")
            return
        }
        val team = model.teamsSet.find { it.teamName.get() == params[1] }

        if(team == null)
        {
            println("Team with name ${params[1]} doesn't exits")
            return
        }

        val on = if(params[2] == "on") {
            true
        } else if( params[2] == "off") {
            false
        } else {
            println("Unknown option ${params[2]}")
            return
        }
        teamLocSh(team.teamName.get(),on)
    }
}