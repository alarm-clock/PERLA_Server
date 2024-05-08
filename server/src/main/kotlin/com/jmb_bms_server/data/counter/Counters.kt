/**
 * @file: Counters.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing Counters object
 */
package com.jmb_bms_server.data.counter

import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull

/**
 * Object for working with atomic, auto-incrementing database counters. This serves as replacement for AUTO INCREMENT
 * in SQL databases
 *
 */
object Counters {

    private var countersCollection: MongoCollection<CntRow>? = null

    private const val suffix = "_cnt"

    /**
     * Method that sets [countersCollection] attribute.
     *
     * @param db Database in which counters collection will be
     */
    fun setCountersCollection(db: MongoDatabase)
    {
        countersCollection = db.getCollection<CntRow>("counters")
    }

    /**
     * Method that returns current value of counter identified by [id] and then increments its value.
     * If given counter does not exist it will be created and 0 is returned.
     *
     * @param id ID of counter
     * @return Current counters value
     */
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

    /**
     * Method that returns current value of counter identified by [id]
     *
     * @param id ID of counter
     * @return Current counters value
     */
    suspend fun getCnt(id: String): Long
    {
        val value = countersCollection!!.find(Filters.eq("_id",id + suffix),CntRow::class.java).firstOrNull()
        return value?.seq ?: 0L
    }

    /**
     * Method that removes counter with [id] from counters collection
     *
     * @param id ID of counter that will be removed
     */
    suspend fun removeCounter(id: String)
    {
        countersCollection?.deleteOne(Filters.eq("_id",id + suffix))
    }

    /**
     * Method that inserts counter into collection
     *
     * @param id
     */
    private suspend fun createCounter(id: String)
    {
        countersCollection?.insertOne(CntRow(id,0))
    }

}

