/**
 * @file: UserConnectionHandler.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing UserConnectionHandler class
 */
package com.jmb_bms_server

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jmb_bms_server.MessagesJsons.Messages
import com.jmb_bms_server.data.location.Location
import com.jmb_bms_server.data.team.TeamEntry
import com.jmb_bms_server.data.user.UserProfile
import com.jmb_bms_server.data.user.UserSession
import com.jmb_bms_server.terminal.TerminalSh
import com.jmb_bms_server.utils.*
import com.mongodb.client.model.Updates
import io.ktor.websocket.*
import kotlinx.coroutines.*
import org.bson.types.ObjectId
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Class that stores all connection data, connected user profile and handles reaction to all messages received from client
 *
 * @property session [DefaultWebSocketSession] with client
 * @property serverModel Server model
 * @property terminalSh [TerminalSh] to access model methods
 */
class UserConnectionHandler(val session: DefaultWebSocketSession, val serverModel: TmpServerModel, private val terminalSh: TerminalSh) {

    private var connectionState = ConnectionState.NOT_CONNECTED

    private lateinit var userProfile: UserProfile

    private lateinit var userSession: UserSession

    private var sentBye = false

    private var userInitiatedClose = false

    /**
     * Method that parses received JSON string into [Map]<[String],[Any]?>
     *
     * @param json Message that will be parsed
     * @return Parsed message
     */
    private fun parseServerJson(json: String): Map<String, Any?>
    {
        val gson = Gson()
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        val map: Map<String, Any?> = gson.fromJson(json,type)
        return map
    }

    /**
     * Method that returns messages opCode
     *
     * @param map Parsed message
     * @return opCode or null if something unknown was sent
     */
    private fun getOpcode(map: Map<String, Any?>): Int? {
        return (map["opCode"] as? Double)?.toInt()
    }

    /**
     * Method that returns teamID from parsed message
     *
     * @param map Parsed message
     * @return TeamID or null if there is no teamID
     */
    private fun getTeamId(map: Map<String, Any?>): String? = map["_id"] as? String

    /**
     * Method that will run coroutine on IO context
     *
     * @param code Closure that will be run
     */
    private fun coroutineRun(code: suspend () -> Unit)
    {
        CoroutineScope(Dispatchers.IO).launch {
            code()
        }
    }

    /**
     * Method that relays updated location
     *
     * @param params Parsed JSON message
     */
    private fun relayUpdatedLocation(params: Map<String, Any?>)
    {
        val lat = params["lat"] as? Double
        val long = params["long"] as? Double

        if( lat == null || long == null)
        {
            Logger.log(" stopped sharing location, last recorded location is:" +
                    " Lat ${userProfile.location.get()?.lat?.get()} - Long ${userProfile.location.get()?.long?.get()}",userProfile.userName.get())
            userProfile.location.set( null )
        } else
        {
            if(userProfile.location.get() == null) userProfile.location.set(Location(AtomicReference( 0.0),AtomicReference(0.0)))
            userProfile.location.get()!!.lat.set(lat)
            userProfile.location.get()!!.long.set(long)
            Logger.log(" sent location update, new values:" +
                    " Lat ${userProfile.location.get()?.lat?.get()} - Long ${userProfile.location.get()?.long?.get()}",userProfile.userName.get())
        }
        thread {
            runBlocking{
                serverModel.updateProfile(userProfile._id.get()!!,Updates.set(userProfile::location.name,userProfile.location.get()?.getStorableLocation()))
                serverModel.userSessionsSet.forEach { otherClient ->
                    if(otherClient.userId.get() != userProfile._id.get())
                    {
                        otherClient.session.get()?.send(Frame.Text(Messages.sendLocationUpdate(userProfile)))
                    }
                }
            }
        }
    }

    /**
     * Method that relays user disconnection
     *
     */
    private fun handleDiscRelay()
    {
        thread {
            runBlocking {
                serverModel.userSessionsSet.forEach {
                    it.session.get()?.send(Frame.Text(Messages.userDisconnected(userProfile._id.get()!!.toHexString())))
                }
            }
        }
    }

