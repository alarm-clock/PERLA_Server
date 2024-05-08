/**
 * @file: TmpServerModel.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing TmpServerModel class
 */
package com.jmb_bms_server

import com.jmb_bms_server.MessagesJsons.Messages
import com.jmb_bms_server.data.chat.StorableChat
import com.jmb_bms_server.data.location.Location
import com.jmb_bms_server.data.point.PointEntry
import com.jmb_bms_server.data.point.StorablePointEntry
import com.jmb_bms_server.data.team.StorableTeamEntry
import com.jmb_bms_server.data.team.TeamEntry
import com.jmb_bms_server.data.user.StorableUserProfile
import com.jmb_bms_server.data.user.UserProfile
import com.jmb_bms_server.data.user.UserSession
import com.jmb_bms_server.utils.GetJarPath
import com.jmb_bms_server.utils.Logger
import com.jmb_bms_server.utils.Transaction
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.flow.toList
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import java.io.File
import java.util.Collections
import java.util.NoSuchElementException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicReference

/**
 * Class that servers as model for whole server
 *
 * @property collection User profiles collection
 * @property teamCollection Team profiles collection
 * @property pointCollection Point profiles collection
 * @param db Database
 * @param restoreFromDb Flag indicating if instance will be initialized from database or if database should be wiped and
 * @constructor Based on restoreFromDB flag initializes instance from DB or wipes DB clean and initializes model blank
 *
 * instance initialized blank
 */
