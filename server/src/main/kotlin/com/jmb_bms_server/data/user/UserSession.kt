/**
 * @file: UserSession.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing UserSession class
 */
package com.jmb_bms_server.data.user

import io.ktor.websocket.*
import org.bson.types.ObjectId
import java.util.concurrent.atomic.AtomicReference

/**
 * Class that holds reference to websocket [session] of user identified by [userId]
 *
 * @property userId ID of user whose [session] is referenced in this instance
 * @property session [DefaultWebSocketSession] that can be used to send messages to client
 */
data class UserSession(val userId: AtomicReference<ObjectId?>, var session: AtomicReference<DefaultWebSocketSession?> = AtomicReference(null))