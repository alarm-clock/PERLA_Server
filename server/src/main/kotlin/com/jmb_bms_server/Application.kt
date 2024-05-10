/**
 * @file: Application.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing Main function of server
 */
package com.jmb_bms_server

import com.jmb_bms_server.data.counter.Counters
import com.jmb_bms_server.data.point.StorablePointEntry
import com.jmb_bms_server.data.team.StorableTeamEntry
import com.jmb_bms_server.data.user.StorableUserProfile
import com.jmb_bms_server.terminal.TerminalSh
import com.jmb_bms_server.utils.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.*
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock

val lock = ReentrantLock()
var appRun: Boolean = true


val threadPool = Executors.newSingleThreadExecutor {
        task -> Thread(task, "my-background-thread")
}.asCoroutineDispatcher()

/**
 * Function that prints and parses initial dialog
 *
 * @return [InitDialogReturn] value representing what options user picked
 */
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

/**
 * Server model. It must be global variable because otherwise it could not be passed to handler and it is singleton
 */
private var model: TmpServerModel? = null

/**
 * [TerminalSh] instance
 */
private var terminalSh: TerminalSh? = null

/**
 * Main function of whole server
 *
 */
fun main() {

    val input = initalDialog()

    if( input == InitDialogReturn.EXIT )
    {
        println("Exiting program...")
        return
    }

    DBConnection.setMongoString()

    val dbClient = DBConnection.getMongoClient()
    val database = DBConnection.getUserDatabase(dbClient)

    val profileCollection = database.getCollection<StorableUserProfile>("usersTable")
    val teamsCollection = database.getCollection<StorableTeamEntry>("teamTable")
    val pointCollection = database.getCollection<StorablePointEntry>("pointTable")
    Counters.setCountersCollection(database)

    model = TmpServerModel(profileCollection,teamsCollection,pointCollection,database,input.restore)

    //generateCertificate()

    terminalSh = TerminalSh(model!!)

    terminalSh!!.startApplication()

}

/**
 * Method that holds routing server block.
 *
 */
fun Application.module() {


    install(WebSockets)
    {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
    }
    install(Sessions)
    {
        header<MySession>("SESSION")
    }

    routing {
        webSocket("/connect") {
            UserConnectionHandler(this,model!!,terminalSh!!).handleConnection()
        }
        post("/upload"){
            uploadFile(this,model!!)
        }
        get("download/{fileName}"){
            downloadFile(this,model!!)
        }
    }
}