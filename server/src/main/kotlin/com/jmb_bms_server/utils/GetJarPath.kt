/**
 * @file: GetJarPath.kt
 * @author: Jozef Michal Bukas <xbukas00@stud.fit.vutbr.cz,jozefmbukas@gmail.com>
 * Description: File containing GetJarPath object
 */
package com.jmb_bms_server.utils

/**
 * Object for method to store paths relative to jar

 */
object GetJarPath {

    /**
     * Current working directory of running jar
     */
    val currentWorkingDirectory = System.getProperty("user.dir")
}