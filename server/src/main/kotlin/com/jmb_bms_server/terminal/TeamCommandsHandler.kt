/**
 * @file: TeamCommandsHandler.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing TeamCommandsHandler class
 */
package com.jmb_bms_server.terminal

import com.jmb_bms_server.data.location.Location
import com.jmb_bms_server.MessagesJsons.Messages
import com.jmb_bms_server.TmpServerModel
import com.jmb_bms_server.data.team.TeamEntry
import com.jmb_bms_server.utils.MissingParameter
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import org.bson.types.ObjectId
import java.lang.NumberFormatException
import java.util.concurrent.atomic.AtomicReference

/**
 * Class that implements all teams methods
 *
 * @property model server model, used for database operations and access user sessions
 * @property server
 */
class TeamCommandsHandler(private val model: TmpServerModel, private val server: NettyApplicationEngine) {

    /**
     * Method that sends [message] to all users
     *
     * @param message JSON string that can be parsed by client
     */
    private suspend fun sendJsonToAllUsers(message: String)
    {
        model.userSessionsSet.forEach {
            it.session.get()?.send(Frame.Text(message))
        }
    }

    /**
     * Method that creates team from parameters
     *
     * @param leadersName Name of user that is creating team
     * @param parameters Parsed JSON message
     */
    fun createTeam(leadersName: String, parameters: Map<String, Any?>)
    {
        val name = parameters["teamName"] as? String ?: throw MissingParameter("Could not extract team name")
        val icon = parameters["teamIcon"] as? String ?: throw MissingParameter("Could not extract team icon")
        val topTeamIdString = parameters["_id"] as? String ?: throw MissingParameter("Could not extract teamId")
        val topTeamId = model.teamsSet.find { it._id.get().toString() == topTeamIdString}?.teamName?.get() ?: throw MissingParameter("Could not find top team")

        val members = parameters["teamMembers"] as? MutableList<String> ?: throw  MissingParameter("Could not extract members")
        val m = members.map { e -> model.userSet.find { it._id.get().toString() == e }?.userName?.get() ?: "E"}.toMutableList()

        var list = listOf("",name,icon,leadersName)
        if( !createTeam(list) ) throw  MissingParameter("Team name is already in use")

        list = listOf("",topTeamId,name)
        addUsersOrTeamsToTeam(list,true)

        m.add(0,"")
        m.add(1,name)

        addUsersOrTeamsToTeam(m,true)

    }

    /**
     * Method that creates team from cmd line command parsed into [params]
     *
     * @param params Parsed cmd line command: {createTeam, teamName, {teamSymbol ? def}, team leader userName, {lat, long} ? }
     * @return True on success else false
     */
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

        val newEntry = TeamEntry().apply {
            this.teamName.set(teamName)
            this.teamIcon.set(symbolCode)
            this.teamLead.set(leadersName)
        }

