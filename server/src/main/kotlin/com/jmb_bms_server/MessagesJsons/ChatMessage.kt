/**
 * @file: ChatMessage.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing ChatMessage class
 */
package com.jmb_bms_server.MessagesJsons

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Chat message
 *
 * @property _id ID of message unique to for given [chatId]. Also, messages are ordered by this property
 * @property chatId ID of chat room in which this message was sent
 * @property userName Username of user who sent this message
 * @property userSymbol Symbol code that user had when he sent this message
 * @property text Message text
 * @property files [List] of attached file names
 * @property points [List] of attached points
 * @property opCode Message opCode. Don't change unless it is used in another message!!
 */
@Serializable
data class ChatMessage @OptIn(ExperimentalSerializationApi::class) constructor(
    val _id: Long,
    val chatId: String,
    val userName: String,
    val userSymbol: String,
    val text: String,
    val files: List<String>,
    val points: List<String>,
    @EncodeDefault val opCode: Long = 64
)
