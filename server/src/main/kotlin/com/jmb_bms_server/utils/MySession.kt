/**
 * @file: MySession.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing MySession class
 */
package com.jmb_bms_server.utils

/**
 * Class into which http header SESSION is parsed
 *
 * @property userId ID of user that made the request
 */
data class MySession(val userId: String)
