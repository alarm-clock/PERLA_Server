package com.jmb_bms_server.utils

import io.ktor.http.*
import java.lang.RuntimeException

class MissingParameter(message: String) : RuntimeException()