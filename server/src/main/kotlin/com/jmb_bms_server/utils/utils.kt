/**
 * @file: utils.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing utility methods
 */
package com.jmb_bms_server.utils

import java.io.File

/**
 * Method that checks if all files with file names stored in [list] are uploaded
 *
 * @param list [List] of file names that will be checked
 * @return True if all files are uploaded to server else false
 */
fun checkIfAllFilesAreUploaded(list: List<String>): Boolean
{
    list.forEach {
        if( !File("${GetJarPath.currentWorkingDirectory}/files/$it").exists() ) return false
    }
    return true
}