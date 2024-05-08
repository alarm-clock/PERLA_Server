/**
 * @file: TerminalSh.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing TerminalSh class
 */
package com.jmb_bms_server.terminal

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jmb_bms_server.*
import com.jmb_bms_server.MessagesJsons.Messages
import com.jmb_bms_server.utils.GetJarPath
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlin.concurrent.thread

/**
 * Class that runs terminal input, parses commands and opens server to outside connections
 *
 * @property model Server model
 * @constructor Initializes command handlers with [model]
 */
class TerminalSh(private val model: TmpServerModel ) {

    private lateinit var server: NettyApplicationEngine

    lateinit var teamCommandsHandler : TeamCommandsHandler
    lateinit var pointCommandsHandler: PointCommandsHandler
    lateinit var chatCommandsHandler: ChatCommandsHandler

    private lateinit var userCommandsHandler : UserCommandsHandler

    private var run = true

    private var debug = false

    private fun init(server: NettyApplicationEngine)
    {
        this.server = server
        teamCommandsHandler = TeamCommandsHandler(model, server)
        userCommandsHandler = UserCommandsHandler(model, server)
        pointCommandsHandler = PointCommandsHandler(model)
        chatCommandsHandler = ChatCommandsHandler(model)
    }

    /**
     * Method that extracts command from string. Command is first continuous sequence of characters ended by whitespace character.
     *
     * @param line Line from which command will be extracted
     * @return String with command
     */
    private fun getCommand(line: String) = line.substring(0, if(line.indexOf(' ') != -1) line.indexOf(' ') else line.length )

    /**
     * Method that splits string into [List]<[String]> with whitespace characters as delimiters.
     *
     * @param line Line that will be split
     * @return [List]<[String]> created from [line]
     */
    private fun getParameters(line: String): List<String> = line.split(Regex(" +"))

    /**
     * Method that prints help on terminal
     *
     */
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

                "printAllPoints -> prints all points\n" +
                "printPoint [pointID] -> prints point identified by pointID\n" +
                "deletePoint [pointID] -> deletes point identified by pointID\n" +
                "updatePoint [pointID] [key value [key value [...]]] -> updates point's attributes identified by key with values\n" +

                "createChat [chat_name] [userID [userID [... ?] ?] ?] -> create chat room with chat_name and users\n" +
                "deleteChat [chatRoomID] -> deletes chat room identified by chatRoomId\n" +
                "manageMembers [chatRoomId] [true ? false] [userId [userId [...]]] -> method that adds (true) or removes (false) users to/from chat room\n" +
                "sendMessage [chatRoomName] [body] [of] [message] [...] ... -> command that sends message to chat room where message begins by first word after chat room name\n"+
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

    /**
     * Method that parses string from input into [List]<[String]> and checks if command has right number of arguments
     *
     * @param line String that will be parsed
     * @param numsOfParams Expected number of parameters
     * @param failCondition Closure that takes two parameters: expected -> expected number of parameters, real -> real number of parameters,
     * and should return true if number of arguments doesn't match number of expected arguments
     * @return String parsed into [List]<[String]> or null if number of arguments is incorrect
     */
    private fun parseParams(line: String, numsOfParams: Int, failCondition: ((expected: Int,real: Int)-> Boolean)) : List<String>?
    {
        val params = getParameters(line)
        return if(failCondition(numsOfParams,params.size)) null
               else params
    }

