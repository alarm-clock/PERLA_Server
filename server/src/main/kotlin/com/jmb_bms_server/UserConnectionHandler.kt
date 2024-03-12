package com.jmb_bms_server

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jmb_bms_server.MessagesJsons.Messages
import com.jmb_bms_server.data.TeamEntry
import com.jmb_bms_server.data.UserProfile
import com.jmb_bms_server.data.UserSession
import com.jmb_bms_server.terminal.TerminalSh
import com.mongodb.client.model.Updates
import io.ktor.websocket.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.bson.types.ObjectId
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class UserConnectionHandler(val session: DefaultWebSocketSession, val serverModel: TmpServerModel, private val terminalSh: TerminalSh) {

    private var connectionState = ConnectionState.NOT_CONNECTED

    private lateinit var userProfile: UserProfile

    private lateinit var userSession: UserSession

    private var sentBye = false

    private var userInitiatedClose = false

    private fun parseServerJson(json: String): Map<String, Any?>
    {
        val gson = Gson()
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        val map: Map<String, Any?> = gson.fromJson(json,type)
        return map
    }


    private fun getOpcode(map: Map<String, Any?>): Double? {
        return map["opCode"] as? Double
    }

    private fun getTeamId(map: Map<String, Any?>): String? = map["_id"] as? String

    private fun threadRun(code: suspend () -> Unit)
    {
        thread {
            runBlocking {
                code()
            }
        }
    }

    private fun relayUpdatedLocation(params: Map<String, Any?>)
    {
        val lat = params["lat"] as? Double
        val long = params["long"] as? Double

        if( lat == null || long == null)
        {
            userProfile.location.set( null )

        } else
        {
            if(userProfile.location.get() == null) userProfile.location.set(Location(AtomicReference( 0.0),AtomicReference(0.0)))
            userProfile.location.get()!!.lat.set(lat)
            userProfile.location.get()!!.long.set(long)
        }
        thread {
            runBlocking{
                serverModel.updateProfile(userProfile._id.get()!!,Updates.set(userProfile::location.name,userProfile.location.get()?.getStorableLocation()))
                serverModel.userSessionsSet.forEach { otherClient ->
                    if(otherClient.userId.get() != userProfile._id.get())
                    {
                        println(userProfile.location.get())
                        otherClient.session.get()?.send(Frame.Text(Messages.sendLocationUpdate(userProfile)))
                    }
                }
            }
        }
    }

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

    private fun disconnectUserAndRelayIt(params: Map<String, Any?>)
    {
        userInitiatedClose = true
        println("User with Id: ${userProfile._id} and username: ${userProfile.userName} is disconnecting\nReason: ${params["reason"] as? String ?: "none"}")
        userProfile.connected.set(false)
        userSession.session.set(null)
        connectionState = ConnectionState.NOT_CONNECTED
        sentBye = true
        handleDiscRelay()
    }

    private fun getTeamByIdAndCheckLead(teamId: String): TeamEntry?
    {
        val team = serverModel.teamsSet.find { it._id.get().toString() == teamId } ?: return null
        if( team.teamLead.get().toString() != userProfile._id.get().toString())
        {
            return null
        }
        return team
    }


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
                val res = terminalSh.teamCommandsHandler.createTeam(userProfile.userName.get(),params)
                if( !res.first )
                {
                    session.send(Frame.Text(Messages.respErr21(res.second)))
                }
            }
        }
    }

    private fun handleLeaderDeletingHisTeam(params: Map<String, Any?>)
    {
        threadRun {
            val teamId = getTeamId(params)

            if(teamId == null)
            {
                session.send(Frame.Text(Messages.teamDeletion("",true)))
                return@threadRun
            }
            val team = getTeamByIdAndCheckLead(teamId)

            if(team == null)
            {
                session.send(Frame.Text(Messages.teamDeletion("",true)))
                return@threadRun
            }
            terminalSh.teamCommandsHandler.deleteTeam(team.teamName.get())
        }
    }

    private fun handleMemberAddOrDel(params: Map<String, Any?>)
    {
        threadRun {
            val teamId = getTeamId(params) ?: return@threadRun
            val memberId = params["memberId"] as? String ?: return@threadRun
            val adding = params["adding"] as? Boolean ?: return@threadRun

            val team = getTeamByIdAndCheckLead(teamId) ?: return@threadRun

            val member = serverModel.userSet.find { it._id.get().toString() == memberId }?.userName?.get()
                ?: serverModel.teamsSet.find { it._id.get().toString() == memberId }?.teamName?.get()
                ?: return@threadRun

            terminalSh.teamCommandsHandler.addUsersOrTeamsToTeam(member,team.teamName.get(),adding)
        }
    }

    private fun handleTeamLeaderChange(params: Map<String, Any?>)
    {
        threadRun {
            val teamId = getTeamId(params) ?: return@threadRun
            val newLeaderId = params["newLeaderId"] as? String ?: return@threadRun

            val team = getTeamByIdAndCheckLead(teamId) ?: return@threadRun
            val userName = serverModel.userSet.find { it._id.get().toString() == newLeaderId }?.userName?.get() ?: return@threadRun
            terminalSh.teamCommandsHandler.updateLeader(team.teamName.get(),userName)
        }
    }

    private fun handleTeamUpdate(params: Map<String, Any?>)
    {
        threadRun {
            val teamId = getTeamId(params) ?: return@threadRun
            val newName = params["newName"] as? String ?: return@threadRun
            val newIcon = params["newIcon"] as? String ?: return@threadRun

            val team = getTeamByIdAndCheckLead(teamId) ?: return@threadRun

            terminalSh.teamCommandsHandler.updateTeam(team.teamName.get(),newIcon,newName)
        }
    }

    private fun handleAllTurnOnLocReq(params: Map<String, Any?>)
    {
        threadRun {
            val teamId = getTeamId(params) ?: return@threadRun
            val teamName = getTeamByIdAndCheckLead(teamId)?.teamName?.get() ?: return@threadRun

            val on = params["on"] as? Boolean ?: return@threadRun

            terminalSh.teamCommandsHandler.teamLocSh(teamName,on)
        }
    }

    private fun handleMemberLocationSharingReq(params: Map<String, Any?>)
    {
        threadRun {
            val teamId = getTeamId(params) ?: return@threadRun
            val userId = params["userId"] as? String ?: return@threadRun

            getTeamByIdAndCheckLead(teamId) ?: return@threadRun

            if (serverModel.userSet.find { it._id.get().toString() == userId }?.teamEntry?.get()
                    ?.find { it.toString() == teamId } == null
            ) {
                return@threadRun
            }
            serverModel.userSessionsSet.find { it.userId.get().toString() == userId }?.session?.get()
                ?.send(Frame.Text(Messages.requestLocShChange()))
        }
    }

    private fun handleTeamLocUpdate(params: Map<String, Any?>,wholeMessage: String)
    {
        //TODO add location storing for team and indication that it is working
        threadRun {
            serverModel.userSessionsSet.forEach {
                it.session.get()?.send(Frame.Text(wholeMessage))
            }
        }
    }

    private fun parseTextFrame(frame: Frame.Text)
    {
        val params = parseServerJson(frame.readText())
        when(getOpcode(params))
        {
            0.0 -> disconnectUserAndRelayIt(params)
            2.0 -> handleUserUpdatingProfile(params)
            3.0 -> relayUpdatedLocation(params)

            21.0 -> handleLeaderCreatingTeam(params)
            22.0 -> handleLeaderDeletingHisTeam(params)
            23.0 -> handleMemberAddOrDel(params)
            24.0 -> handleTeamLeaderChange(params)
            25.0 -> handleTeamUpdate(params)
            26.0 -> handleAllTurnOnLocReq(params)
            27.0 -> handleMemberLocationSharingReq(params)
            29.0 -> handleTeamLocUpdate(params,frame.toString())
        }
    }

    private fun parseCloseFrame(frame: Frame.Close) {
        userInitiatedClose = true
        if (!sentBye)
        {
            println("User with Id: ${userProfile._id} and username: ${userProfile.userName} is disconnecting")
            userProfile.location.set( null )
            connectionState = ConnectionState.NOT_CONNECTED
            handleDiscRelay()
        }
        userProfile.connected.set(false)
        println("${userProfile._id.get()} Close reason: ${frame.readReason()?.message}\nCode: ${frame.readReason()?.code}")
    }

    private fun handleFrame(frame: Frame)
    {
        when(frame)
        {
            is Frame.Text -> parseTextFrame(frame)
            is Frame.Close -> parseCloseFrame(frame)
            else -> {}
        }
    }

    private suspend fun checkMsgTypeAndOpCode(frame: Frame, type: FrameType, opCode: Int, errorLambda: suspend () -> Unit): Boolean
    {
        if(frame.frameType != type)
        {
            errorLambda()
            return false
        }
        if( getOpcode(parseServerJson((frame as Frame.Text).readText())) != opCode.toDouble() )
        {
            errorLambda()
            return false
        }
        return true
    }

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
            println("User with username $userName already exists")
            session.send(Frame.Text(Messages.userNameAlreadyInUse()))
            connectionState = ConnectionState.ERROR
            return false
        }
        val profile = serverModel.userSet.find { it.userName.get() == userName }
        if( profile == null)
        {
            println("Internal error occurred in <UserConnectedHandler.kt> createUserAndStoreItInDb line 71")
            session.send(Frame.Text(Messages.bye("Internal server error")))
            session.send(Frame.Close(CloseReason(CloseReason.Codes.INTERNAL_ERROR,"Internal server error")))
            connectionState = ConnectionState.ERROR
            return false
        }
        val storedSession = serverModel.userSessionsSet.find{ it.userId.get() == profile._id.get()}
        if( storedSession == null )
        {
            println("Error: Session was not stored in server model")
            session.send(Frame.Text(Messages.bye("Internal server error")))
            session.send(Frame.Close(CloseReason(CloseReason.Codes.INTERNAL_ERROR,"Internal server error")))
            connectionState = ConnectionState.ERROR
            return false
        }
        userSession = storedSession
        userProfile = profile
        connectionState = ConnectionState.CONNECTED
        session.send(Frame.Text(Messages.connectionSuccesFull(userProfile._id.toString())))
        return true
    }

    private suspend fun findAndCheckUserById(id: ObjectId, userName: String, symbolCode: String): Boolean
    {
        val profile =  serverModel.checkAndAddReconectedUser(
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

        println("Checking by id")
        userSession = storedSession
        userProfile = profile
        session.send(Frame.Text(Messages.connectionSuccesFull(profile._id.toString())))
        connectionState = ConnectionState.CONNECTED
        return true
    }

    private suspend fun checkUserProfileAndStoreIt(params: Map<String, Any?>): Boolean
    {

        val userName = params["userName"] as? String
        val symbolCode = params["symbolCode"] as? String
        val _id = params["_id"] as? String

        if(userName == null || symbolCode == null)
        {
            println("Username or symbol code was null... Terminating connection")
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


    private suspend fun initSequence()
    {
        var frame = session.incoming.receive()

        if( !checkMsgTypeAndOpCode(frame,FrameType.TEXT,-1){
                println("Initial frame was not text or had wrong opCode")
                session.send(Frame.Close(CloseReason(CloseReason.Codes.VIOLATED_POLICY,"Wrong initial message")))
                connectionState = ConnectionState.ERROR
            }) return

        session.send(Frame.Text(Messages.helloThere()))
        connectionState = ConnectionState.NEGOTIATING
        println("Negotiating")

        frame = session.incoming.receive()

        if (!checkMsgTypeAndOpCode(frame, FrameType.TEXT, 1) {
            println("Frame was not text frame or had wrong opcode")
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
        println("Creating user profile")
        //TODO renegotiation request is sent in create user function
        //TODO connection state changes in create users functions
        val params = parseServerJson((frame as Frame.Text).readText())
        checkUserProfileAndStoreIt(params)
        //connectionState = ConnectionState.CONNECTED
    }

    private suspend fun sendProfilesAndPoints()
    {
        //TODO add points here

        serverModel.userSet.forEach { profile ->
            if(profile.connected.get() && (profile._id.get().toString() != userProfile._id.get().toString())) session.send(Frame.Text(Messages.sendUserProfile(profile)))
        }
        serverModel.teamsSet.forEach {
            session.send(Frame.Text(Messages.teamProfile(it)))
        }
    }
    private suspend fun notifyOtherUsers()
    {
        serverModel.userSessionsSet.forEach {
            if(it.userId.get() != userProfile._id.get()) it.session.get()?.send(Frame.Text(Messages.sendUserProfile(userProfile)))
        }
    }

    //vyuzivanie 15
    //popis stavby rodinny dom
    //druh stavby 10
    //dnesny tum
    suspend fun handleConnection()
    {
       //no
        try{
            initSequence()
            if(connectionState != ConnectionState.CONNECTED) return
            sendProfilesAndPoints()
            println("Notifying other users")
            notifyOtherUsers()
            println("Done notifying other users")

            for (frame in session.incoming)
            {
                handleFrame(frame)
            }
            println("Got from session.incoming")
            userSession.session.set(null)
            userProfile.connected.set(false)
            if(!userInitiatedClose)
            {
                handleDiscRelay()
            }
        } catch (e: Exception)
        {
            println("Error: " + e.message + "\n" + e.cause?.message)
            if(session.isActive)
            {
                try {
                    session.send(Frame.Text(Messages.bye("Internal server error: ${e.message}")))
                    session.send(Frame.Close(CloseReason(CloseReason.Codes.INTERNAL_ERROR,"Internal server error: ${e.message}")))
                } catch (e: Exception)
                {
                    println("Error: ${e.message}")
                }
            }
            userSession.session.set(null)
            userProfile.connected.set(false)
        }
    }
}