    /**
     * Method that updates model with values indicating that user was disconnected and relays is to other users
     *
     * @param params Parsed JSON message
     */
    private fun disconnectUserAndRelayIt(params: Map<String, Any?>)
    {
        userInitiatedClose = true
        Logger.log("User is disconnecting\nReason: ${params["reason"] as? String ?: "none"}"
            ,userProfile.userName.get())

        userProfile.connected.set(false)
        userSession.session.set(null)
        connectionState = ConnectionState.NOT_CONNECTED
        sentBye = true
        handleDiscRelay()
        serverModel.tmpTransactionFiles.find { it.owner == userProfile._id.get().toString() }?.failTransactionNoWait()
    }

    /**
     * Method that gets team and checks if user is team leader
     *
     * @param teamId ID of team that will be returned
     * @return [TeamEntry] on success else null
     */
    private fun getTeamByIdAndCheckLead(teamId: String): TeamEntry?
    {
        val team = serverModel.teamsSet.find { it._id.get().toString() == teamId } ?: return null
        if( team.teamLead.get().toString() != userProfile._id.get().toString())
        {
            return null
        }
        return team
    }

    /**
     * Method that handles user updating his profile
     *
     * @param params Parsed JSON message
     */
    private fun handleUserUpdatingProfile(params: Map<String, Any?>)
    {
        val newUserName = params[userProfile::userName.name] as? String
        val newSymbol = params[userProfile::symbolCode.name] as? String

        var somethingChanged = false;

        if(newSymbol == null && newUserName == null) return

        thread {
            runBlocking {
                if(newUserName != null && userProfile.userName.get() != newUserName)
                {
                    userProfile.userName.set(newUserName)
                    serverModel.updateProfile(userProfile._id.get()!!,Updates.set(userProfile::userName.name,newUserName))
                    somethingChanged = true
                }
                if(newSymbol != null && userProfile.symbolCode.get() != newSymbol)
                {
                    userProfile.symbolCode.set(newSymbol)
                    serverModel.updateProfile(userProfile._id.get()!!,Updates.set(userProfile::symbolCode.name,newSymbol))
                    somethingChanged = true
                }
                if(somethingChanged)
                {
                    session.send(Frame.Text(Messages.relayProfileUpdate(userProfile)))
                }
            }
        }
    }

    /**
     * Handle leader creating team
     *
     * @param params Parsed JSON message
     */
    private fun handleLeaderCreatingTeam(params: Map<String, Any?>)
    {
        val topTeamId = params["_id"] as? String

        thread {
            runBlocking {
                if(topTeamId == null)
                {
                    session.send(Frame.Text(Messages.respErr21("Could not extract teamId")))
                    return@runBlocking
                }
                if( serverModel.teamsSet.find { it.teamLead.get().toString() == userProfile._id.get().toString() }?._id?.get().toString() != topTeamId)
                {
                    session.send(Frame.Text(Messages.respErr21("User is not team leader of provided team")))
                    return@runBlocking
                }
                try {
                    terminalSh.teamCommandsHandler.createTeam(userProfile.userName.get(),params)
                }
                catch (e: MissingParameter)
                {
                    session.send(Frame.Text(Messages.respErr21(e.message!!)))
                }
            }
        }
    }

    /**
     * Method that handles team leader deleting his team
     *
     * @param params Parsed JSON message
     */
    private fun handleLeaderDeletingHisTeam(params: Map<String, Any?>)
    {
        coroutineRun {
            val teamId = getTeamId(params)

            if(teamId == null)
            {
                Logger.log(" Tried to delete team but there was no teamId",userProfile.userName.get())
                session.send(Frame.Text(Messages.teamDeletion("",true)))
                return@coroutineRun
            }
            val team = getTeamByIdAndCheckLead(teamId)

            if(team == null)
            {
                Logger.log("tried to delete not existing team or user is not leader",userProfile.userName.get())
                session.send(Frame.Text(Messages.teamDeletion("",true)))
                return@coroutineRun
            }
            Logger.log("going to delete team ${team.teamName.get()}",userProfile.userName.get())
            terminalSh.teamCommandsHandler.deleteTeam(team.teamName.get())
        }
    }