    /**
     * Method that sends close messages to all clients, then stops server and sets [run] to false.
     *
     */
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
       // Logger.log("test test test", 5)
    }
    fun generateUsers()
    {
        for(i in 1..150)
        {
            userCommandsHandler.createUser(listOf("",i.toString(),"SFGPUC---------"))
        }
    }
    fun allConnected()
    {
        model.userSet.forEach {
            it.connected.set(true)
        }
    }
    fun unConnectDelta()
    {
        model.userSet.find { it.userName.get() == "Delta" }?.connected?.set(false)
        model.userSet.find { it.userName.get() == "Jozef" }?.connected?.set(false)
    }

    /**
     * Method that turns on/off debug mode in terminal. Debug mode adds few debug commands.
     * @param params Parsed command line command in [List]
     */
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

    private fun t()
    {
        userCommandsHandler.simMoveRandom(listOf("","1","360","10000"))
        userCommandsHandler.simMoveRandom(listOf("","2","360","10000"))
/*        userCommandsHandler.simMoveRandom(listOf("","3","360","10000"))
        userCommandsHandler.simMoveRandom(listOf("","4","360","10000"))
        userCommandsHandler.simMoveRandom(listOf("","5","360","10000"))
        userCommandsHandler.simMoveRandom(listOf("","6","360","10000"))
        userCommandsHandler.simMoveRandom(listOf("","7","360","10000"))
        userCommandsHandler.simMoveRandom(listOf("","8","360","10000"))
        userCommandsHandler.simMoveRandom(listOf("","9","360","10000"))
        userCommandsHandler.simMoveRandom(listOf("","10","360","10000"))
        userCommandsHandler.simMoveRandom(listOf("","11","360","10000"))
        userCommandsHandler.simMoveRandom(listOf("","12","360","10000"))
        userCommandsHandler.simMoveRandom(listOf("","13","360","10000"))
        userCommandsHandler.simMoveRandom(listOf("","14","360","10000"))
        userCommandsHandler.simMoveRandom(listOf("","15","360","10000"))
        userCommandsHandler.simMoveRandom(listOf("","16","360","10000"))
        userCommandsHandler.simMoveRandom(listOf("","17","360","10000"))
        userCommandsHandler.simMoveRandom(listOf("","18","360","10000"))
        userCommandsHandler.simMoveRandom(listOf("","19","360","10000"))
        userCommandsHandler.simMoveRandom(listOf("","20","360","10000"))*/

    }

    /**
     * Method that parses command taken from stdin and invokes correct method that changes server state
     *
     * @param line String from stdin that will be parsed
     */
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
                "teamLocSh" -> teamCommandsHandler.teamLocSh(parseParams(line,3){expected, real -> real != expected })
                "addUsersToTeam" -> teamCommandsHandler.addUsersOrTeamsToTeam(parseParams(line,3){ expected, real -> real < expected },true)
                "removeUsersFromTeam" -> teamCommandsHandler.addUsersOrTeamsToTeam(parseParams(line,3){ expected, real -> real < expected },false)
                "printAllTeams" -> teamCommandsHandler.printAllTeams()
                "printTeam" -> teamCommandsHandler.printTeam(parseParams(line,2){expected, real -> expected != real })
                "printAllPoints" -> pointCommandsHandler.printAllPoints()
                "printPoint" -> pointCommandsHandler.printPoint(parseParams(line,2){expected, real -> expected != real }?.get(1) ?: "")
                "deletePoint" -> {
                    runBlocking {
                        pointCommandsHandler.deletePoint(parseParams(line,2){expected, real -> expected != real }?.get(1) ?: "","admin")
                    }
                }
                "updatePoint" -> pointCommandsHandler.updatePoint(parseParams(line,2){expected, real -> real < expected })
                "createChat" -> chatCommandsHandler.createChatRoom(parseParams(line,3){expected, real -> real < expected  })
                "deleteChat" -> chatCommandsHandler.deleteChatRoom(parseParams(line,2){expected, real -> expected != real})
                "manageMembers" -> chatCommandsHandler.manageChatUsers(parseParams(line,4){expected, real -> real < expected })
                "sendMessage" -> chatCommandsHandler.sendMessage(parseParams(line,3){expected, real -> real < expected })

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
                "createTeam" -> teamCommandsHandler.createTeam(parseParams(line,0){_ ,real -> real != 6 && real != 4 })
                "deleteTeam" -> teamCommandsHandler.deleteTeam(parseParams(line,2){ expected, real -> real != expected })
                "updateTeam" -> teamCommandsHandler.updateTeam(parseParams(line,3){ expected ,real -> real < expected})
                "addUsersToTeam" -> teamCommandsHandler.addUsersOrTeamsToTeam(parseParams(line,3){ expected, real -> real < expected }, true)
                "removeUsersFromTeam" -> teamCommandsHandler.addUsersOrTeamsToTeam(parseParams(line,3){ expected, real -> real < expected },false)
                "printAllTeams" -> teamCommandsHandler.printAllTeams()
                "printTeam" -> teamCommandsHandler.printTeam(parseParams(line,2){expected, real -> expected != real })
                "printAllPoints" -> pointCommandsHandler.printAllPoints()
                "printPoint" -> pointCommandsHandler.printPoint(parseParams(line,2){expected, real -> expected != real }?.get(1) ?: "")
                "deletePoint" -> {
                    runBlocking {
                        pointCommandsHandler.deletePoint(parseParams(line,2){expected, real -> expected != real }?.get(1) ?: "","admin")
                    }
                }
                "updatePoint" -> pointCommandsHandler.updatePoint(parseParams(line,2){expected, real -> real < expected })
                "a" -> allConnected()
                "d" -> unConnectDelta()
                "g" -> generateUsers()
                "t" -> t()
                else -> println("Unknown command... Enter help to list available commands")
            }
        }
    }

    /**
     * Method that starts thread which will parse user's input and commands
     *
     */
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

    /**
     * Method that truly starts server by starting and opening embedded server for connection and starts thread that will
     * parse users input. Main thread will check every 10 seconds if server is still running.
     *
     */
    fun startApplication()
    {
        var res: NettyApplicationEngine

        res = embeddedServer(Netty, commandLineEnvironment(
            arrayOf("-config=${GetJarPath.currentWorkingDirectory}/config/application.conf")
        )).start(false)

        thread {
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

