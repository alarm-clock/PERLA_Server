package com.jmb_bms_server.utils

import io.ktor.http.ContentDisposition.Companion.File
import io.ktor.http.content.*
import java.io.File
import java.util.UUID

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