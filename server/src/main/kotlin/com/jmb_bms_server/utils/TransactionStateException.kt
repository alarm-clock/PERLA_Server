/**
 * @file: TransactionStateException.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing TransactionStateException class
 */
package com.jmb_bms_server.utils

/**
 * Transaction state exception
 * @param message
 */
class TransactionStateException(message: String) : RuntimeException(message)