package com.jmb_bms_server.data.counter

import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull


object Counters {

    private var countersCollection: MongoCollection<CntRow>? = null

    private const val suffix = "_cnt"

    fun setCountersCollection(db: MongoDatabase)
    {
        countersCollection = db.getCollection<CntRow>("counters")
    }

    suspend fun getCntAndInc(id: String): Long
    {
        countersCollection ?: return -1
        val value = countersCollection!!.findOneAndUpdate(
            Filters.eq("_id",id + suffix),
            Updates.inc("seq",1),
            FindOneAndUpdateOptions().upsert(true)
        ) ?: return 0L

        return value.seq
    }

    suspend fun getCnt(id: String): Long
    {
        val value = countersCollection!!.find(Filters.eq("_id",id + suffix),CntRow::class.java).firstOrNull()
        return value?.seq ?: 0L
    }

    suspend fun removeCounter(id: String)
    {
        countersCollection?.deleteOne(Filters.eq("_id",id + suffix))
    }

    private suspend fun createCounter(id: String)
    {
        countersCollection?.insertOne(CntRow(id,0))
    }

}

