/**
 * @file: InitDialogReturn.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing InitDialogReturn class
 */
package com.jmb_bms_server

/**
 * Init dialog return
 *
 * @property restore Flag indicating if server should restore its state from database
 */
enum class InitDialogReturn(val restore: Boolean) {
    USE_DB(true),
    CLEAR_DB(false),
    EXIT(false)
}