    /**
     * Method that handles addition or removal of users from team
     *
     * @param params Parsed JSON message
     */
    private fun handleMemberAddOrDel(params: Map<String, Any?>)
    {
        coroutineRun {
            Logger.log("user is going to update member list",userProfile.userName.get())
            val teamId = getTeamId(params) ?: return@coroutineRun
            val memberId = params["memberId"] as? String ?: return@coroutineRun
            val adding = params["adding"] as? Boolean ?: return@coroutineRun

            val team = getTeamByIdAndCheckLead(teamId) ?: return@coroutineRun

            Logger.log("All parameters where sent",userProfile.userName.get())

            val member = serverModel.userSet.find { it._id.get().toString() == memberId }?.userName?.get()
                ?: serverModel.teamsSet.find { it._id.get().toString() == memberId }?.teamName?.get()
                ?: return@coroutineRun

            Logger.log("adding: $adding member $member",userProfile.userName.get())
            terminalSh.teamCommandsHandler.addUsersOrTeamsToTeam(member,team.teamName.get(),adding)
        }
    }

    /**
     * Method that handles team leader change
     *
     * @param params Parsed JSON message
     */
    private fun handleTeamLeaderChange(params: Map<String, Any?>)
    {
        coroutineRun {
            Logger.log("updating team leader",userProfile.userName.get())
            val teamId = getTeamId(params) ?: return@coroutineRun
            val newLeaderId = params["newLeaderId"] as? String ?: return@coroutineRun

            val team = getTeamByIdAndCheckLead(teamId) ?: return@coroutineRun
            val userName = serverModel.userSet.find { it._id.get().toString() == newLeaderId }?.userName?.get() ?: return@coroutineRun

            Logger.log("all parameters where sent, updating team leader...",userProfile.userName.get())

            terminalSh.teamCommandsHandler.updateLeader(team.teamName.get(),userName)
        }
    }

    /**
     * Method that handles team's profile update
     *
     * @param params Parsed JSON message
     */
    private fun handleTeamUpdate(params: Map<String, Any?>)
    {
        coroutineRun {
            Logger.log("updating team",userProfile.userName.get())
            val teamId = getTeamId(params) ?: return@coroutineRun
            val newName = params["newName"] as? String ?: return@coroutineRun
            val newIcon = params["newIcon"] as? String ?: return@coroutineRun
            val team = getTeamByIdAndCheckLead(teamId) ?: return@coroutineRun


            Logger.log("all parameters where sent, updating team...",userProfile.userName.get())
            terminalSh.teamCommandsHandler.updateTeam(team.teamName.get(),newIcon,newName)
        }
    }

    /**
     *  Method that handles team leader's request to turn on/off location sharing to all users in team
     *
     * @param params Parsed JSON message
     */
    private fun handleAllTurnOnLocReq(params: Map<String, Any?>)
    {
        coroutineRun {
            Logger.log("trying to change team members location sharing",userProfile.userName.get())
            val teamId = getTeamId(params) ?: return@coroutineRun
            getTeamByIdAndCheckLead(teamId) ?: return@coroutineRun

            val on = params["on"] as? Boolean ?: return@coroutineRun

            Logger.log("all paremeters where sent, turning location share for team $teamId to $on",userProfile.userName.get())

            terminalSh.teamCommandsHandler.teamLocSh(teamId,on)
        }
    }

    /**
     * Method that handles team leader's request to toggle members location sharing
     *
     * @param params Parsed JSON message
     */
    private fun handleMemberLocationSharingReq(params: Map<String, Any?>)
    {
        coroutineRun {
            Logger.log("trying to change location share for one user",userProfile.userName.get())
            val teamId = getTeamId(params) ?: return@coroutineRun
            val userId = params["userId"] as? String ?: return@coroutineRun

            getTeamByIdAndCheckLead(teamId) ?: return@coroutineRun

            if (serverModel.userSet.find { it._id.get().toString() == userId }?.teamEntry?.get()
                    ?.find { it.toString() == teamId } == null
            ) {
                return@coroutineRun
            }

            Logger.log("got all parameters and given member $userId is in team $teamId, toggling location share...",userProfile.userName.get())

            serverModel.userSessionsSet.find { it.userId.get().toString() == userId }?.session?.get()
                ?.send(Frame.Text(Messages.requestLocShChange()))
        }
    }

