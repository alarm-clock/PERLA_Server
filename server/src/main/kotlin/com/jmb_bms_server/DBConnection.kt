package com.jmb_bms_server

import com.jmb_bms_server.utils.Configuration
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase

object DBConnection {
    private var connString = "mongodb://myAdmin:admin69@localhost:27017/?maxPoolSize=20&w=majority"

    private const val prefix = "mongodb://"
    private const val suffix = "/?maxPoolSize=20&w=majority"

    fun setMongoString()
    {
        connString = "$prefix${Configuration.mongo}$suffix"
    }

    fun getMongoClient(): MongoClient
    {
        return MongoClient.create(connString)
    }
    fun getUserDatabase(client: MongoClient): MongoDatabase{
        return client.getDatabase("serverDb")
    }

}