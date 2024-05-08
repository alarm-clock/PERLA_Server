/**
 * @file: ConnectionState.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing ConnectionState class
 */
package com.jmb_bms_server

/**
 * Connection state
 */
enum class ConnectionState() {
    NOT_CONNECTED,
    NEGOTIATING,
    CONNECTED,
    RECONNECTING,
    ERROR
}