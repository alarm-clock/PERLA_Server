/**
 * @file: DBConnection.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing DBConnection class
 */
package com.jmb_bms_server

import com.jmb_bms_server.utils.GetJarPath
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.server.engine.*

/**
 * Object that implements methods for connecting to database
 */
object DBConnection {
    private var connString = "mongodb://myAdmin:admin69@localhost:27017/?maxPoolSize=20&w=majority&authMechanism=SCRAM_SHA_1"

    private const val prefix = "mongodb://"
    private const val suffix = "/?maxPoolSize=20&w=majority"

    /**
     * Method that creates mongo connection string from configuration file
     *
     */
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

    /**
     * Get mongo client
     *
     * @return [MongoClient]
     */
    fun getMongoClient(): MongoClient
    {
        return MongoClient.create(connString)
    }

    /**
     * Get user database
     *
     * @param client [MongoClient]
     * @return [MongoDatabase]
     */
    fun getUserDatabase(client: MongoClient): MongoDatabase{
        return client.getDatabase("serverDb")
    }

}