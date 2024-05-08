/**
 * @file: Transaction.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing Transaction class
 */
package com.jmb_bms_server.utils

import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Class representing transaction. Transaction is object that holds files that are uploaded together and if one upload
 * fails, whole transaction fails and all uploaded files will be deleted and new requests with same transaction[id] will
 * be stopped right away with fail response. Transaction will live for 10 minutes from last update to transaction or until
 * it is finished. After those 10 minutes it will fail on its own and delete all files in it. Every edit to transaction restarts 10 minute timeout.
 * Transaction has 3 states: in progress, fail, finnish. When in progress state it can add new files to transaction until it finishes or fails.
 * When in fail state it will wait for ten minutes to tell some forgotten request that this transaction is dead. When finished
 * it will remove itself from list of transactions.
 *
 * @property id Transaction id
 * @property owner UserID of user that started transaction
 * @property storedList List of all transactions
 * @constructor Starts 10 minute wait
 */
class Transaction(
    val id: String,
    val owner: String,
    private var storedList: CopyOnWriteArrayList<Transaction>? )
{
    var transactionState = AtomicReference(TransactionState.IN_PROGRESS)
        set(value){
            field = value
            startJob()
        }


    private var timeout: Job? = null
    val files = mutableListOf<String>()
    init {
        startJob()
    }

    /**
     * Method that starts timeout job which after 10 minutes fails transaction for good
     *
     */
    private fun startJob()
    {
        timeout?.cancel()
        timeout = CoroutineScope(Dispatchers.IO).launch {
            delay(600000)  // 10 minutes
            files.forEach{
                File("${GetJarPath.currentWorkingDirectory}/files/$it").delete()
            }
            transactionState.customSet(TransactionState.FAILED)
            storedList?.remove(this@Transaction)
            storedList = null
        }
    }

    /**
     * Method that adds file to transaction and resets timeout
     *
     * @param fileName
     */
    fun addFileName(fileName: String)
    {
        startJob()
        files.add(fileName)
    }

    /**
     * Method that sets failed state to transaction, deletes all files and sets timeout for removal from [storedList]
     * for 10 minutes.
     *
     */
    fun failTransaction()
    {

        files.forEach{
            File("${GetJarPath.currentWorkingDirectory}/files/$it").delete()
        }
        files.clear()
        transactionState.customSet(TransactionState.FAILED)
        startJob()
    }

    /**
     * Method that fails transaction, deletes all files, removes itself from [storedList] without 10 minute wait.
     * Use it when client acknowledges transaction fail.
     *
     */
    fun failTransactionNoWait()
    {
        files.forEach{
            File("${GetJarPath.currentWorkingDirectory}/files/$it").delete()
        }
        transactionState.set(TransactionState.FAILED)
        storedList?.remove(this)
        storedList = null
        cancelTimeout()
    }

    /**
     * Method that cancels timeout
     *
     */
    fun cancelTimeout() { timeout?.cancel()}

    /**
     * Method that finishes transaction and removes it from [storedList]
     *
     */
    fun finishTransaction()
    {
        transactionState.customSet(TransactionState.FINISHED)
        cancelTimeout()
        storedList?.remove(this)
    }

    /**
     * Method that sets new state and resets timeout
     *
     * @param newState
     */
    private fun AtomicReference<TransactionState>.customSet(newState: TransactionState)
    {
        this.set(newState)
        startJob()
    }
}