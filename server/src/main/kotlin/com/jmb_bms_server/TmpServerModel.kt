package com.jmb_bms_server

import com.jmb_bms_server.MessagesJsons.Messages
import com.jmb_bms_server.data.*
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import java.util.Collections
import java.util.NoSuchElementException
import java.util.concurrent.atomic.AtomicReference

class TmpServerModel(val collection: MongoCollection<StorableUserProfile>, val teamCollection: MongoCollection<StorableTeamEntry>, restoreFromDb: Boolean) {

    //val userList = Collections.synchronizedList(mutableListOf<UserProfile>())
    val userSet = Collections.synchronizedSet(HashSet<UserProfile>())
    val userSessionsSet = Collections.synchronizedSet(HashSet<UserSession>())

    val teamsSet = Collections.synchronizedSet(HashSet<TeamEntry>())

    init {
        runBlocking {
            if(restoreFromDb)
            {
                initUsers()
                initTeams()

            } else {
                collection.deleteMany(Filters.empty())
                teamCollection.deleteMany(Filters.empty())
            }
        }
    }

    private suspend fun initUsers()
    {
        val set = HashSet<StorableUserProfile>()
        collection.find().toCollection(set)

        set.forEach {
            userSet.add(
                UserProfile(AtomicReference(it._id),
                AtomicReference(it.userName),
                AtomicReference(it.symbolCode),
                AtomicReference(Location(AtomicReference(it.location?.lat), AtomicReference(it.location?.long))),
                AtomicReference(false),
                AtomicReference(it.teamEntry)
            )
            )
        }
        userSet.forEach{
            userSessionsSet.add(UserSession(it._id))    // I hope this will work well !!
        }
    }

    private suspend fun initTeams()
    {
        val teams = HashSet<StorableTeamEntry>()
        teamCollection.find().toCollection(teams)

        teams.forEach {
            teamsSet.add(TeamEntry().updateValuesFromStorableTeamEntry(it))
        }
    }


    suspend fun checkAndAddReconectedUser(userProfile: UserProfile, session: DefaultWebSocketSession): UserProfile?
    {
        val profile = userSet.find { it._id.get() == userProfile._id.get() }
        if(profile == null)
        {
            addNewUser(userProfile,session)
            return userSet.find { it.userName == userProfile.userName }
        }

        val sessionEntry = userSessionsSet.find { it.userId.get() == profile._id.get() }
        if(sessionEntry == null)
        {
            userSessionsSet.add(UserSession(profile._id,AtomicReference( session )))
        } else
        {
            if(sessionEntry.session.get() == null)
            {
                println("OverHere")
                userSessionsSet.elementAt(userSessionsSet.indexOfFirst { it.userId.get() == profile._id.get() }).session.set(session)
            } else
            {
                sessionEntry.session.get()?.send(Frame.Ping(ByteArray(0)))
                delay(2000)

                if(sessionEntry.session.get() == null) return checkAndAddReconectedUser(userProfile, session)
                println("User with same ID ${userProfile._id} already has active connection with server")
                return null
            }
        }
        //TODO if yes then when I implement points and messages those names must change too
        profile.userName = userProfile.userName
        profile.symbolCode = userProfile.symbolCode
        profile.location = userProfile.location
        profile.connected.set(true)

        val update = Updates.combine(
            Updates.set(profile::userName.name,profile.userName.get()),
            Updates.set(profile::symbolCode.name,profile.symbolCode.get()),
            Updates.set(profile::location.name,profile.location.get()),
            Updates.set(profile::connected.name,profile.connected.get())
        )
        collection.findOneAndUpdate(Filters.eq(userProfile::_id.name, userProfile._id.get()),update)
        return profile
    }

    suspend fun updateProfile(id: ObjectId, update: Bson)
    {
        collection.findOneAndUpdate(Filters.eq(UserProfile::_id.name,id),update)
    }


    suspend fun addNewUser(newUserProfile: UserProfile, session: DefaultWebSocketSession?): Boolean
    {
        if( userSet.find { it.userName == newUserProfile.userName } != null) return false

        return try {
            collection.find(Filters.eq(newUserProfile::userName.name, newUserProfile.userName.get())).first()
            println("Found something")
            false
        } catch (_: NoSuchElementException) {
            println("Storing id and user session")
            collection.insertOne(newUserProfile.getStorableUserProfile()).insertedId ?: return false //if this fails it will be fun
            val id = collection.find<StorableUserProfile>(Filters.eq(UserProfile::userName.name,newUserProfile.userName.get())).toList().first()._id
            newUserProfile._id.set(id)
            userSet.add(newUserProfile)
            userSessionsSet.add(UserSession(AtomicReference(id), AtomicReference(session)))
            println("Stored session")
            true
        }
    }

    private suspend fun checkIfDelUserWasTeamLead(userProfile: UserProfile)
    {
        teamsSet.forEach {
            checkTeamLeaderAndUpdateTeam(userProfile,it)
        }
    }