    /**
     * Method that handles location update for a team
     *
     * @param params Parsed JSON message
     * @param wholeMessage Plain message that will be sent to other clients
     */
    private fun handleTeamLocUpdate(params: Map<String, Any?>,wholeMessage: String)
    {

        Logger.log("handling team location update",userProfile.userName.get())

        val id = params["_id"] as? String ?: return
        val lat = params["lat"]  as? Double
        val long = params["long"] as? Double

        val name = getTeamByIdAndCheckLead(id)?.teamName?.get() ?: return
        terminalSh.teamCommandsHandler.updateLocation(name,lat.toString(),long.toString())

        coroutineRun {
            serverModel.userSessionsSet.forEach {
              if(it.userId.get().toString() != userProfile._id.get().toString()) it.session.get()?.send(Frame.Text(wholeMessage))
            }
        }
    }

    /**
     * Method that fails transaction with given [id]
     *
     * @param id ID of transaction that will be failed
     */
    private fun failTransaction(id: String?)
    {
        val transaction = serverModel.tmpTransactionFiles.find { it.id == id } ?: return
        Logger.log("Transaction $id has failed and all files are going to be deleted",userProfile.userName.get())
        transaction.failTransactionNoWait()
    }

    /**
     * Method that handles point creation
     *
     * @param params Parsed JSON message
     */
    private fun handlePointCreation(params: Map<String, Any?>)
    {
        coroutineRun {
            val localId = params["serverId"] as? String ?: throw MissingParameter("NoId")

            try {

                val transaction = serverModel.tmpTransactionFiles.find { it.id == localId }

                var allFiles = ""
                transaction?.files?.forEach {
                    allFiles += (it + "\n")
                }

                Logger.log("Transaction state: ${transaction?.transactionState}\n" +
                        "   id: ${transaction?.id}\n" +
                        "   files: $allFiles",userProfile.userName.get())

                if( transaction != null && transaction.transactionState.get() != TransactionState.IN_PROGRESS)
                {
                    failTransaction(localId)
                    session.send(Frame.Text(Messages.pointCreationResult(false, reason = "WrongState")))
                    return@coroutineRun
                }
                val existingPoint = serverModel.pointSet.find { it._id.get().toString() == localId }

                var id: String?

                if(existingPoint == null)
                {
                    id = terminalSh.pointCommandsHandler.addPoint(params,userProfile._id.get().toString(),userProfile.userName.get())
                    if(id == null)
                    {
                        failTransaction(localId)
                        session.send(Frame.Text(Messages.pointCreationResult(false, reason = "Missing files")))
                        return@coroutineRun
                    }
                    Logger.log("Point with $id was created", userProfile.userName.get())
                } else
                {
                    id = existingPoint._id.get().toString()
                    val opRes = terminalSh.pointCommandsHandler.updatePoint(params,userProfile._id.get().toString())

                    if(!opRes)
                    {
                        failTransaction(localId)
                        session.send(Frame.Text(Messages.pointCreationResult(false, reason = "Missing files")))
                        return@coroutineRun
                    }
                    Logger.log("Point with $id was updated", userProfile.userName.get())
                }

                session.send(Frame.Text(Messages.pointCreationResult(true,id)))
                val entry = serverModel.pointSet.find { it._id.get().toString() == id }



                serverModel.userSessionsSet.forEach {

                    if(it.userId.get().toString() != userProfile._id.get().toString())
                        it.session.get()?.send(Frame.Text(Messages.pointEntry(entry!!)))
                }

                serverModel.tmpTransactionFiles.find { it.id == localId }?.finishTransaction() ?: return@coroutineRun

            }catch (e: MissingParameter)
            {
                Logger.log("message has missing parameter ${e.message}",userProfile.userName.get())
                failTransaction(localId)
                session.send(Frame.Text(Messages.pointCreationResult(false,localId, reason = e.message)))
                return@coroutineRun
            } catch (e: Exception)
            {
                Logger.log("general excetpion ${e.message}",userProfile.userName.get())
                e.printStackTrace()
                failTransaction(localId)
                session.send(Frame.Text(Messages.pointCreationResult(false,localId ,reason = e.message)))
                return@coroutineRun
            }
        }
    }

