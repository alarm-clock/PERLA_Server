package com.jmb_bms_server


import com.jmb_bms_server.data.counter.Counters
import com.jmb_bms_server.data.point.StorablePointEntry
import com.jmb_bms_server.data.team.StorableTeamEntry
import com.jmb_bms_server.data.user.StorableUserProfile
import com.jmb_bms_server.terminal.TerminalSh
import com.jmb_bms_server.utils.*
import io.ktor.network.tls.certificates.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileReader
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock

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

fun readConfiguration(): Map<String, String>?
{
    try {
        val conf = mutableMapOf<String, String>()

        val file = File("${GetJarPath.currentWorkingDirectory}/config/conf.txt")

        FileReader(file).forEachLine {
            val trimmedLine = it.trim()
            if(trimmedLine.isNotEmpty() && trimmedLine[0] != '!' && trimmedLine.isNotBlank())
            {
                val pair = trimmedLine.split(":", limit = 2)
                conf[pair[0].trim()] = pair[1].trim()
            }
        }

        Configuration.port = conf["port"]!!.toInt()
        Configuration.mongo = conf["mongo"]!!

        return conf
    } catch (e:Exception){
        e.printStackTrace()
        println("Unable to read conf.txt. Stopping server...")
        return null
    }

}

private var model: TmpServerModel? = null
private var terminalSh: TerminalSh? = null

//@OptIn(DelicateCoroutinesApi::class)
fun main() {

    readConfiguration() ?: return

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

    generateCertificate()

    terminalSh = TerminalSh(model!!)

    terminalSh!!.startApplication()

}

fun Application.module() {
    //configureSecurity()
    //configureSerialization()
    //configureSockets()
    //configureRouting()



    println(environment.config.port)
    println(environment.config.host)
    println(environment.config.toMap().toString())

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