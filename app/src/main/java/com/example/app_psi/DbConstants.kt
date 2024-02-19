package com.example.app_psi

class DbConstants {

    object DbConstants {

        // NetworkService
        const val ACTION_STATUS_UPDATED = "com.example.app_psi.receivers.ACTION_STATUS_UPDATED"
        const val ACTION_SERVICE_CREATED = "com.example.app_psi.receivers.ACTION_SERVICE_CREATED"
        const val DFL_PORT = 5001

        // Node
        const val VERSION = "1.1 - DEV"
        const val DFL_BIT_LENGTH = 128
        const val DFL_DOMAIN = 40
        const val DFL_SET_SIZE = 10
        const val DFL_EXPANSION_FACTOR = 2

        // LogService
        const val LOG_INTERVAL = 10000L
    }
}