    /**
     * Method that handles point deletion
     *
     * @param params Parsed JSON message
     */
    private fun handlePointDeletion(params: Map<String, Any?>)
    {
        coroutineRun {
            Logger.log("trying to delete point",userProfile.userName.get())
            try {
                terminalSh.pointCommandsHandler.deletePoint(params, userProfile._id.get().toString())
            } catch (e: MissingParameter) {
              //  session.send(Frame.Text(Messages.pointCreationResult(false, reason = e.message)))
                Logger.log("Could not deletet point because there was missing parameter ${e.message}",userProfile.userName.get())

                return@coroutineRun
            }
        }
    }

    /**
     * Method that handles point update
     *
     * @param params Parsed JSON message
     */
    private fun handlePointUpdate(params: Map<String, Any?>)
    {
        coroutineRun {
            Logger.log("trying to update point",userProfile.userName.get())
            try {
                terminalSh.pointCommandsHandler.updatePoint(params,userProfile._id.get().toString())
            } catch (_:MissingParameter)
            {
                return@coroutineRun
            }
        }
    }

    /**
     * Method that handles point syncing request from client
     *
     * @param params
     */
    private fun handleSyncRequest(params: Map<String, Any?>)
    {
        coroutineRun {
            val owner = userProfile._id.get().toString()

            terminalSh.pointCommandsHandler.syncWithClient(params,owner)

            val otherUsersPoints = serverModel.pointSet.filter { it.ownerId.get().toString() != owner }
            if(otherUsersPoints.isNotEmpty()) session.send(Frame.Text(Messages.syncPointsMessage(otherUsersPoints.map { it._id.get().toString() })))
            else session.send(Frame.Text(Messages.syncPointsMessage(listOf())))
            otherUsersPoints.forEach {
                session.send(Frame.Text(Messages.pointEntry(it)))
            }
        }
    }

    /**
     * Method that handle failed transaction ack from user
     *
     * @param params Parsed JSON message
     */
    private fun handleFailedTransactionAcknowledgement(params: Map<String, Any?>)
    {
        val transactionId = params["transactionId"] as? String ?: return
        serverModel.tmpTransactionFiles.find { it.id == transactionId }?.failTransaction()
    }

    /**
     * Method that creates chat room
     *
     * @param params Parsed JSON message
     */
    private fun handleChatRoomCreation(params: Map<String, Any?>)
    {
        coroutineRun {
           val res = terminalSh.chatCommandsHandler.createChatRoom(params, userProfile._id.get().toString())

            if(!res)
            {
                session.send(Frame.Text(Messages.failedToCreateChatRoom(params["name"] as? String ?: "")))
            }
        }
    }

    /**
     * Method handles chat room deletion
     *
     * @param params Parsed JSON message
     */
    private fun handleChatRoomDeletion(params: Map<String, Any?>)
    {
        coroutineRun {
            terminalSh.chatCommandsHandler.deleteChatRoom(params,userProfile._id.get().toString())
        }
    }

    /**
     * Handle manage chat room users
     *
     * @param params Parsed JSON message
     */
    private fun handleManageChatRoomUsers(params: Map<String, Any?>)
    {
        coroutineRun {
            terminalSh.chatCommandsHandler.manageChatUsers(params,userProfile._id.get().toString())
        }
    }

    /**
     * Handle chat room owner change
     *
     * @param params Parsed JSON message
     */
    private fun handleChatRoomOwnerChange(params: Map<String, Any?>)
    {
        coroutineRun {
            terminalSh.chatCommandsHandler.changeChatOwner(params,userProfile._id.get().toString())
        }
    }

    /**
     * Method that handles message sent by user into chat room
     *
     * @param params Parsed JSON message
     */
    private fun handleSentMessage(params: Map<String, Any?>)
    {
        coroutineRun {
            val transactionId = params["transactionId"] as? String

            val transaction = serverModel.tmpTransactionFiles.find { it.id == transactionId }

            if(transaction != null && transaction.transactionState.get() != TransactionState.IN_PROGRESS)
            {
                transaction.failTransaction()

                return@coroutineRun
            }

            val res = terminalSh.chatCommandsHandler.sendMessage(params,userProfile._id.get().toString(),userProfile.userName.get(),userProfile.symbolCode.get())

            if(!res)
            {
                transaction?.failTransaction()

            } else
            {
                transaction?.finishTransaction()
            }
        }
    }

