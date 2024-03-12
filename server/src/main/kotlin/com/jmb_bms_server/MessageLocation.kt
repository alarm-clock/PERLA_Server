package com.jmb_bms_server

import kotlinx.serialization.Serializable

@Serializable
data class MessageLocation(var lat: Double, var long: Double)