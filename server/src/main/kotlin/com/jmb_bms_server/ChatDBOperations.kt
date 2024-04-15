package com.jmb_bms_server

import com.jmb_bms_server.data.chat.StorableChat
import com.jmb_bms_server.data.chat.StorableChatMessage
import com.jmb_bms_server.data.counter.Counters
import com.jmb_bms_server.utils.GetJarPath
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.bson.types.ObjectId
import java.io.File
import java.util.NoSuchElementException

class ChatDBOperations(private val db: MongoDatabase) {

    private val prefix = "room_"

    private val chatCollection: MongoCollection<StorableChat> = db.getCollection("Chats")

    fun getChat(chatId: String): StorableChat {
        return runBlocking {
            chatCollection.find(Filters.eq(StorableChat::_id.name,ObjectId(chatId))).first()
        }
    }
    fun checkIfUserIsOwner(ownerId: String, chatId: String): Boolean = getChat(chatId).ownerId == ownerId || ownerId == "admin"
    fun checkIfUserIsMember(memberId: String, chatId: String): Boolean = getChat(chatId).members.find { it == memberId } != null || memberId == "admin"

    suspend fun createChat(chat: StorableChat): String?
    {
        return try {
            chatCollection.find(Filters.eq(StorableChat::name.name,chat.name)).first()
            null
        } catch (_: Exception)
        {
            chatCollection.insertOne(chat)
            val id = chatCollection.find(Filters.eq(StorableChat::name.name,chat.name)).toList().first()._id
            return id.toString()
        }
    }

    suspend fun deleteChat(id: String)
    {
        try {
            getChat(id)
        } catch (_: Exception) { return }

        chatCollection.deleteOne(Filters.eq(StorableChat::_id.name,ObjectId(id)))

        val messages = db.getCollection<StorableChatMessage>("$prefix$id")

        messages.find(Filters.empty()).toList().forEach {
            it.files.forEach {fileName ->
                File("${GetJarPath.currentWorkingDirectory}/files/$fileName").delete()
            }
        }
        messages.drop()
        Counters.removeCounter("$prefix$id")
    }

    suspend fun addUsersToChatRoom(userIds: List<String>, chatId: String): StorableChat?
    {
        val chat = try {
            getChat(chatId)
        } catch (_: Exception) { return null }
        chat.members.addAll(userIds)
        chatCollection.updateOne(Filters.eq(StorableChat::_id.name,ObjectId(chatId)),Updates.set(StorableChat::members.name,chat.members))

        return chat
    }

    suspend fun removeUserFromChatRoom(chatId: String, userId: String): StorableChat?
    {
        var chat = try {
            getChat(chatId)
        } catch (_: Exception) { return null }

        chat.members.remove(userId)

        if( chat.members.isEmpty() )
        {
            deleteChat(chatId)
            return chat
        }
        if(chat.ownerId == userId)
        {
            chat = updateOwner(chat.members.first(),chatId) ?: return null
        }

        chatCollection.updateOne(Filters.eq(StorableChat::_id.name,ObjectId(chatId)),Updates.set(StorableChat::members.name,chat.members))

        return chat

        //TODO send delete to user who is deleted and update to others
    }

    suspend fun updateOwner(newOwnerId: String, chatId: String) : StorableChat?
    {
        val chat = try {
            getChat(chatId)
        } catch (_: Exception) { return null }

        if( chat.members.find { it == newOwnerId } == null) return null
        chatCollection.updateOne(Filters.eq(StorableChat::_id.name,ObjectId(chatId)),Updates.set(StorableChat::ownerId.name,newOwnerId))

        return chat
        //TODO send update to all users in chat
    }

    suspend fun getChatId(name: String): String?
    {
        return try {
            val res = chatCollection.find(Filters.eq(StorableChat::name.name,name)).first()
            res._id.toString()
        } catch (_:NoSuchElementException) {
            null
        }

    }

    suspend fun getAllChats(): List<StorableChat>?
    {
        return try {
            chatCollection.find(Filters.empty()).toList()
        } catch (_:Exception)
        {
            null
        }
    }

    suspend fun addMessage(message: StorableChatMessage)
    {
        val messagesCollection = db.getCollection<StorableChatMessage>("$prefix${message.chatId}")

        message._id = Counters.getCntAndInc("$prefix${message.chatId}")

        messagesCollection.insertOne(message)

        //TODO add check if user is in room, if room exists, if all points and files exists , and send it to all other users
    }


    suspend fun get30MessagesFromIndex(cap: Long, chatId: String): List<StorableChatMessage> {
        val messagesCollection = db.getCollection<StorableChatMessage>("$prefix$chatId")

        val checkedCap = (if (cap == -1L) Counters.getCnt("$prefix$chatId") else cap) + 1

        return messagesCollection.find(
            Filters.and(
                Filters.lte(StorableChatMessage::_id.name, checkedCap),
                Filters.gt(StorableChatMessage::_id.name, checkedCap - 32)
            )
        ).toList()
    }
}