    /**
     * Method that handles messages fetch request
     *
     * @param params Parsed JSON message
     */
    private fun handleFetchMessages(params: Map<String, Any?>)
    {
        coroutineRun {
            terminalSh.chatCommandsHandler.fetchMessages(params, userProfile._id.get().toString())
        }
    }

    /**
     * Method that parses text frame and based on the message opcode invokes correct method. Second part of router
     *
     * @param frame Received text frame from client
     */
    private fun parseTextFrame(frame: Frame.Text)
    {
        val params = parseServerJson(frame.readText())
        when(getOpcode(params))
        {
            0 -> disconnectUserAndRelayIt(params)
            2 -> handleUserUpdatingProfile(params)
            3 -> relayUpdatedLocation(params)

            21 -> handleLeaderCreatingTeam(params)
            22 -> handleLeaderDeletingHisTeam(params)
            23 -> handleMemberAddOrDel(params)
            24 -> handleTeamLeaderChange(params)
            25 -> handleTeamUpdate(params)
            26 -> handleAllTurnOnLocReq(params)
            27 -> handleMemberLocationSharingReq(params)
            29 -> handleTeamLocUpdate(params,frame.readText())

            40 -> handlePointCreation(params)
            42 -> handlePointDeletion(params)
            43 -> handlePointUpdate(params)
            44 -> handleSyncRequest(params)

            49 -> handleFailedTransactionAcknowledgement(params)
            50 -> {}

            60 -> handleChatRoomCreation(params)
            61 -> handleChatRoomDeletion(params)
            62 -> handleManageChatRoomUsers(params)
            63 -> handleChatRoomOwnerChange(params)
            64 -> handleSentMessage(params)
            65 -> handleFetchMessages(params)
        }
    }

    /**
     * Method that parses close frame, sets all required attributes in model so that it indicates that user si no longer
     * connected to server
     *
     * @param frame Close frame
     */
    private fun parseCloseFrame(frame: Frame.Close) {
        userInitiatedClose = true
        if (!sentBye)
        {
            Logger.log("User with Id: ${userProfile._id} and username: ${userProfile.userName} is disconnecting",userProfile.userName.get())
            userProfile.location.set( null )
            connectionState = ConnectionState.NOT_CONNECTED
            handleDiscRelay()

            serverModel.tmpTransactionFiles.find { it.owner == userProfile._id.get().toString() }?.failTransaction()
        }
        userProfile.connected.set(false)
        Logger.log("${userProfile._id.get()} Close reason: ${frame.readReason()?.message}\nCode: ${frame.readReason()?.code}",userProfile.userName.get())
    }

    /**
     * Method that handles incoming frame from client
     *
     * @param frame Incoming frame
     */
    private fun handleFrame(frame: Frame)
    {
        when(frame)
        {
            is Frame.Text -> parseTextFrame(frame)
            is Frame.Close -> parseCloseFrame(frame)
            else -> {}
        }
    }

    /**
     * Method that check message opCode and type
     *
     * @param frame Incoing frame
     * @param type Expected type
     * @param opCode Expected opcode
     * @param errorLambda
     * @receiver Closure that will be invoked when error occurs
     * @return True on success else false
     */
    private suspend fun checkMsgTypeAndOpCode(frame: Frame, type: FrameType, opCode: Int, errorLambda: suspend () -> Unit): Boolean
    {
        if(frame.frameType != type)
        {
            errorLambda()
            return false
        }
        if( getOpcode(parseServerJson((frame as Frame.Text).readText())) != opCode )
        {
            errorLambda()
            return false
        }
        return true
    }

