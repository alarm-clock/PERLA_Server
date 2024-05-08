/**
 * @file: ChatDBOperations.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing ChatDBOperations class
 */
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

/**
 * Class that implements chat database operations
 *
 * @property db Database in which chat collection is stored
 */
class ChatDBOperations(private val db: MongoDatabase) {

    private val prefix = "room_"

    private val chatCollection: MongoCollection<StorableChat> = db.getCollection("Chats")

    /**
     * Method that gets chat from collection based on [chatId]
     *
     * @throws Exception when no chat with given [chatId] exists
     * @param chatId ChatID
     * @return [StorableChat]
     */
    fun getChat(chatId: String): StorableChat {
        return runBlocking {
            chatCollection.find(Filters.eq(StorableChat::_id.name,ObjectId(chatId))).first()
        }
    }

    /**
     * Method that checks if user is chat owner
     *
     * @param ownerId UserID of possible owner
     * @param chatId ChatID whose owner will be checked
     * @return True if user is chat's owner else false
     */
    fun checkIfUserIsOwner(ownerId: String, chatId: String): Boolean = getChat(chatId).ownerId == ownerId || ownerId == "admin"

    /**
     * Method that checks if user is member of chat room
     *
     * @param memberId UserID of possible member
     * @param chatId ChatID of room whose member will be checked
     * @return True if user is member else false
     */
    fun checkIfUserIsMember(memberId: String, chatId: String): Boolean = getChat(chatId).members.find { it == memberId } != null || memberId == "admin"

    /**
     * Method that stores [chat] in collection
     *
     * @param chat [StorableChat] that will be stored in collection
     * @return ChatID if chat was successfully stored in collection, null if chat room with same [StorableChat.name] is already stored
     */
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

    /**
     * Method that deletes chat room, all it's messages, and atomic counter
     *
     * @param id ID of deleted room
     */
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

    /**
     * Method that adds users to chat room
     *
     * @param userIds [List] of userIDs that will be added to room
     * @param chatId ID of room where users will be added
     * @return Updated [StorableChat] or null no room with [chatId] exist
     */
    suspend fun addUsersToChatRoom(userIds: List<String>, chatId: String): StorableChat?
    {
        val chat = try {
            getChat(chatId)
        } catch (_: Exception) { return null }
        chat.members.addAll(userIds)
        chatCollection.updateOne(Filters.eq(StorableChat::_id.name,ObjectId(chatId)),Updates.set(StorableChat::members.name,chat.members))

        return chat
    }

    /**
     * Method that removes user from chat room
     *
     * @param userId UserID that will be removed from room
     * @param chatId ID of room where user will be removed
     * @return Updated [StorableChat] or null no room with [chatId] exist
     */
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
    }

    /**
     * Remove user from all chat rooms
     *
     * @param userId UserID of user who will be removed from all chat rooms
     */
    suspend fun removeUserFromAllChatRooms(userId: String)
    {
        val allRooms = getAllChats() ?: return

        allRooms.forEach {
            if(it._id != null)
            {
                removeUserFromChatRoom(it._id!!.toString(),userId)
            }
        }
    }

    /**
     * Update owner method
     *
     * @param newOwnerId
     * @param chatId
     * @return Updated [StorableChat] or null no room with [chatId] exist
     */
    suspend fun updateOwner(newOwnerId: String, chatId: String) : StorableChat?
    {
        val chat = try {
            getChat(chatId)
        } catch (_: Exception) { return null }

        if( chat.members.find { it == newOwnerId } == null) return null
        chatCollection.updateOne(Filters.eq(StorableChat::_id.name,ObjectId(chatId)),Updates.set(StorableChat::ownerId.name,newOwnerId))

        return chat

    }

    /**
     * Method that return chat id of chat room with given name
     *
     * @param name
     * @return ChatID if room exists else false
     */
    suspend fun getChatId(name: String): String?
    {
        return try {
            val res = chatCollection.find(Filters.eq(StorableChat::name.name,name)).first()
            res._id.toString()
        } catch (_:NoSuchElementException) {
            null
        }
    }

    /**
     * Get room by name
     *
     * @param name
     * @return [StorableChat] or null if room doesn't exist
     */
    suspend fun getRoomByName(name: String): StorableChat?
    {
        return try {
            chatCollection.find(Filters.eq(StorableChat::name.name,name)).first()
        }catch (_: Exception)
        {
            null
        }
    }

    /**
     * Method that returns all stored chat rooms
     *
     * @return [List]<[StorableChat]> or null if there is no chat room stored
     */
    suspend fun getAllChats(): List<StorableChat>?
    {
        return try {
            chatCollection.find(Filters.empty()).toList()
        } catch (_:Exception)
        {
            null
        }
    }

    /**
     * Method that stores chat message in chat room's messages collection
     *
     * @param message [StorableChatMessage] that will be stored
     */
    suspend fun addMessage(message: StorableChatMessage)
    {
        val messagesCollection = db.getCollection<StorableChatMessage>("$prefix${message.chatId}")

        message._id = Counters.getCntAndInc("$prefix${message.chatId}")

        messagesCollection.insertOne(message)
    }


    /**
     * Method that returns 30 messages sent prior message with [cap] id. If there is fewer messages then 30 then returns
     * all remaining messages
     *
     * @param cap MessageID of message before which messages will be sent
     * @param chatId ChatID of room from which messages will be returned
     * @return
     */
    suspend fun get30MessagesFromIndex(cap: Long, chatId: String): List<StorableChatMessage> {
        val messagesCollection = db.getCollection<StorableChatMessage>("$prefix$chatId")

        //if cap is -1 then it means to return last 30 messages
        val checkedCap = (if (cap == -1L) Counters.getCnt("$prefix$chatId") else cap) + 1

        return messagesCollection.find(
            Filters.and(
                Filters.lte(StorableChatMessage::_id.name, checkedCap),
                Filters.gt(StorableChatMessage::_id.name, checkedCap - 32)
            )
        ).toList()
    }
}