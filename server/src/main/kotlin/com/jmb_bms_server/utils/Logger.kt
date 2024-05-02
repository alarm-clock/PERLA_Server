package com.jmb_bms_server.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.locks.ReentrantLock

object Logger {

    private val lock = ReentrantLock()


    fun log(message: String, userName: String, level: Int = 0)
    {
        CoroutineScope(Dispatchers.IO).launch {
            val dateString = "[${SimpleDateFormat("yyyy.MM.dd_HH-mm-ss", Locale.GERMAN).format(Date())}]"

            val levelString = " -$level- "

            val wholeString = dateString + levelString + " User ${userName}:" + message + "\n"

            lock.lock()


            val writer = if(File("${GetJarPath.currentWorkingDirectory}/config/log.log").exists()){
                FileWriter("${GetJarPath.currentWorkingDirectory}/config/log.log",true)
            } else
            {
                val file = File("${GetJarPath.currentWorkingDirectory}/config/log.log")
                FileWriter(file,true)
            }
            writer.append(wholeString)
            writer.flush()
            writer.close()

            lock.unlock()
        }
    }
}