    /**
     * Method that creates new user profile and stores it in DB
     *
     * @param userName
     * @param symbolCode
     * @return True on success else false
     */
    private suspend fun createUserAndStoreItInDb(userName: String, symbolCode: String): Boolean
    {
        val newProfile = UserProfile(
            userName = AtomicReference(userName),
            symbolCode = AtomicReference(symbolCode),
            location = AtomicReference( null),
            connected = AtomicReference(true)
        )
        if( !serverModel.addNewUser(newProfile,session) )
        {
            Logger.log("User with username $userName already exists",userName,6)
            session.send(Frame.Text(Messages.userNameAlreadyInUse()))
            connectionState = ConnectionState.ERROR
            return false
        }
        val profile = serverModel.userSet.find { it.userName.get() == userName }
        if( profile == null)
        {
            Logger.log("Internal error occurred in <UserConnectedHandler.kt> createUserAndStoreItInDb line 71",userName,6)
            session.send(Frame.Text(Messages.bye("Internal server error")))
            session.send(Frame.Close(CloseReason(CloseReason.Codes.INTERNAL_ERROR,"Internal server error")))
            connectionState = ConnectionState.ERROR
            return false
        }
        val storedSession = serverModel.userSessionsSet.find{ it.userId.get() == profile._id.get()}
        if( storedSession == null )
        {
            Logger.log("Error: Session was not stored in server model",userName,6)
            session.send(Frame.Text(Messages.bye("Internal server error")))
            session.send(Frame.Close(CloseReason(CloseReason.Codes.INTERNAL_ERROR,"Internal server error")))
            connectionState = ConnectionState.ERROR
            return false
        }
        userSession = storedSession
        userProfile = profile
        connectionState = ConnectionState.CONNECTED
        session.send(Frame.Text(Messages.connectionSuccesFull(userProfile._id.toString(),userProfile.teamEntry.get())))
        return true
    }

    /**
     * Method that checks connecting user in database and checks if user with same ID doesn't have active connection
     *
     * @param id Connecting user's ID
     * @param userName His username
     * @param symbolCode His symbol code
     * @return True on success else false
     */
    private suspend fun findAndCheckUserById(id: ObjectId, userName: String, symbolCode: String): Boolean
    {
        val profile = serverModel.checkAndAddReconectedUser(
            UserProfile(
                AtomicReference(id),
                AtomicReference(userName),
                AtomicReference(symbolCode),
                AtomicReference(null),AtomicReference(true)
            ),
            session
        )

        if(profile == null)
        {

            session.send(Frame.Text(Messages.userNameAlreadyInUse()))
            connectionState = ConnectionState.ERROR
            return false
        }

        val storedSession = serverModel.userSessionsSet.find{ it.userId.get() == profile._id.get()}

        if( storedSession == null)
        {
            session.send(Frame.Text(Messages.userNameAlreadyInUse()))
            connectionState = ConnectionState.ERROR
            return false
        }

        userSession = storedSession
        userProfile = profile
        session.send(Frame.Text(Messages.connectionSuccesFull(profile._id.toString(),userProfile.teamEntry.get())))
        connectionState = ConnectionState.CONNECTED
        return true
    }

    /**
     * Method that checks if user profile is valid and stores it DB
     *
     * @param params Parsed JSON message
     * @return True on success else false
     */
    private suspend fun checkUserProfileAndStoreIt(params: Map<String, Any?>): Boolean
    {

        val userName = params["userName"] as? String
        val symbolCode = params["symbolCode"] as? String
        val _id = params["_id"] as? String

        if(userName == null || symbolCode == null)
        {
            Logger.log("Username or symbol code was null... Terminating connection","unknown",6)
            session.send(Frame.Text(Messages.bye("Username or symbol code was null")))
            session.send(Frame.Close(CloseReason(CloseReason.Codes.VIOLATED_POLICY,"Username or symbol code was null")))
            connectionState = ConnectionState.ERROR
            return false
        }

        return if(_id == null || _id == "") {
            createUserAndStoreItInDb(userName,symbolCode)
        } else {
            findAndCheckUserById(ObjectId(_id),userName,symbolCode)
        }
    }

    /**
     * Method that handles initial handshake between client and server. It also stores user profile in DB. If error
     * occurs it will set [connectionState] to [ConnectionState.ERROR]. If everythign goes well then it will set [connectionState]
     * to [ConnectionState.CONNECTED]
     *
     */
    private suspend fun initSequence()
    {
        var frame = session.incoming.receive()

        if( !checkMsgTypeAndOpCode(frame,FrameType.TEXT,-1){
                Logger.log("Initial frame was not text or had wrong opCode","unknown",6)
                session.send(Frame.Close(CloseReason(CloseReason.Codes.VIOLATED_POLICY,"Wrong initial message")))
                connectionState = ConnectionState.ERROR
            }) return

        session.send(Frame.Text(Messages.helloThere()))
        connectionState = ConnectionState.NEGOTIATING
        Logger.log("Negotiating","unknown",1)

        frame = session.incoming.receive()

        if (!checkMsgTypeAndOpCode(frame, FrameType.TEXT, 1) {
            Logger.log("Frame was not text frame or had wrong opcode","unknown",6)
                session.send(
                    Frame.Close(
                        CloseReason(
                            CloseReason.Codes.VIOLATED_POLICY,
                            "Incorrect message sequence"
                        )
                    )
                )
                connectionState = ConnectionState.ERROR
        }) return
        Logger.log("Creating user profile","unknown",1)
        //TODO renegotiation request is sent in create user function
        //TODO connection state changes in create users functions
        val params = parseServerJson((frame as Frame.Text).readText())
        checkUserProfileAndStoreIt(params)
        //connectionState = ConnectionState.CONNECTED
    }

