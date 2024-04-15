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


class Messages {

    companion object{

        const val OPCODE = "opCode"
        const val REASON = "reason"
        const val ID = "_id"
        const val LAT = "lat"
        const val LONG = "long"
        const val ADDING = "adding"
        const val PROFILE_ID = "profileId"

        fun helloThere(): String
        {
            val jsonObj = buildJsonObject{
                put(OPCODE,-1)
            }
            return jsonObj.toString()
        }
        fun bye(reason: String): String
        {
            return buildJsonObject {
                put(OPCODE,0)
                put(REASON,reason)
            }.toString()
        }
        fun userNameAlreadyInUse(): String
        {
            return buildJsonObject {
                put(OPCODE,1)
                put(ID,0)
            }.toString()
        }
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
        fun sendStopShLoc(id: String): String
        {
            return buildJsonObject {
                put(OPCODE, 3)
                put(ID,id)
                put(LAT,"stop")
                put(LONG,"stop")
            }.toString()
        }
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
        fun userDisconnected(id: String): String
        {
            return  buildJsonObject {
                put(OPCODE,7)
                put(ID,id)
            }.toString()
        }
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
        fun requestUserForStartOrStopOfLocShare(teamName: String, start: Boolean): String
        {
            return buildJsonObject {
                put(OPCODE,26)
                put(TeamEntry::_id.name,teamName)
                put("on",start)
            }.toString()
        }

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

        fun teamDeletion(id: String,err: Boolean): String
        {
            return buildJsonObject {
                put(OPCODE,22)
                if(!err) put(TeamEntry::_id.name,id)
            }.toString()
        }

        fun manageTeamRoster(teamId: ObjectId,profileId: ObjectId,adding: Boolean): String
        {
            return buildJsonObject {
                put(OPCODE,23)
                put(TeamEntry::_id.name,teamId.toString())
                put(PROFILE_ID,profileId.toString())
                put(ADDING,adding)
            }.toString()
        }

        fun changingTeamLeader(teamEntry: TeamEntry, userProfile: UserProfile): String
        {
            return buildJsonObject {
                put(OPCODE,24)
                put(TeamEntry::_id.name,teamEntry._id.get().toString())
                put("userId",userProfile._id.get().toString())
            }.toString()
        }

        fun updateTeam(teamEntry: TeamEntry): String
        {
            return buildJsonObject {
                put(OPCODE,25)
                put(TeamEntry::_id.name,teamEntry._id.get().toString())
                put(TeamEntry::teamName.name,teamEntry.teamName.get())
                put(TeamEntry::teamIcon.name,teamEntry.teamIcon.get())
            }.toString()
        }

        fun respErr21(reason: String): String
        {
            return buildJsonObject {
                put(OPCODE,21)
                put("reason",reason)
            }.toString()
        }

        //it is meant to work as toggle
        fun requestLocShChange(teamEntry: TeamEntry? = null): String
        {
            return buildJsonObject {
                put(OPCODE,28)
                if(teamEntry != null) put(TeamEntry::_id.name,teamEntry._id.get().toString())
            }.toString()
        }

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

        fun pointDeltion(id: String): String
        {
            return buildJsonObject {
                put(OPCODE,42)
                put("serverId",id)
            }.toString()
        }

        fun chatRoomCreation(storableChat: StorableChat): String
        {
            return Json.encodeToString(storableChat.getCreationMessage())
        }

        fun deleteChatRoom(id: String): String
        {
            return buildJsonObject {
                put(OPCODE,61)
                put("_id",id)
            }.toString()
        }

        fun chatRoomMessage(storableChatMessage: StorableChatMessage): String
        {
            return Json.encodeToString(storableChatMessage.getChatMessage())
        }

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

        fun failedToCreateChatRoom(name: String): String
        {
            return buildJsonObject {
                put(OPCODE,66)
                put("reason","This chat room name \"$name\" is already taken")
            }.toString()
        }

        //TODO update user profile messages so that chat will be present and also update init message so that also chats in which is user will be sent
    }
}
