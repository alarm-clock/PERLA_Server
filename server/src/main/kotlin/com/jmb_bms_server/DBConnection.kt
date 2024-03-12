package com.jmb_bms_server

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase

object DBConnection {
    private const val connString = "mongodb://myAdmin:admin69@localhost:27017/?maxPoolSize=20&w=majority"

    fun getMongoClient(): MongoClient
    {
        return MongoClient.create(connString)
    }
    fun getUserDatabase(client: MongoClient): MongoDatabase{
        return client.getDatabase("serverDb")
    }

}