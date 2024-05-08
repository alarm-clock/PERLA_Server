/**
 * @file: MissingParameter.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing MissingParameter class
 */
package com.jmb_bms_server.utils

import java.lang.RuntimeException

/**
 * Missing parameter
 * @param message
 */
class MissingParameter(message: String) : RuntimeException()