        // location was also present in command
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
                println("Team with name ${params[1]} already exists")
                return@runBlocking false
            }
            sendJsonToAllUsers(Messages.teamProfile(newEntry))
            return@runBlocking true
        }

    }

    /**
     * Method that deletes team identified by its name
     *
     * @param teamName Name of team that is deleted
     */
    fun deleteTeam(teamName: String)
    {
        deleteTeam(listOf("",teamName))
    }

    /**
     * Method that deletes team from cmd line command parsed into [params]
     *
     * @param params Parsed cmd line command: {deleteTeam, teamName}
     */
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

    /**
     * Method that updates team profile's attribute identified by [key] with [value]
     *
     * @param key Attribute name from [TeamEntry] class
     * @param value New value
     * @param teamEntry Team that is being updated
     * @return True on success otherwise false
     */
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

                model.teamCollection.findOneAndUpdate(
                    Filters.eq(TeamEntry::_id.name,teamEntry._id.get()),
                    Updates.set(TeamEntry::teamName.name,value)
                )
                sendJsonToAllUsers(Messages.updateTeam(teamEntry))
            }
            TeamEntry::teamIcon.name -> {
                teamEntry.teamIcon.set(value)


                model.teamCollection.findOneAndUpdate(
                    Filters.eq(TeamEntry::_id.name,teamEntry._id.get()),
                    Updates.set(TeamEntry::teamIcon.name,value)
                )
                sendJsonToAllUsers(Messages.updateTeam(teamEntry))
            }
            Location::lat.name -> {
                if(value == "null")
                {
                    teamEntry.teamLocation.set(null)
                    return true
                }
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
                if(value == "null")
                {
                    teamEntry.teamLocation.set(null)
                    return true
                }
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

    /**
     * Method that updates team's team leader
     *
     * @param teamName Team name
     * @param userName Username of user that will be new team leader
     */
    fun updateLeader(teamName: String, userName: String)
    {
        updateTeam(listOf("",teamName, TeamEntry::teamLead.name,userName))
    }

    /**
     * Method that updates teams name and icon
     *
     * @param teamName Current team name
     * @param teamIcon New team icon
     * @param newTeamName New team name
     */
    fun updateTeam(teamName: String, teamIcon: String, newTeamName: String)
    {
        updateTeam(listOf("",teamName, TeamEntry::teamName.name,newTeamName, TeamEntry::teamIcon.name,teamIcon))
    }

    /**
     * Method that updates teams location
     *
     * @param teamName Team name
     * @param lat Latitude
     * @param long Longitude
     */
    fun updateLocation(teamName: String, lat: String, long: String)
    {
        updateTeam(listOf("",teamName, Location::lat.name,lat, Location::long.name,long))
    }

    /**
     * Method that updates team from parsed cmd line command in [params]
     *
     * @param params Parsed cmd line command in [List]: {updateTeam, teamName, {key, value {key, value {...}}}}
     */
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

    /**
     * Method that adds or removes users or teams from team
     *
     * @param userName Username or team name of user/team that will be added
     * @param teamName Team name of which members list will be updated
     * @param adding Flag indicating if users are being added or removed
     */
    fun addUsersOrTeamsToTeam(userName: String, teamName: String, adding: Boolean)
    {
        addUsersOrTeamsToTeam(listOf("",teamName,userName),adding)
    }

    /**
     * Method that adds or removes users/teams to/from team from cmd line command in [params]
     *
     * @param params Parsed cmd line command in [List]: {addUsersToTeam, teamName, userName, userName, ...}
     * @param adding Flag indicating if method will be adding or removing users
     */
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

    /**
     * Method that prints team's profile to stdout
     *
     * @param teamEntry Team profile
     * @param max Number of teams (used when printing multiple teams)
     * @param index Current index (used when printing multiple teams)
     */
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

    /**
     * Method that prints team profile from cmd line command in [params] to stdout
     *
     * @param params Parsed cmd line command in [List]: {printTeam, team name}
     */
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

    /**
     * Method that prints all teams on stdout
     *
     */
    fun printAllTeams()
    {
        val max = model.teamsSet.size
        model.teamsSet.forEachIndexed { index, teamEntry ->
            printTeamInfo(teamEntry, max, index)
        }
    }

    /**
     * Method that sends turn on/off location message to all team members
     *
     * @param teamName Name of team whose members will receive message
     * @param on Flag indicating if location sharing will be turned on or off
     */
    fun teamLocSh(teamName: String, on: Boolean)
    {
        val teamMates = model.userSet.filter { profile -> profile.teamEntry.get()?.find { it.toString() == teamName } != null }
        val sessions = teamMates.map { profile ->
            model.userSessionsSet.find { it.userId.get().toString() == profile._id.get().toString() }
        }
        println(sessions.isEmpty())
        runBlocking {
            sessions.forEach {

                it?.session?.get()?.send(Frame.Text(Messages.requestUserForStartOrStopOfLocShare(teamName,on)))
            }
        }
    }

    /**
     * Method that turns on/off location sharing for all team members
     *
     * @param params Parsed cmd line command in [List]: {teamLocSh, teamName, {on ? off}}
     */
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