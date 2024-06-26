/**
 * @file: Messages.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing Messages class
 */
package com.jmb_bms_server.MessagesJsons

import com.jmb_bms_server.data.user.UserProfile
import com.jmb_bms_server.customSerializers.ObjectIdSerializer
import com.jmb_bms_server.data.chat.StorableChat
import com.jmb_bms_server.data.chat.StorableChatMessage
import com.jmb_bms_server.data.point.PointEntry
import com.jmb_bms_server.data.team.TeamEntry
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
import org.bson.types.ObjectId

/**
 * Class with static methods that create JSON messages that can be sent to client, and he will be able to parse them.
 */
class Messages {

    companion object{

        const val OPCODE = "opCode"
        const val REASON = "reason"
        const val ID = "_id"
        const val LAT = "lat"
        const val LONG = "long"
        const val ADDING = "adding"
        const val PROFILE_ID = "profileId"

        /**
         * Hello there message. Initial response for clients hello there (I know it should be "General Kenobi")
         *
         * @return JSON message with opCode -1
         */
        fun helloThere(): String
        {
            val jsonObj = buildJsonObject{
                put(OPCODE,-1)
            }
            return jsonObj.toString()
        }

        /**
         * Bye. Message sent when closing websocket connection
         *
         * @param reason Closure reason
         * @return JSON message with opCode 0
         */
        fun bye(reason: String): String
        {
            return buildJsonObject {
                put(OPCODE,0)
                put(REASON,reason)
            }.toString()
        }

        /**
         * Username already in use. Message sent when user is connecting but his username is already in use.
         *
         * @return JSON message with opCode 1 and ID 0.
         */
        fun userNameAlreadyInUse(): String
        {
            return buildJsonObject {
                put(OPCODE,1)
                put(ID,0)
            }.toString()
        }

        /**
         * Connection successful. Message sent user has successfully connected to server. It contains his serverID and
         * team entries.
         *
         * @param id UsersID
         * @param teamEntry [HashSet] of teamIds whose user is member
         * @return JSON message with opCode 1
         */
        fun connectionSuccesFull(id: String, teamEntry: HashSet<ObjectId>): String
        {
            val message = SuccessfullConnecion(_id = id, teamEntry = teamEntry)
            val json = Json{
                serializersModule = SerializersModule {
                    contextual(ObjectId::class,ObjectIdSerializer)
                }
            }
            return json.encodeToString(message)
        }

        /**
         * Send user profile. Message sent when new user has connected or when client is syncing with server.
         *
         * @param profile [UserProfile] that is sent to client
         * @return JSON message wit opCode 2.
         */
        fun sendUserProfile(profile: UserProfile): String
        {
            val message = UserProfileMessage(
                _id = profile._id.get(),
                userName = profile.userName.get(),
                symbolCode = profile.symbolCode.get(),
                location = profile.location.get(),
                teamEntry =  profile.teamEntry.get()
            )
            val json = Json{
                serializersModule = SerializersModule {
                    contextual(ObjectId::class,ObjectIdSerializer)
                }
            }
            return json.encodeToString(message)
        }

        /**
         * Send stopped location share. Message sent to other clients when user has stopped sharing location.
         *
         * @param id UserId of user that has stopped sharing his location
         * @return JSON message with opCode 3
         */
        fun sendStopShLoc(id: String): String
        {
            return buildJsonObject {
                put(OPCODE, 3)
                put(ID,id)
                put(LAT,"stop")
                put(LONG,"stop")
            }.toString()
        }

        /**
         * Send location update. Message sent to other clients when user updated his location.
         *
         * @param profile [UserProfile] of user that has updated his location. Location will be taken from this object.
         * @return JSON message with opCode 3.
         */
        fun sendLocationUpdate(profile: UserProfile): String
        {
            if(profile.location.get() == null) return sendStopShLoc(profile._id.get().toString())

            val message = LocationUpdateMessage(
                _id = profile._id.get(),
                lat = profile.location.get()?.lat?.get() ?: 0.0,
                long = profile.location.get()?.long?.get() ?: 0.0
            )
            val json = Json{
                serializersModule = SerializersModule {
                    contextual(ObjectId::class, ObjectIdSerializer)
                }
            }
            return json.encodeToString(message)
        }

        /**
         * User disconnected. Message sent to other clients when user has disconnected.
         *
         * @param id UserId of user that has disconnected
         * @return JSON message with opCode 7.
         */
        fun userDisconnected(id: String): String
        {
            return  buildJsonObject {
                put(OPCODE,7)
                put(ID,id)
            }.toString()
        }

        /**
         * Relay profile update. Message sent to other clients when user profile has been updated.
         *
         * @param userProfile Updated [UserProfile]
         * @return JSON message with opCode 8.
         */
        fun relayProfileUpdate(userProfile: UserProfile): String
        {
            val message = ProfileUpdateMessage(
                _id = userProfile._id.get()!!,
                userName = userProfile.userName.get(),
                symbolCode = userProfile.symbolCode.get()
            )
            val json = Json{
                serializersModule = SerializersModule {
                    contextual(ObjectId::class, ObjectIdSerializer)
                }
            }
            return json.encodeToString(message)
        }

        /**
         * Request user for start or stop of loc share. Message sent to client that will, based on [start], start or
         * stop location sharing.
         *
         * @param teamName TeamId of team whose team leader requested start/stop to location share
         * @param start Flag indicating if location share should start or stop
         * @return JSON message with opCode 26.
         */
        fun requestUserForStartOrStopOfLocShare(teamName: String, start: Boolean): String
        {
            return buildJsonObject {
                put(OPCODE,26)
                put(TeamEntry::_id.name,teamName)
                put("on",start)
            }.toString()
        }

        /**
         * Team profile. Message sent to clients when new team was created, existing team was updated, or when user
         * is syncing with server
         *
         * @param teamEntry [TeamEntry] of team that is sent
         * @return JSON message with opCode 20.
         */
        fun teamProfile(teamEntry: TeamEntry): String
        {
            val message = TeamProfileMessage(
                _id = teamEntry._id.get()!!,
                teamName = teamEntry.teamName.get(),
                teamIcon = teamEntry.teamIcon.get(),
                teamLocation = teamEntry.teamLocation.get(),
                teamLead = teamEntry.teamLead.get(),
                teamEntry = teamEntry.teamEntry.get()
            )
            val json = Json{
                serializersModule = SerializersModule {
                    contextual(ObjectId::class,ObjectIdSerializer)
                }
            }
            return json.encodeToString(message)
        }

        /**
         * Team deletion. Message sent to clients when team is deleted.
         *
         * @param id TeamId of team that is deleted
         * @param err Flag indicating that team could not be deleted on server
         * @return JSON message with opCode 22.
         */
        fun teamDeletion(id: String,err: Boolean): String
        {
            return buildJsonObject {
                put(OPCODE,22)
                if(!err) put(TeamEntry::_id.name,id)
            }.toString()
        }

        /**
         * Manage team roster. Message sent to all clients when user was added or removed from team.
         *
         * @param teamId TeamId of team whose member list is changing
         * @param profileId UserId of user that is added or removed from team.
         * @param adding Flag indicating if user is added or removed from team
         * @return JSON message with opCode 23.
         */
        fun manageTeamRoster(teamId: ObjectId,profileId: ObjectId,adding: Boolean): String
        {
            return buildJsonObject {
                put(OPCODE,23)
                put(TeamEntry::_id.name,teamId.toString())
                put(PROFILE_ID,profileId.toString())
                put(ADDING,adding)
            }.toString()
        }

        /**
         * Changing team leader. Method sent to clients when team leader has changed.
         *
         * @param teamEntry [TeamEntry] of team whose leader has changed
         * @param userProfile [UserProfile] of new team leader
         * @return JSON message with opcode 24.
         */
        fun changingTeamLeader(teamEntry: TeamEntry, userProfile: UserProfile): String
        {
            return buildJsonObject {
                put(OPCODE,24)
                put(TeamEntry::_id.name,teamEntry._id.get().toString())
                put("userId",userProfile._id.get().toString())
            }.toString()
        }

        /**
         * Update team. Message sent when team profile has changed
         *
         * @param teamEntry Updated profile
         * @return JSON message with opCode 25.
         */
        fun updateTeam(teamEntry: TeamEntry): String
        {
            return buildJsonObject {
                put(OPCODE,25)
                put(TeamEntry::_id.name,teamEntry._id.get().toString())
                put(TeamEntry::teamName.name,teamEntry.teamName.get())
                put(TeamEntry::teamIcon.name,teamEntry.teamIcon.get())
            }.toString()
        }

        /**
         * Resp err21. Message sent when user can not create team (This feature is not used)
         *
         * @param reason
         * @return JSON message with opCode 21
         */
        fun respErr21(reason: String): String
        {
            return buildJsonObject {
                put(OPCODE,21)
                put("reason",reason)
            }.toString()
        }

        /**
         * Request loc sh change. Message that is sent to client to toggle his location sharing.
         *
         * @param teamEntry Argument used when toggling teams location sharing
         * @return JSON message with opCode 28.
         */
        fun requestLocShChange(teamEntry: TeamEntry? = null): String
        {
            return buildJsonObject {
                put(OPCODE,28)
                if(teamEntry != null) put(TeamEntry::_id.name,teamEntry._id.get().toString())
            }.toString()
        }

        /**
         * Point creation result. Message sent to inform point creator if point was successfully created
         *
         * @param success Flag indicating if point was created successfully
         * @param serverId Points ID
         * @param reason Optional argument used to inform client why point creation failed
         * @return JSON message with opCode 41.
         */
        fun pointCreationResult(success: Boolean, serverId: String? = null, reason: String? = null): String
        {
            return buildJsonObject {
                put(OPCODE,41)
                put("success",success)
                put("serverId",serverId)
                if(!success)
                {
                    put("reason",reason)
                }
            }.toString()
        }

        /**
         * Point entry. Message sent to clients when new point was created, existing point was updated, or client is
         * syncing with server.
         *
         * @param pointEntry Point that is sent to client
         * @return JSON message with opCode 40.
         */
        fun pointEntry(pointEntry: PointEntry): String
        {
            val message = PointEntryMessage(
                serverId =   pointEntry._id.get().toString(),
                name = pointEntry.name.get(),
                description = pointEntry.description.get(),
                ownerId = pointEntry.ownerId.get(),
                files = pointEntry.files,
                symbol = pointEntry.symbol.get(),
                menuString = pointEntry.menusString.get(),
                location = pointEntry.location.get().getStorableLocation(),
                ownerName = pointEntry.ownerName.get()
            )

            return Json.encodeToString(message)
        }

        /**
         * Sync points message. Message sent to client when he is syncing his points with server database.
         *
         * @param list [List] of PointIDs that are present in server database.
         * @return JSON message with opCode 44.
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun syncPointsMessage(list: List<String>): String
        {
            return buildJsonObject {
                put(OPCODE,44)
                putJsonArray("ids"){
                    this.addAll(list)
                }
            }.toString()
        }

        /**
         * Point deletion. Message sent to clients when point is deleted.
         *
         * @param id PointID of point that is deleted
         * @return JSON message with opCode 42.
         */
        fun pointDeltion(id: String): String
        {
            return buildJsonObject {
                put(OPCODE,42)
                put("serverId",id)
            }.toString()
        }

        /**
         * Chat room creation. Message sent to clients when new chat room is created, existing chat room is updated, or
         * when user is syncing with server.
         *
         * @param storableChat [StorableChat] instance with chat room profile
         * @return JSON message with opCode 60.
         */
        fun chatRoomCreation(storableChat: StorableChat): String
        {
            return Json.encodeToString(storableChat.getCreationMessage())
        }

        /**
         * Delete chat room. Message sent when chat room is deleted.
         *
         * @param id ID of chat room that is deleted
         * @return JSON message with opCode 61.
         */
        fun deleteChatRoom(id: String): String
        {
            return buildJsonObject {
                put(OPCODE,61)
                put("_id",id)
            }.toString()
        }

        /**
         * Chat room message. Chat room message sent to all chat room members.
         *
         * @param storableChatMessage Chat message
         * @return JSON message with opCode 64.
         */
        fun chatRoomMessage(storableChatMessage: StorableChatMessage): String
        {
            return Json.encodeToString(storableChatMessage.getChatMessage())
        }

        /**
         * Fetched messages. Message sent to client when he fetches messages from given chat room containing those messages.
         *
         * @param list [List]<[StorableChatMessage]> with fetched messages
         * @return JSON message with opCode 65.
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fetchedMessages(list: List<StorableChatMessage>): String
        {
            return buildJsonObject {
                put(OPCODE,65)
                putJsonArray("messages"){
                    this.addAll(list.map { Json.encodeToString(it) })
                }
            }.toString()
        }

        /**
         * Failed to create chat room. Message sent to client when chat room could not be created.
         *
         * @param name Chat rooms name that user tried to create but that name is already in use
         * @return JSON message with opCode 66
         */
        fun failedToCreateChatRoom(name: String): String
        {
            return buildJsonObject {
                put(OPCODE,66)
                put("reason","This chat room name \"$name\" is already taken")
            }.toString()
        }
    }
}