    /**
     * Method that sends all user profiles of users that are connected, teams and chat rooms of which user is member to user
     *
     */
    private suspend fun sendProfilesAndPoints()
    {

        Logger.log("Sending profiles and chats to user",userProfile.userName.get())
        serverModel.userSet.forEach { profile ->
            if(profile.connected.get() && (profile._id.get().toString() != userProfile._id.get().toString())) session.send(Frame.Text(Messages.sendUserProfile(profile)))
        }
        serverModel.teamsSet.forEach {
            session.send(Frame.Text(Messages.teamProfile(it)))
        }

        serverModel.chatDb.getAllChats()?.forEach {
            if(it.members.find { id -> id == userProfile._id.get().toString() } != null)
            {
                session.send(Frame.Text(Messages.chatRoomCreation(it)))
            }
        }
        Logger.log("Finished sending users and chats to user",userProfile.userName.get())
    }

    /**
     * Method that sends all other clients this user's profile
     */
    private suspend fun notifyOtherUsers()
    {
        serverModel.userSessionsSet.forEach {
            if(it.userId.get() != userProfile._id.get()) it.session.get()?.send(Frame.Text(Messages.sendUserProfile(userProfile)))
        }
    }

    /**
     * Method that handles whole users connection from initial state to its closure by either client or server.
     *
     */
    suspend fun handleConnection()
    {
       //no
        try{
            initSequence()
            if(connectionState != ConnectionState.CONNECTED) return
            sendProfilesAndPoints()
            notifyOtherUsers()

            Logger.log("User ${userProfile.userName.get()} with id ${userProfile._id.get().toString()} has connected to server.",userProfile.userName.get())

            for (frame in session.incoming)
            {
                handleFrame(frame)
            }

            Logger.log("User ${userProfile.userName.get()} with id ${userProfile._id.get().toString()}" +
                    "has disconnected from server\n/*session.closeReason.getCompleted().toString()*/",userProfile.userName.get(),1)

            userSession.session.set(null)
            userProfile.connected.set(false)
            serverModel.tmpTransactionFiles.find { it.owner == userProfile._id.get().toString() }?.failTransaction()
            if(!userInitiatedClose)
            {
                handleDiscRelay()
            }
        } catch (e: Exception)
        {
            //e.printStackTrace()
            Logger.log("Error: ${e.message}",userProfile.userName.get(),9)
            if(session.isActive)
            {
                try {
                    //sending special close reason to client in case this is bug when client doesn't respond to ping
                    //message even though he is connected. If this is the case then this message should indicate
                    //to him that he should reconnect
                    if(e.message == "Ping timeout")
                    {
                        session.close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER,"${e.message}"))
                    }else
                    {
                        session.send(Frame.Text(Messages.bye("Internal server error: ${e.message}")))
                        session.send(Frame.Close(CloseReason(CloseReason.Codes.INTERNAL_ERROR,"Internal server error: ${e.message}")))
                    }

                } catch (e: Exception)
                {
                    Logger.log("Error: ${e.message}",userProfile.userName.get())
                }
            }
            session.close()
            userSession.session.set(null)
            userProfile.connected.set(false)

            serverModel.userSessionsSet.forEach {
                it?.session?.get()?.send(Frame.Text(Messages.userDisconnected(userProfile._id.get()!!.toHexString())))
            }

            serverModel.tmpTransactionFiles.find { it.owner == userProfile._id.get().toString() }?.failTransaction()
        } catch (e: IOException)
        {

        }
    }
}

