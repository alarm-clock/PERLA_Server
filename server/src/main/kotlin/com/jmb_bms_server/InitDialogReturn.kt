package com.jmb_bms_server

enum class InitDialogReturn(val restore: Boolean) {
    USE_DB(true),
    CLEAR_DB(false),
    EXIT(false)
}