    suspend fun removeUser(userProfile: UserProfile): Boolean
    {
        if(userSet.find { it._id == userProfile._id } == null) return false
        userSet.remove(userProfile)
        val session = userSessionsSet.find { it.userId == userProfile._id }?.session?.get()
        if(session != null)
        {
            session.send(Frame.Text(Messages.bye("Admin terminated your connection")))
            session.send(Frame.Close(CloseReason(CloseReason.Codes.NORMAL,"Admin terminated your connection")))
            println("Terminated ${userProfile.userName}\'s connection")
        }

        userSessionsSet.removeIf { it.userId == userProfile._id }
        collection.findOneAndDelete(Filters.eq(userProfile::_id.name,userProfile._id.get()))
        checkIfDelUserWasTeamLead(userProfile)
        return true
    }

    suspend fun getAllUsersFromDatabase(): List<UserProfile> {
        return collection.find(Filters.empty()).toList().map { UserProfile().updateValuesFromStorableUserProfile(it) }

    }

    suspend fun addNewTeam(teamEntry: TeamEntry): Boolean
    {
        if( teamsSet.find { it.teamName == teamEntry.teamName } != null) return false

        return try {
            teamCollection.find(Filters.eq(teamEntry::teamName.name,teamEntry.teamName.get())).first()
            false
        } catch (_: Exception)
        {
            teamCollection.insertOne(teamEntry.getStorableTeamEntry()).insertedId ?: return false
            val id = teamCollection.find(Filters.eq(teamEntry::teamName.name,teamEntry.teamName.get())).first()._id
            teamEntry._id.set(id)
            teamsSet.add(teamEntry)

            val leader = userSet.find { teamEntry.teamLead.get() == it._id.get() }

            if(leader == null)
            {
                teamsSet.remove(teamEntry)
                removeTeam(teamEntry)
                return false
            }
            addUserToTeam(leader,teamEntry)
            true
        }
    }

    fun findAllUserWithinTeam(teamId: ObjectId): HashSet<UserProfile> = userSet.filter { profile ->
        profile.teamEntry.get().find { it == teamId } != null
    }.toHashSet()

    suspend fun removeTeamFromUsers(teamId: ObjectId)
    {
        val teamMembers = findAllUserWithinTeam(teamId)

        teamMembers.forEach { profile ->
            profile.teamEntry.get().remove(teamId)
            updateProfile(profile._id.get() ?: ObjectId(),Updates.set(profile::teamEntry.name, profile.teamEntry.get()))
        }
    }

    suspend fun removeTeam(teamEntry: TeamEntry): Boolean
    {
        if(teamsSet.find { it._id.get() == teamEntry._id.get() } == null) return false
        teamsSet.remove(teamEntry)
        removeTeamFromUsers(teamEntry._id.get() ?: ObjectId())

        teamCollection.findOneAndDelete(Filters.eq(teamEntry::_id.name,teamEntry._id.get()))

        return true
    }

    private suspend fun checkTeamLeaderAndUpdateTeam(userProfile: UserProfile, teamEntry: TeamEntry)
    {
        if(userProfile._id.get() == teamEntry.teamLead.get())
        {
            val teamMates = findAllUserWithinTeam(teamEntry._id.get() ?: ObjectId())
            if(teamMates.isEmpty())
            {
                println("No other users in team... Deleting team ${teamEntry.teamName.get()}")
                removeTeam(teamEntry)
                return
            }
            println(teamMates)
            teamEntry.teamLead.set(teamMates.first()._id.get())
        }
    }

    suspend fun removeUserFromTeam(userProfile: UserProfile, teamEntry: TeamEntry)
    {
        userProfile.teamEntry.get().remove(teamEntry._id.get()!!)
        updateProfile(userProfile._id.get() ?: ObjectId("K"),Updates.set(userProfile::teamEntry.name,userProfile.teamEntry.get()))
        checkTeamLeaderAndUpdateTeam(userProfile, teamEntry)
    }

    suspend fun addUserToTeam(userProfile: UserProfile,teamEntry: TeamEntry)
    {
        if( !userProfile.teamEntry.get().add(teamEntry._id.get()!!)) return
        updateProfile(userProfile._id.get() ?: ObjectId(),Updates.set(userProfile::teamEntry.name,userProfile.teamEntry.get()))
    }

    suspend fun addTeamToTeam(targetTeamEntry: TeamEntry, addedTeamEntry: TeamEntry)
    {
        if( !addedTeamEntry.teamEntry.get().add(targetTeamEntry._id.get()!!)) return
        updateTeam(addedTeamEntry._id.get()!!,Updates.set(TeamEntry::teamEntry.name,addedTeamEntry.teamEntry.get()))
    }

    suspend fun removeTeamFromTeam(targetTeamEntry: TeamEntry, removedTeamEntry: TeamEntry)
    {
        removedTeamEntry.teamEntry.get().remove(targetTeamEntry._id.get()!!)
        updateTeam(removedTeamEntry._id.get()!!,Updates.set(TeamEntry::teamEntry.name,removedTeamEntry.teamEntry.get()))
    }

    suspend fun updateTeam(teamId: ObjectId, update: Bson)
    {
        collection.findOneAndUpdate(Filters.eq(TeamEntry::_id.name,teamId),update)
    }

    suspend fun clearDatabase()
    {
        collection.deleteMany(Filters.empty())
    }

}