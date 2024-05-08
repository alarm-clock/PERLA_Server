/**
 * @file: SaveFile.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing extension method for file item
 */
package com.jmb_bms_server.utils

import io.ktor.http.content.*
import java.io.File

/**
 * Extension method for [PartData.FileItem] that saves file item into file on [path]
 *
 * @param path Path where file should be stored
 * @param userId ID of user who is uploading file that will be added as prefix to file name
 * @return [String] with new file name
 */
fun PartData.FileItem.save(path: String,userId: String): String
{

    val fileName = "$userId-$originalFileName"
    if(File("$path/files/$fileName").exists()) throw FileExistsException()

    val fileBytes = streamProvider().readBytes()
    val folder = File("$path/files")
    folder.mkdir()

    File("$path/files/$fileName").writeBytes(fileBytes)
    return fileName
}