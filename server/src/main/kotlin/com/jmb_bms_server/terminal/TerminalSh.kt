package com.jmb_bms_server.terminal

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jmb_bms_server.*
import com.jmb_bms_server.MessagesJsons.Messages
import com.jmb_bms_server.data.UserProfile
import com.jmb_bms_server.data.UserSession
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.lang.NumberFormatException
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import kotlin.concurrent.thread

class TerminalSh(private val model: TmpServerModel, ) {

    private lateinit var server: NettyApplicationEngine

    private fun getCommand(line: String) = line.substring(0, if(line.indexOf(' ') != -1) line.indexOf(' ') else line.length )

    private fun getParameters(line: String): List<String> = line.split(Regex(" +"))

    lateinit var teamCommandsHandler : TeamCommandsHandler

    private lateinit var userCommandsHandler : UserCommandsHandler

    private var run = true

    private var debug = false

    private fun init(server: NettyApplicationEngine)
    {
        this.server = server
        teamCommandsHandler = TeamCommandsHandler(model, server)
        userCommandsHandler = UserCommandsHandler(model, server)
    }



    private fun printHelp()
    {
        println("Usage: command [parameters]\nexit -> close all connections and turn off server\n" +
                "help -> prints available commands\ncreateUser [userName] [symbolString] [[lat] [long] ? ]  -> creates new user and adds it to database\n" +
                "deleteUser [userName] -> terminates user connection if there is some and deletes him from database\n" +
                "printUser [userName] -> prints user details\nprintAllUsers -> prints user detail about all users\n" +
                "updateUser [username] [key value [key value [...]]] -> updates user fields with same key\n" +
                "termConnection [userName] -> terminates user connection but leaves his entry in database\n" +
                "printAllUsers -> prints all user profiles and session data\n" +

                "createTeam [teamName] [teamSymbol ? def] [team leader userName] [[lat] [long] ? ] -> creates team with name, icon, and team leader\n" +
                "deleteTeam [teamName] -> deletes team with given name and updates user profiles\n" +
                "updateTeam [teamName] [key value [key value [...]]] -> updates values identified by keys with values in team with teamName\n" +
                "addUsersToTeam [teamName] [userName [userName [...]]] -> adds users to teams\n" +
                "removeUsersFromTeam [teamName] [userName [userName [...]]] -> removes users from team\n" +
                "teamLocSh [teamName] [on ? off] -> turns on/off location sharing to all users within team except team lead\n" +
                "printAllTeams -> prints info about all teams\n" +
                "printTeam [teamName] -> prints info about team identified by teamName\n" +
                "debug [on ? off] -> turns on/off debug commands\n" +

                "\nDebug commands:\n"+
                "turnOnLocUp [userName] [on ? off] -> turns on users location sharing remotely \n"+
                "simTurnOffLocSh [userName] -> simulates that user stopped sharing location\n" +
                "simMoveRandom [userName] [how many moves] [interval] -> moves user +-2 meters at random, informs where user was moved")
    }
    fun parseServerJson(json: String): Map<String, Any?>
    {
        val gson = Gson()
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        val map: Map<String, Any?> = gson.fromJson(json,type)
        return map
    }

    fun getOpcode(map: Map<String, Any?>): Int? {
        return map["opCode"] as? Int
    }

    private fun parseParams(line: String, numsOfParams: Int, failCondition: ((expected: Int,real: Int)-> Boolean)) : List<String>?
    {
        val params = getParameters(line)
        return if(failCondition(numsOfParams,params.size)) null
               else params
    }

    private fun exit()
    {
        runBlocking {
            model.userSessionsSet.forEach{
                it.session.get()?.send(Frame.Text(Messages.bye("Server is shutting down on command")))
                it.session.get()?.send(Frame.Close(CloseReason(CloseReason.Codes.NORMAL,"Server is shutting down on command")))
                it.session.set(null)
            }
        }
        println("Gracefully terminated all connections...")
        server.stop(1500,1500)
        run = false
        println("Server stopped... Exiting program")
    }

    private fun c(params: List<String>?){

        val a = listOf(listOf("","pepe","PICA"),listOf("","vojto","JEBAL"),listOf("","Mojmoir","NEMAL"),listOf("","dezo","JEZO"),listOf("","pipik","JEBAL"))

        a.forEach {
            userCommandsHandler.createUser(it)
        }

        val memberIDs = mutableListOf<String>()

        a.forEach { list ->
            memberIDs.add(model.userSet.find { it.userName.get() == list[1] }?._id?.get().toString())
        }

        teamCommandsHandler.createTeam(listOf("","A","def","pepe"))

        val map = mutableMapOf<String, Any?>()
        arrayOf(
            Pair("teamName","Jeblinky"),
            Pair("teamIcon","def"),
            Pair("topTeamId",model.teamsSet.find { it.teamName.get() == "A" }?._id?.get().toString())
        ).forEach {
            map[it.first] = it.second
        }

        map["teamMembers"] = memberIDs

        println(map["teamName"].toString() + " " + map["teamIcon"].toString() + " " + map["teamMembers"] )

        teamCommandsHandler.createTeam("pepe",map)

        teamCommandsHandler.printAllTeams()
        userCommandsHandler.printAllUsers()
    }

