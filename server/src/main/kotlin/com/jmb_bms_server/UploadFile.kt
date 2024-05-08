/**
 * @file: UploadFile.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing methods related to file uploading and downloading
 */
package com.jmb_bms_server

import com.jmb_bms_server.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import java.io.File
import java.lang.Exception


/**
 * Method that validates if request is valid request or not. It checks if SESSION header has id of user that
 * exists in system and has active connection.
 *
 * @param server [PipelineContext] from which header will be taken
 * @param model [TmpServerModel] used to check for user
 * @return UserID on success else null
 */
suspend fun validateRequest(server: PipelineContext<Unit, ApplicationCall>, model: TmpServerModel): String?
{
    val id = server.call.request.headers["SESSION"]

    if(id == null)
    {
        Logger.log("Someone tried to upload/download file without identification\n " +
                "ip: ${server.call.request.local.remoteHost}","unknown")
        server.call.respond(HttpStatusCode.Forbidden, "Unidentified")
        return null
    }

    when(model.validateId(id))
    {
        0 -> return id
        1 -> {
            Logger.log("Identified user doesn't exists\n " +
                    "ip: ${server.call.request.local.remoteHost}","Unidentified")
            server.call.respond(HttpStatusCode.Forbidden, "NoUser")
            return null
        }
        2 -> {
            Logger.log("Identified user doesn't have active connection\n " +
                    "ip: ${server.call.request.local.remoteHost}","$id")
            server.call.respond(HttpStatusCode.Forbidden, "NotConnected")
            return null
        }
        else ->
        {
            Logger.log("Internal error while validating user, line 43","Unknown")
            server.call.respond(HttpStatusCode.InternalServerError)
            return null
        }
    }
}

/**
 * Method that tries to find existing transaction. If no transaction is found then new transaction is created.
 *
 * @param transactionId ID of transaction
 * @param userId ID of user that is uploading file
 * @param model Server model used to find transaction
 * @return [Transaction]
 */
fun findTransaction(transactionId: String,userId: String, model: TmpServerModel): Transaction
{
    var transaction = model.tmpTransactionFiles.find { it.id == transactionId }

    if(transaction == null)
    {
        val newTransaction = Transaction(transactionId,userId,model.tmpTransactionFiles)
        model.tmpTransactionFiles.add(newTransaction)
        transaction = newTransaction
    }

    return transaction
}

/**
 * Method that adds new file to transaction
 *
 * @param transactionId ID of transaction
 * @param userId ID of user who is uploading files
 * @param model Server model
 * @param fileName Stored file's name
 */
fun editTransaction(transactionId: String?, userId: String, model: TmpServerModel, fileName: String?)
{
    transactionId ?: return
    val transaction = findTransaction(transactionId, userId, model)

    if(transaction.transactionState.get() != TransactionState.IN_PROGRESS) throw TransactionStateException("Transaction either failed or has already finished")
    transaction.addFileName(fileName!!)
}

/**
 * Method that fails transaction
 *
 * @param transactionId ID of transaction that will be failed
 * @param model Server model
 */
fun failTransaction(transactionId: String?, model: TmpServerModel)
{
    transactionId ?: return
    val transaction = model.tmpTransactionFiles.find{it.id == transactionId} ?: return
    Logger.log("failing transaction $transaction","none")
    transaction.failTransaction()
}

/**
 * Method that handles file upload to server
 *
 * @param server [PipelineContext]
 * @param model Server Model
 */
suspend fun uploadFile(server: PipelineContext<Unit, ApplicationCall>, model: TmpServerModel)
{
    val multipart = server.call.receiveMultipart()
    var fileName: String? = null
    val id = validateRequest(server, model) ?: return

    var isPointRelated = false
    var transaction: Transaction? = null
    var fileAlreadyExists = false

    try {
        multipart.forEachPart { partData ->
            when(partData){
                is PartData.FormItem -> {

                    //parse text part
                    when(partData.name)
                    {
                        "transactionId" ->
                        {
                            //finds transaction
                            val transactionId = partData.value
                            transaction = findTransaction(transactionId,id,model)
                            if(transaction!!.transactionState.get() != TransactionState.IN_PROGRESS) throw TransactionStateException("Transaction already failed or finished")
                        }
                        //flag indicating that multiple files are in transaction
                        "point" -> isPointRelated = partData.value.toBoolean()
                    }
                }
                is PartData.FileItem -> {
                    try {
                        //file saving
                        fileName = partData.save(GetJarPath.currentWorkingDirectory,id)
                    } catch (_:FileExistsException)
                    {
                        //if file exists just stop upload and retirn success
                        fileAlreadyExists = true
                    }
                }
                else -> throw UnknownFieldException()
            }
        }
        if(fileName == null && !fileAlreadyExists) throw Exception()

        if(isPointRelated){

            if(!fileAlreadyExists) editTransaction(transaction?.id,id,model, fileName)
        }
        server.call.respond(HttpStatusCode.OK)

    }catch (e: UnknownFieldException)
    {
        //e.printStackTrace()
        //println("Here1")
        server.call.respond(HttpStatusCode.Conflict)
        if(isPointRelated)
        {
            failTransaction(transaction?.id,model)
        }
        File("${GetJarPath.currentWorkingDirectory}/files/$fileName").delete()
    } catch (e: TransactionStateException)
    {
        //e.printStackTrace()
       // println("Here2")
        server.call.respond(HttpStatusCode.FailedDependency, e.stackTrace)
        if(isPointRelated)
        {
            failTransaction(transaction?.id,model)
        }
        File("${GetJarPath.currentWorkingDirectory}/files/$fileName").delete()
    }
    catch (e: Exception)
    {
        //e.printStackTrace()
        server.call.respond(HttpStatusCode.InternalServerError, e.stackTrace)
        //println("Here3")
        if(isPointRelated)
        {
            failTransaction(transaction?.id,model)
        }
        File("${GetJarPath.currentWorkingDirectory}/files/$fileName").delete()
    }
}


/**
 * Function that handles file download request
 *
 * @param server
 * @param model
 */
suspend fun downloadFile(server: PipelineContext<Unit, ApplicationCall>, model: TmpServerModel)
{
    validateRequest(server, model) ?: return
    val fileName = server.call.parameters["fileName"]

    if(fileName == null)
    {
        server.call.respond(HttpStatusCode.Conflict,"NoName")
        return
    }
    //println("${GetJarPath.currentWorkingDirectory}/files/$fileName")
    val file = File("${GetJarPath.currentWorkingDirectory}/files/$fileName")
    if( file.exists())
    {
        //println("Here")
        server.call.respondFile(file)
    } else
    {
        server.call.respond(HttpStatusCode.NotFound)
    }
}
