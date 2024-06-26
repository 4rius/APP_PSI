package uk.arias.app_psi.collections
object DbConstants {

    // NetworkService
    const val ACTION_STATUS_UPDATED = "com.example.app_psi.receivers.ACTION_STATUS_UPDATED"
    const val ACTION_SERVICE_CREATED = "com.example.app_psi.receivers.ACTION_SERVICE_CREATED"
    const val DFL_PORT = 5001
    const val KEYGEN_DONE = "com.example.app_psi.receivers.KEYGEN_DONE"
    const val KEYGEN_ERROR = "com.example.app_psi.receivers.KEYGEN_ERROR"
    const val INTERSECTION_STEP_1 = "com.example.app_psi.receivers.INTERSECTION_STEP_1"
    const val INTERSECTION_STEP_2 = "com.example.app_psi.receivers.INTERSECTION_STEP_2"
    const val INTERSECTION_STEP_F = "com.example.app_psi.receivers.INTERSECTION_STEP_F"
    const val CARDINALITY_DONE = "com.example.app_psi.receivers.CARDINALITY_DONE"

    // Node
    const val DFL_BIT_LENGTH_PAILLIER = 2048
    const val DFL_BIT_LENGTH_DAMGARD = 2048
    const val DFL_DOMAIN = 500
    const val DFL_SET_SIZE = 50
    const val DFL_EXPANSION_FACTOR = 2
    const val TEST_ROUNDS = 20 // 120 operations
    const val NODE_INIT = "Node already initialized - use getInstance() instead of createNode() or stop the current node first"

    // LogService
    const val LOG_INTERVAL = 10000L
}