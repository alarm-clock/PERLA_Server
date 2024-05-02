package com.jmb_bms_server

import com.jmb_bms_server.utils.Configuration
import com.jmb_bms_server.utils.GetJarPath
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.server.application.*
import io.ktor.server.engine.*

object DBConnection {
    private var connString = "mongodb://myAdmin:admin69@localhost:27017/?maxPoolSize=20&w=majority"

    private const val prefix = "mongodb://"
    private const val suffix = "/?maxPoolSize=20&w=majority"

    fun setMongoString()
    {
        val config = commandLineEnvironment(
            arrayOf("-config=${GetJarPath.currentWorkingDirectory}/config/application.conf")
        ).config

        val user = config.property("database.user").getString()
        val pwd = config.property("database.pwd").getString()
        val host = config.property("database.host").getString()
        val port = config.property("database.port").getString()

        connString = "$prefix$user:$pwd@$host:$port$suffix"
    }

    fun getMongoClient(): MongoClient
    {
        return MongoClient.create(connString)
    }
    fun getUserDatabase(client: MongoClient): MongoDatabase{
        return client.getDatabase("serverDb")
    }

}