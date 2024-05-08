/**
 * @file: ChatCommandsHandler.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing ChatCommandsHandler class
 */
package com.jmb_bms_server.terminal

import com.jmb_bms_server.MessagesJsons.Messages
import com.jmb_bms_server.TmpServerModel
import com.jmb_bms_server.data.chat.StorableChat
import com.jmb_bms_server.data.chat.StorableChatMessage
import com.jmb_bms_server.utils.checkIfAllFilesAreUploaded
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bson.types.ObjectId

/**
 * Class that implements all methods for chat feature that are invoked as reaction to received message in UserConnectionHandler
 *
 * @property model [TmpServerModel] server model, used for database operations and access user sessions
 */
class ChatCommandsHandler(private val model: TmpServerModel) {

    /**
     * Method used to create chat room from parsed command in [list].
     *
     * @param list Parsed cmd line command into [List]<[String]>: {createChat, chat_name, userID, userID, ...}
     */
    fun createChatRoom(list: List<String>?)
    {
        //method that checks number of arguments is called before this invocation
        if(list == null)
        {
            println("Wrong number of arguments")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {

            //converting list to map so that same method can be used for both client and admin, don't have time
            //to create same method twice, in the future I will do it not now. This applies to all other similar methods.
            val params = mutableMapOf<String, Any?>()
            params["name"] = list[1]

            val members = mutableListOf<String>()

            for(cnt in 2 until list.size) members.add(list[cnt])

            params["memberIds"] = members

            createChatRoom(params,"admin")
        }
    }

    /**
     * Method used to create chat room from parsed JSON message received from client. This method also checks if
     * message contains all values required to create chat room.
     *
     * @param params Parsed JSON message into [Map]<[key, value]>
     * @param userId UserId of user that wants to create chat room
     * @return True if chat room was created successfully otherwise false
     */
    suspend fun createChatRoom(params: Map<String, Any?>, userId: String): Boolean
    {
        val name = params["name"] as? String ?: return false
        val memberIds = params["memberIds"] as? List<String>

        val addedIds = memberIds?.toMutableList() ?: mutableListOf()
        if(userId != "admin")addedIds.add(userId)

        val chat = StorableChat(name = name, members = addedIds, ownerId = userId)

        val id = model.chatDb.createChat(chat) ?: return false

        chat._id = ObjectId(id)

        model.sendMessageToCertainGroup(Messages.chatRoomCreation(chat),chat.members)

        return true
    }

    /**
     * Method used to delete chat room parsed command stored in [list]
     *
     * @param list Parsed cmd line command into [List]<[String]>: {deleteChat, chatRoomID}
     */
    fun deleteChatRoom(list: List<String>?)
    {
        if(list == null)
        {
            println("Wrong number of arguments")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val params = mutableMapOf<String, Any?>()

            val id = model.chatDb.getChatId(list[1]) ?: return@launch

            params["_id"] = id
            deleteChatRoom(params,"admin")
        }
    }

    /**
     * Method used to delete chat room from parsed JSON message sent by client. Method also checks if user is able to
     * delete chat room and if message contains all values needed for chat deletion
     *
     * @param params Parsed JSON message into [Map]<[key, value]>
     * @param userId ID of user that sent request to delete chat room
     */
    suspend fun deleteChatRoom(params: Map<String, Any?>, userId: String)
    {
        val id = params["_id"] as? String ?: return

        if( !model.chatDb.checkIfUserIsOwner(userId,id) ) return

        val chat = try {
            model.chatDb.getChat(id)
        } catch (_:Exception){ return }

        model.chatDb.deleteChat(id)
        model.sendMessageToCertainGroup(Messages.deleteChatRoom(id),chat.members)
    }

    /**
     * Method used to update chat room members from parsed command in [list]
     *
     * @param list Parsed cmd line command into [List]<[String]>: {manageMembers, chatRoomId, {true ? false}, userId, userId, ...}
     */
    fun manageChatUsers(list: List<String>?){
        if(list == null)
        {
            println("Wrong number of arguments")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val params = mutableMapOf<String, Any?>()

            val id = model.chatDb.getChatId(list[1]) ?: return@launch
            params["_id"] = id
            params["add"] = list[2] == "true"

            val members = mutableListOf<String>()

            for(cnt in 3 until list.size) members.add(list[cnt])
            params["userIds"] = members
            manageChatUsers(params,"admin")

        }
    }

    /**
     * Method for managing users in chat room from parsed JSON message sent by client. Method also checks if user is able to
     * manage users in chat room and if message contains all values needed to do operation
     *
     * @param params Parsed JSON message into [Map]<[key, value]>
     * @param userId ID of user that is managing users in chat room
     */
    suspend fun manageChatUsers(params: Map<String, Any?>, userId: String)
    {
        val id = params["_id"] as? String ?: return
        val ids = params["userIds"] as? List<String> ?: return
        val add = params["add"] as? Boolean ?: return

        if( !model.chatDb.checkIfUserIsOwner(userId,id) ) return

        if(add)
        {
            val chat = model.chatDb.addUsersToChatRoom(ids,id) ?: return
            model.sendMessageToCertainGroup(Messages.chatRoomCreation(chat),chat.members)
        } else
        {
            if(ids.isEmpty()) return

            var chat: StorableChat?  = null

            ids.forEach {
                chat = model.chatDb.removeUserFromChatRoom(id,it) ?: return
            }
            model.sendMessageToCertainGroup(Messages.deleteChatRoom(id),ids)
            model.sendMessageToCertainGroup(Messages.chatRoomCreation(chat!!), chat!!.members)
        }
    }

    /**
     * Method for changing chat room's owner from parsed JSON message sent by client. Method also checks if user is able to
     * change chat room's owner and if message contains all values needed to do operation
     *
     * @param params Parsed JSON message into [Map]<[key, value]>
     * @param userId ID of user that is changing chat room's owner
     */
    suspend fun changeChatOwner(params: Map<String, Any?>, userId: String)
    {
        val id = params["_id"] as? String ?: return
        val newOwnerId = params["newOwnerId"] as? String ?: return

        if( !model.chatDb.checkIfUserIsOwner(userId,id) ) return

        val chat = model.chatDb.updateOwner(newOwnerId,id) ?: return
        model.sendMessageToCertainGroup(Messages.chatRoomCreation(chat), chat.members)
    }

    /**
     * Method that sends message to chat room from parsed command in [list]
     *
     * @param list Parsed cmd line command into [List]<[String]>: {sendMessage, chatRoomName, body of message ...}
     */
    fun sendMessage(list: List<String>?)
    {
        if(list == null)
        {
            println("Wrong number of arguments")
            return
        }
        CoroutineScope(Dispatchers.IO).launch{
            val params = mutableMapOf<String, Any?>()

            val id = model.chatDb.getChatId(list[1]) ?: return@launch
            params["_id"] = id

            var builder = StringBuilder()

            for(cnt in 2 until list.size) builder.append(list[cnt]).append(' ')

            params["text"] = builder.toString()

            sendMessage(params,"admin","admin","")
        }
    }

    /**
     * Method for adding message to chat room from parsed JSON message sent by client. Method also checks if user is able to
     * send messages in chat room and if message contains all values needed to do operation
     *
     * @param params Parsed JSON message into [Map]<[key, value]>
     * @param userId ID of user that is sending message
     * @param userName Username
     * @param userSymbol Symbol code of user's symbol
     * @return True if message was successfully parsed and sent to other users else false
     */
    suspend fun sendMessage(params: Map<String, Any?>, userId: String, userName: String, userSymbol: String): Boolean
    {
        val id = params["_id"] as? String ?: return false

        if(!model.chatDb.checkIfUserIsMember(userId,id)) return false

        val text = params["text"] as? String
        val files = params["files"] as? List<String>
        val points = params["points"] as? List<String>

        if(files != null)
        {
            if(!checkIfAllFilesAreUploaded(files)) return false
        }

        //not checking points because for now I will use already created points and if something went wrong client will show that point no longer exists

        val message = StorableChatMessage(
            chatId = id, userName = userName, userSymbol = userSymbol, text = text ?: "", files = files ?: listOf(), points = points ?: listOf()
        )
        model.chatDb.addMessage(message)

        val chat = try {
            model.chatDb.getChat(id)
        }catch (_:Exception) { return false }

        model.sendMessageToCertainGroup(Messages.chatRoomMessage(message),chat.members)

        return true
    }

    /**
     * Method that sends messages request by user in parsed JSON message. Method also checks if user is in given
     * chat room and if message contains everything required to fetch messages.
     *
     * @param params  Parsed JSON message into [Map]<[key, value]>
     * @param userId ID of user that is fetching messages
     */
    suspend fun fetchMessages(params: Map<String, Any?>, userId: String)
    {
        val id = params["_id"] as? String ?: return
        val cap = params["cap"] as? Double ?: return

        if(!model.chatDb.checkIfUserIsMember(userId,id)) return

        var messages = model.chatDb.get30MessagesFromIndex(cap.toLong(),id)

        messages = messages.sortedBy { it._id }

        //begging of chat was reached so adding special message that indicates that there are no more messages
        if(messages.isEmpty() || messages.first()._id == 0L)
        {
            val tmp = messages.toMutableList()
            tmp.add(StorableChatMessage(-1,id,"","","", listOf(), listOf()))
            messages = tmp
        }

        val session = model.userSessionsSet.find { it.userId.get().toString() == userId }?.session?.get() ?: return
        session.send(Frame.Text(Messages.fetchedMessages(messages)))
    }
}