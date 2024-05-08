/**
 * @file: TransactionState.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing TransactionState class
 */
package com.jmb_bms_server.utils

/**
 * Transaction states
 */
enum class TransactionState {
    IN_PROGRESS,
    FINISHED,
    FAILED,
}