    private fun debug(params: List<String>?)
    {
        if(params == null)
        {
            println("Incorrect number of parameters for updateUser command. Expecting 1 ")
            return
        }
        when(params[1])
        {
            "on" -> debug = true
            "off" -> debug = false
            else -> println("Unknown option ${params[1]}")
        }
    }

    private fun parseCommand(line: String)
    {
        val command = getCommand(line)

        if(!debug) {
            when (command) {
                "exit" -> exit()
                "help" -> printHelp()
                "debug" -> debug(parseParams(line, 2) { expected, real -> real != expected })
                "createUser" -> userCommandsHandler.createUser(parseParams(line, 0) { _, real -> real != 5 && real != 3 })
                "deleteUser" -> userCommandsHandler.deleteUser(parseParams(line, 2) { expected, real -> expected != real })
                "printUser" -> userCommandsHandler.printAllUserInfo(parseParams(line, 2) { expected, real -> real != expected })
                "updateUser" -> userCommandsHandler.updateUser(parseParams(line, 3) { expected, real -> real < expected })
                "printAllUsers" -> userCommandsHandler.printAllUsers()
                "termConnection" -> userCommandsHandler.termConnection(parseParams(line, 2) { expected, real -> real != expected })
                "createTeam" -> teamCommandsHandler.createTeam(parseParams(line,0){_ ,real -> real != 6 && real != 4 })
                "deleteTeam" -> teamCommandsHandler.deleteTeam(parseParams(line,2){ expected, real -> real != expected })
                "updateTeam" -> teamCommandsHandler.updateTeam(parseParams(line,3){ expected ,real -> real < expected})
                "addUsersToTeam" -> teamCommandsHandler.addUsersOrTeamsToTeam(parseParams(line,3){ expected, real -> real < expected },true)
                "removeUsersFromTeam" -> teamCommandsHandler.addUsersOrTeamsToTeam(parseParams(line,3){ expected, real -> real < expected },false)
                "printAllTeams" -> teamCommandsHandler.printAllTeams()
                "printTeam" -> teamCommandsHandler.printTeam(parseParams(line,2){expected, real -> expected != real })
                else -> println("Unknown command... Enter help to list available commands")
            }
        } else
        {
            when(command)
            {
                "exit" -> exit()
                "help" -> printHelp()
                "debug" -> debug(parseParams(line, 2) { expected, real -> real != expected })
                "createUser" -> userCommandsHandler.createUser(parseParams(line, 0) { _, real -> real != 5 && real != 3 })
                "deleteUser" -> userCommandsHandler.deleteUser(parseParams(line, 2) { expected, real -> expected != real })
                "printUser" -> userCommandsHandler.printAllUserInfo(parseParams(line, 2) { expected, real -> real != expected })
                "updateUser" -> userCommandsHandler.updateUser(parseParams(line, 3) { expected, real -> real < expected })
                "printAllUsers" -> userCommandsHandler.printAllUsers()
                "termConnection" -> userCommandsHandler.termConnection(parseParams(line, 2) { expected, real -> real != expected })
                "simTurnOffLocSh" -> userCommandsHandler.simTurnOffLocSh(parseParams(line, 2) { expected, real -> real != expected })
                "simMoveRandom" -> userCommandsHandler.simMoveRandom(parseParams(line, 4) { expected, real -> real != expected })
                "c" -> c(parseParams(line, 0) { expected, real -> true })
                "createTeam" -> teamCommandsHandler.createTeam(parseParams(line,0){_ ,real -> real != 5 && real != 3 })
                "deleteTeam" -> teamCommandsHandler.deleteTeam(parseParams(line,2){ expected, real -> real != expected })
                "updateTeam" -> teamCommandsHandler.updateTeam(parseParams(line,3){ expected ,real -> real < expected})
                "addUsersToTeam" -> teamCommandsHandler.addUsersOrTeamsToTeam(parseParams(line,3){ expected, real -> real < expected }, true)
                "removeUsersFromTeam" -> teamCommandsHandler.addUsersOrTeamsToTeam(parseParams(line,3){ expected, real -> real < expected },false)
                "printAllTeams" -> teamCommandsHandler.printAllTeams()
                "printTeam" -> teamCommandsHandler.printTeam(parseParams(line,2){expected, real -> expected != real })
                else -> println("Unknown command... Enter help to list available commands")
            }
        }
    }

    private fun run()
    {
        println("Entering program shell... Enter help to list available commands")
        thread {
            while (run)
            {
                print("admin -> ")
                val line = readln()
                parseCommand(line)
            }
            lock.lock()
            appRun = false
            lock.unlock()
        }
    }


    fun startApplication()
    {
        var res: NettyApplicationEngine

        res = embeddedServer(Netty, port = 8080, host = "192.168.10.142"){
            module(model,this@TerminalSh)
        }.start(wait = false)

        thread {   //GlobalScope.launch {
            this.init(res)
            this.run()
        }

        while (true){
            runBlocking {
                delay(10000)
            }

            lock.lock()
            if(!appRun)break
            lock.unlock()
        }
    }
}

