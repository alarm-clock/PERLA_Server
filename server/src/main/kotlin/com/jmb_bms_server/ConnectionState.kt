package com.jmb_bms_server

enum class ConnectionState() {
    NOT_CONNECTED,
    NEGOTIATING,
    CONNECTED,
    RECONNECTING,
    ERROR
}