package com.jmb_bms_server.utils

import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

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
    private fun startJob()
    {
        timeout?.cancel()
        timeout = CoroutineScope(Dispatchers.IO).launch {
            delay(1200000)  // 10 minutes
            files.forEach{
                File("${GetJarPath.currentWorkingDirectory}/files/$it").delete()
            }
            transactionState.customSet(TransactionState.FAILED)
            storedList?.remove(this@Transaction)
            storedList = null
        }
    }

    fun addFileName(fileName: String)
    {
        startJob()
        files.add(fileName)
    }

    fun failTransaction()
    {

        files.forEach{
            File("${GetJarPath.currentWorkingDirectory}/files/$it").delete()
        }
        transactionState.customSet(TransactionState.FAILED)
        startJob()
    }

    fun failTransactionNoWait()
    {
        files.forEach{
            File("${GetJarPath.currentWorkingDirectory}/files/$it").delete()
        }
        transactionState.set(TransactionState.FAILED)
        storedList?.remove(this)
        storedList = null
        timeout?.cancel()
    }

    fun cancelTimeout() { timeout?.cancel()}

    fun finishTransaction()
    {
        transactionState.customSet(TransactionState.FINISHED)
        cancelTimeout()
        storedList?.remove(this)
    }

    private fun AtomicReference<TransactionState>.customSet(newState: TransactionState)
    {
        this.set(newState)
        startJob()
    }
}