class TmpServerModel(
    val collection: MongoCollection<StorableUserProfile>,
    val teamCollection: MongoCollection<StorableTeamEntry>,
    val pointCollection: MongoCollection<StorablePointEntry>,
    db: MongoDatabase,
    restoreFromDb: Boolean) {

    val userSet = Collections.synchronizedSet(HashSet<UserProfile>())
    val userSessionsSet = Collections.synchronizedSet(HashSet<UserSession>())

    val pointSet = Collections.synchronizedSet(HashSet<PointEntry>())

    val teamsSet = CopyOnWriteArraySet<TeamEntry>()

    val chatDb = ChatDBOperations(db)

    val tmpTransactionFiles = CopyOnWriteArrayList<Transaction>()

    init {
        runBlocking {
            if(restoreFromDb)
            {
                initUsers()
                initTeams()
                initPoints()
                initChat()

            } else {
                collection.deleteMany(Filters.empty())
                teamCollection.deleteMany(Filters.empty())
                deleteAllPointsAndFiles()
                deleteChats()
            }
        }
    }

    /**
     * Method that creates General chat room
     *
     */
    private suspend fun initChat()
    {
        if(chatDb.getChatId("General") == null)
        {
            chatDb.createChat(StorableChat(name = "General", ownerId = "admin", members = userSet.map { it._id.get().toString() }.toMutableList()))
        }

    }

    /**
     * Method that deletes all chat rooms
     *
     */
    private suspend fun deleteChats()
    {
        val chats = chatDb.getAllChats()
        chats?.forEach {
            chatDb.deleteChat(it._id.toString())
        }
        initChat()
    }

    /**
     * Method that initializes [userSet] from [collection]. Then initializes [userSessionsSet]
     *
     */
    private suspend fun initUsers()
    {
        val set = HashSet<StorableUserProfile>()
        collection.find().toCollection(set)

        set.forEach {
            userSet.add(
                UserProfile(AtomicReference(it._id),
                AtomicReference(it.userName),
                AtomicReference(it.symbolCode),
                AtomicReference( if(it.location == null) null else  Location(AtomicReference(it.location?.lat), AtomicReference(it.location?.long))),
                AtomicReference(false),
                AtomicReference(it.teamEntry)
            )
            )
        }
        userSet.forEach{
            userSessionsSet.add(UserSession(it._id))    // I hope this will work well !!
        }
    }

    /**
     * Method that initializes [teamsSet] from [teamCollection]
     *
     */
    private suspend fun initTeams()
    {
        val teams = HashSet<StorableTeamEntry>()
        teamCollection.find().toCollection(teams)

        teams.forEach {
            teamsSet.add(TeamEntry().updateValuesFromStorableTeamEntry(it))
        }
    }

    /**
     * Method that deletes all points and files attached to them.
     *
     */
    private suspend fun deleteAllPointsAndFiles()
    {
        val points = HashSet<StorablePointEntry>()
        pointCollection.find().toCollection(points)

        points.forEach {
            it.files.forEach { name ->
                File("${GetJarPath.currentWorkingDirectory}/files/$name").delete()
            }
        }
        pointCollection.deleteMany(Filters.empty())
    }

    /**
     * Method that initializes [pointSet] from [pointCollection]
     *
     */
    private suspend fun initPoints()
    {
        val points = HashSet<StorablePointEntry>()
        pointCollection.find().toCollection(points)

        points.forEach {
            pointSet.add(PointEntry().updateValuesFromStorablePointEntry(it))
        }
    }


    /**
     * Method that checks existing user and adds his [session] to [userSessionsSet]. If user with same [UserProfile._id]
     * has active connection null is returned.
     *
     * @param userProfile Profile of reconnected user
     * @param session [DefaultWebSocketSession] that will be stored in [userSessionsSet]
     * @return [UserProfile] on success else null
     */
    suspend fun checkAndAddReconectedUser(userProfile: UserProfile, session: DefaultWebSocketSession): UserProfile?
    {
        //println(userProfile._id.get().toString())
        val profile = userSet.find { it._id.get().toString() == userProfile._id.get().toString() }
        if(profile == null)
        {
            //user was most likely from another server or his profile was deleted in meanwhile
            addNewUser(userProfile,session)
            return userSet.find { it.userName.get() == userProfile.userName.get() }
        }

        val sessionEntry = userSessionsSet.find { it.userId.get().toString() == profile._id.get().toString() }
        if(sessionEntry == null)
        {
            //profile exists without active connection
            userSessionsSet.add(UserSession(profile._id,AtomicReference( session )))
        } else
        {
            if(sessionEntry.session.get() == null)
            {
                //profile exists without active connection
                userSessionsSet.elementAt(userSessionsSet.indexOfFirst { it.userId.get().toString() == profile._id.get().toString() }).session.set(session)
            } else
            {
                //user with same id has active connection so sending ping to "original" user and wait for timeout. If exception occurred then
                //user most likely lost for connection for few seconds and now is trying to reconnect. Else send new user fail
                sessionEntry.session.get()?.send(Frame.Ping(ByteArray(0)))
                delay(17000) //3000

                if(sessionEntry.session.get() == null) return checkAndAddReconectedUser(userProfile, session)
                Logger.log("User with same ID ${userProfile._id} already has active connection with server","none")
                return null
            }
        }

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

    /**
     * Method that updates user's collection entry
     *
     * @param id ID of user that will be updated
     * @param update [Bson] object with update
     */
    suspend fun updateProfile(id: ObjectId, update: Bson)
    {
        collection.findOneAndUpdate(Filters.eq(UserProfile::_id.name,id),update)
    }

    /**
     * Method that adds or removes user to/from General chat room
     *
     * @param userId ID of user that will be added or removed
     * @param add Flag indicating if user will be added or removed
     */
    private suspend fun addOrRemoveUserFromGeneral(userId: String, add: Boolean)
    {
        if(add)
        {
            chatDb.addUsersToChatRoom(listOf(userId),chatDb.getChatId("General")!!)
        } else
        {
            chatDb.removeUserFromChatRoom(chatDb.getChatId("General")!!,userId)
        }
    }

    /**
     * Method that stores [newUserProfile] in collection
     *
     * @param newUserProfile [UserProfile] that will be stored
     * @param session [DefaultWebSocketSession] that will be stored in [userSessionsSet]
     * @return True on success else false
     */
    suspend fun addNewUser(newUserProfile: UserProfile, session: DefaultWebSocketSession?): Boolean
    {
        if( userSet.find { it.userName.get() == newUserProfile.userName.get() } != null) return false
        if(newUserProfile.userName.get().compareTo("admin",true) == 0) return false

        return try {
            collection.find(Filters.eq(newUserProfile::userName.name, newUserProfile.userName.get())).first()
            Logger.log("Found something",newUserProfile.userName.get())
            false
        } catch (_: NoSuchElementException) {
            Logger.log("Storing id and user session",newUserProfile.userName.get())
            collection.insertOne(newUserProfile.getStorableUserProfile()).insertedId ?: return false //if this fails it will be fun
            val id = collection.find<StorableUserProfile>(Filters.eq(UserProfile::userName.name,newUserProfile.userName.get())).toList().first()._id
            newUserProfile._id.set(id)
            userSet.add(newUserProfile)
            userSessionsSet.add(UserSession(AtomicReference(id), AtomicReference(session)))
            addOrRemoveUserFromGeneral(id.toString(),true)
            Logger.log("Stored session",newUserProfile.userName.get())
            true
        }
    }

    /**
     * Method that checks if user was team leader of any team
     *
     * @param userProfile Profile that will be checked
     */
    private suspend fun checkIfDelUserWasTeamLead(userProfile: UserProfile)
    {
        teamsSet.forEach {
            checkTeamLeaderAndUpdateTeam(userProfile,it)
        }
    }

    /**
     * Method that removes user
     *
     * @param userProfile Profile that will be removed
     * @return False if given profile doesn't exist else true
     */
    suspend fun removeUser(userProfile: UserProfile): Boolean
    {
        if(userSet.find { it._id.get().toString() == userProfile._id.get().toString() } == null) return false
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
        addOrRemoveUserFromGeneral(userProfile._id.get().toString(),false)
        chatDb.removeUserFromAllChatRooms(userProfile._id.get().toString())
        return true
    }

    suspend fun getAllUsersFromDatabase(): List<UserProfile> {
        return collection.find(Filters.empty()).toList().map { UserProfile().updateValuesFromStorableUserProfile(it) }

    }

    /**
     * Method that will store new [teamEntry] in collection
     *
     * @param teamEntry New [TeamEntry] that will be stored
     * @return True on success else false (for example another team has same name)
     */
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

    /**
     * Find all user within team
     *
     * @param teamId ID of team who's all members will be found
     * @return All team members
     */
    fun findAllUserWithinTeam(teamId: ObjectId): HashSet<UserProfile> = userSet.filter { profile ->
        profile.teamEntry.get().find { it == teamId } != null
    }.toHashSet()

    /**
     * Method that removes team from all of its members
     *
     * @param teamId Removed team's ID
     */
    suspend fun removeTeamFromUsers(teamId: ObjectId)
    {
        val teamMembers = findAllUserWithinTeam(teamId)

        teamMembers.forEach { profile ->
            profile.teamEntry.get().remove(teamId)
            updateProfile(profile._id.get() ?: ObjectId(),Updates.set(profile::teamEntry.name, profile.teamEntry.get()))
        }
    }

    /**
     * Method that will remove team from collection
     *
     * @param teamEntry Removed team entry
     * @return False if [teamEntry] doesn't exists else true
     */
    suspend fun removeTeam(teamEntry: TeamEntry): Boolean
    {
        if(teamsSet.find { it._id.get() == teamEntry._id.get() } == null) return false
        teamsSet.remove(teamEntry)
        removeTeamFromUsers(teamEntry._id.get() ?: ObjectId())

        teamCollection.findOneAndDelete(Filters.eq(teamEntry::_id.name,teamEntry._id.get()))

        return true
    }

    /**
     * Method that checks if user is team leader and if yes then makes another random user new team leader. If user
     * is last member of team then team is deleted.
     *
     * @param userProfile Checked user
     * @param teamEntry Team where user is maybe leader
     */
    private suspend fun checkTeamLeaderAndUpdateTeam(userProfile: UserProfile, teamEntry: TeamEntry)
    {
        if(userProfile._id.get() == teamEntry.teamLead.get())
        {
            val teamMates = findAllUserWithinTeam(teamEntry._id.get() ?: ObjectId())
            if(teamMates.isEmpty())
            {
                Logger.log("No other users in team... Deleting team ${teamEntry.teamName.get()}",userProfile.userName.get())
                removeTeam(teamEntry)
                sendMessageToAllConnected(Messages.teamDeletion(teamEntry._id.get().toString(),false))
                return
            }
            val newLeader = userSet.find { it._id.get().toString() == teamMates.first()._id.toString()  }
            teamEntry.teamLead.set(newLeader!!._id.get())
            sendMessageToAllConnected( Messages.changingTeamLeader(teamEntry,newLeader))
        }
    }

    /**
     * Method that removes user from team. If it was last user then also removes team
     *
     * @param userProfile removed profile
     * @param teamEntry Team from which user is removed
     */
    suspend fun removeUserFromTeam(userProfile: UserProfile, teamEntry: TeamEntry)
    {
        userProfile.teamEntry.get().remove(teamEntry._id.get()!!)
        updateProfile(userProfile._id.get() ?: ObjectId("K"),Updates.set(userProfile::teamEntry.name,userProfile.teamEntry.get()))
        checkTeamLeaderAndUpdateTeam(userProfile, teamEntry)
    }

    /**
     * Method that adds user to a team
     *
     * @param userProfile Added user
     * @param teamEntry Team to which user will be added
     */
    suspend fun addUserToTeam(userProfile: UserProfile, teamEntry: TeamEntry)
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

    /**
     * Method that updates team in collection
     *
     * @param teamId ID of updated team
     * @param update [Bson] object with update
     */
    suspend fun updateTeam(teamId: ObjectId, update: Bson)
    {
        collection.findOneAndUpdate(Filters.eq(TeamEntry::_id.name,teamId),update)
    }

    /**
     * Method that returns if user exists in [userSet] and is connected or exists and is disconnected or if user doesn't exist
     * in model
     *
     * @param id Checked id
     * @return 1 if user doesn't exist, 2 if isn't connected, 0 if exits and is connected
     */
    fun validateId(id: String): Int
    {
        val userProfile = userSet.find { it._id.get().toString() == id } ?: return 1
        if( !userProfile.connected.get() ) return 2
        return 0
    }

    /**
     * Method that stores [newPoint] in [pointSet] and [pointCollection]
     *
     * @param newPoint New point that will be stored
     * @return ID on success else null
     */
    suspend fun addPoint(newPoint: PointEntry): String?
    {
        if( pointSet.find { it._id.get().toString() == newPoint._id.get().toString() } != null ) return null

        pointCollection.insertOne(newPoint.getStorablePointEntry()).insertedId ?: return null
        val id = pointCollection.find(Filters.eq(PointEntry::_id.name,newPoint._id.get().toString())).first()._id
        pointSet.add(newPoint)

       return id.toString()
    }

    /**
     * Remove point
     *
     * @param id Removed point's id
     */
    suspend fun removePoint(id: String)
    {
        pointSet.removeIf { it._id.get().toString() == id }
        pointCollection.deleteOne(Filters.eq(PointEntry::_id.name,id))
    }

    /**
     * Update point
     *
     * @param id Updated point's id
     * @param updates [Bson] object with update
     */
    suspend fun updatePoint(id: String,updates: Bson)
    {
        pointCollection.updateOne(Filters.eq(PointEntry::_id.name,id),updates)
    }

    /**
     * Clear user collection
     *
     */
    suspend fun clearDatabase()
    {
        collection.deleteMany(Filters.empty())
    }

    /**
     * Method that sends message to certain group
     *
     * @param jsonString Message that will be sent
     * @param ids UserIDs to whom message will be sent
     */
    fun sendMessageToCertainGroup(jsonString: String, ids: List<String>)
    {
        CoroutineScope(Dispatchers.IO).launch {
            val sessions = userSessionsSet.filter { it.userId.get().toString() in ids }.map { it.session }
            sessions.forEach { it.get()?.send(Frame.Text(jsonString)) }
        }
    }

    /**
     * Method that sends message to all users
     *
     * @param jsonString Message that will be sent to all users
     */
    fun sendMessageToAllConnected(jsonString: String)
    {
        CoroutineScope(Dispatchers.IO).launch {
            userSessionsSet.forEach {
                it.session.get()?.send(Frame.Text(jsonString))
            }
        }
    }
}