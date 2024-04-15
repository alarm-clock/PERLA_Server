package com.jmb_bms_server.utils

import java.io.File

fun checkIfAllFilesAreUploaded(list: List<String>): Boolean
{
    list.forEach {
        if( !File("${GetJarPath.currentWorkingDirectory}/files/$it").exists() ) return false
    }
    return true
}