package com.jmb_bms_server


import com.jmb_bms_server.data.StorableTeamEntry
import com.jmb_bms_server.data.StorableUserProfile
import com.jmb_bms_server.terminal.TerminalSh
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.*
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

val lock = ReentrantLock()
var appRun: Boolean = true


val threadPool = Executors.newSingleThreadExecutor {
        task -> Thread(task, "my-background-thread")
}.asCoroutineDispatcher()

fun initalDialog(): InitDialogReturn
{
    while(true)
    {
        println("Would you like to restore server state from database? <yes/no/exit>")
        var response = readln()

        if(response == "yes"){
            return InitDialogReturn.USE_DB
        } else if( response == "no")
        {
            while (true)
            {
                println("Are you sure? This action will wipe database clean. <yes/no/exit>")
                response = readln()
                if(response == "yes") return InitDialogReturn.CLEAR_DB
                else if( response == "no") break
                else if( response == "exit") return InitDialogReturn.EXIT
                else
                {
                    println("Unknown response \"$response\"")
                }
            }
        } else if( response == "exit") return InitDialogReturn.EXIT
        else
        {
            println("Unknown response \"$response\"")
        }
    }
}



//@OptIn(DelicateCoroutinesApi::class)
fun main() {

    val input = initalDialog()

    if( input == InitDialogReturn.EXIT )
    {
        println("Exiting program...")
        return
    }

    val dbClient = DBConnection.getMongoClient()
    val database = DBConnection.getUserDatabase(dbClient)

    val profileCollection = database.getCollection<StorableUserProfile>("usersTable")
    val teamsCollection = database.getCollection<StorableTeamEntry>("teamTable")

    val model = TmpServerModel(profileCollection,teamsCollection,input.restore)

    val terminalSh = TerminalSh(model)
    terminalSh.startApplication()

}

fun Application.module(model: TmpServerModel, terminalSh: TerminalSh) {
    //configureSecurity()
    //configureSerialization()
    //configureSockets()
    //configureRouting()

    install(WebSockets)
    {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
    }
    routing {
        webSocket("/connect") {
            UserConnectionHandler(this,model,terminalSh).handleConnection()
        }
    }
}