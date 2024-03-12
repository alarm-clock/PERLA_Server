package com.jmb_bms_server.data

import io.ktor.websocket.*
import org.bson.types.ObjectId
import java.util.concurrent.atomic.AtomicReference

data class UserSession(val userId: AtomicReference<ObjectId?>, var session: AtomicReference<DefaultWebSocketSession?> = AtomicReference(null))