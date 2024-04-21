package com.example.app_psi.services

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.example.app_psi.BuildConfig.VERSION_NAME
import com.example.app_psi.collections.DbConstants.DFL_DOMAIN
import com.example.app_psi.collections.DbConstants.DFL_SET_SIZE
import com.example.app_psi.collections.DbConstants.INTERSECTION_STEP_1
import com.example.app_psi.collections.DbConstants.INTERSECTION_STEP_2
import com.example.app_psi.collections.DbConstants.INTERSECTION_STEP_F
import com.example.app_psi.collections.DbConstants.KEYGEN_DONE
import com.example.app_psi.collections.DbConstants.LOG_INTERVAL
import com.example.app_psi.network.Node
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties


class LogService: Service() {

    lateinit var id: String
    lateinit var realtimeDatabase: FirebaseDatabase
    var authenticated = false
    var propsFile = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        fbAuth()
        realtimeDatabase = FirebaseDatabase.getInstance()
        id = Node.getInstance()?.id ?: "Unknown"
        generalLog()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_REDELIVER_INTENT
    }

    private fun generalLog() {
        val handler = Handler(Looper.getMainLooper())
        val logRunnable: Runnable = object : Runnable {
            @SuppressLint("SimpleDateFormat")
            override fun run() {
                val dateFormatted = SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(Date())
                val devices = NetworkService.getNode()?.devices
                val port = NetworkService.getNode()?.port
                val fullId = NetworkService.getNode()?.fullId
                var isRunning = NetworkService.getNode()?.isRunning.toString()
                isRunning = if (isRunning == "true") "Running"
                else "Stopped"
                val message = "[$dateFormatted] - ID: $fullId - Port: $port - Devices: $devices - Status: $isRunning"
                Log.d("Node", message)
                handler.postDelayed(this, LOG_INTERVAL)
            }
        }
        handler.post(logRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    fun toggleFirebaseAuth() {
        if (authenticated) {
            FirebaseAuth.getInstance().signOut()
            authenticated = false
        } else {
            fbAuth()
        }
    }

    private fun fbAuth() {
        // Este archivo no se sube a git, se debe añadir manualmente en la carpeta app/src/main/assets por seguridad
        try {
            val properties = Properties().apply { load(applicationContext.assets.open("FirebaseCredentialsAndroid.properties")) }
            val email = properties.getProperty("FIREBASE_EMAIL")
            val password = properties.getProperty("FIREBASE_PASSWORD")
            FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password).addOnCompleteListener {
                if (it.isSuccessful) {
                    Log.d("FirebaseAuth", "User logged in - Firebase RTDB logging enabled")
                    authenticated = true
                    logSetup(DFL_DOMAIN, DFL_SET_SIZE)
                } else {
                    Log.d("FirebaseAuth", "User not logged in - ${it.exception?.message} - Properties file may be corrupted or not contain a valid login.")
                    Log.d("FirebaseAuth", "Your logs will not be saved.")
                }
            }
            propsFile = true
        } catch (e: Exception) {
            Log.d("FirebaseAuth", "Properties file not found - Your logs will not be saved.")
            propsFile = false
        }
    }

    class LoggingObj {
        var ramUsage = ArrayList<Int>()
        var appRamUsage = ArrayList<Int>()
    }

    companion object {

        private val threadLocalLoggingObj = ThreadLocal<LoggingObj>()
        private val jobs = mutableMapOf<Thread, Job>()


        @SuppressLint("SimpleDateFormat")
        fun logSetup(domainSize: Int, setSize: Int) {
            if (!instance?.authenticated!!) return
            val formattedId = NetworkService.getNode()?.id?.replace(".", "-")
            val ref = instance?.realtimeDatabase?.getReference("logs/$formattedId/setup")
            val log = hashMapOf(
                "id" to instance?.id,
                "version" to VERSION_NAME,
                "type" to "Android " + android.os.Build.VERSION.RELEASE,
                "manufacturer" to android.os.Build.MANUFACTURER,
                "model" to android.os.Build.MODEL,
                "timestamp" to SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(Date()),
                "Domain" to domainSize,
                "Set_size" to setSize
            )
            ref?.push()?.setValue(log)
            Log.d("FirebaseRTDB", "Setup log sent to Firebase")
        }
        @SuppressLint("SimpleDateFormat")
        fun logActivity(acitvityCode: String, time: Any, peer: String?= null, cpuTime: Long) {
            if (!instance?.authenticated!!) {
                broadcaster(acitvityCode)
                return
            }
            val formattedId = NetworkService.getNode()?.id?.replace(".", "-")
            val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(Date())
            val ref = instance?.realtimeDatabase?.getReference("logs/$formattedId/activities")
            val cpuTimeF = cpuTime / 1_000_000.0
            val loggingObj = threadLocalLoggingObj.get()

            var avgRamUsage = 0
            var peakRamUsage = 0
            var appAvgRamUsage = 0
            var peakAppRamUsage = 0

            if (loggingObj != null) {
                synchronized(loggingObj.ramUsage) {
                    avgRamUsage = if (loggingObj.ramUsage.isNotEmpty()) loggingObj.ramUsage.sum() / loggingObj.ramUsage.size else 0
                    peakRamUsage = loggingObj.ramUsage.maxOrNull() ?: 0
                    appAvgRamUsage = if (loggingObj.appRamUsage.isNotEmpty()) loggingObj.appRamUsage.sum() / loggingObj.appRamUsage.size else 0
                    peakAppRamUsage = loggingObj.appRamUsage.maxOrNull() ?: 0
                }
            }

            val log = hashMapOf(
                "id" to instance?.id,
                "timestamp" to timestamp,
                "version" to VERSION_NAME,
                "Details" to "Android " + android.os.Build.VERSION.RELEASE + " - " + android.os.Build.MANUFACTURER + " - " + android.os.Build.MODEL,
                "activity_code" to acitvityCode,
                "time" to time,
                "Avg_RAM" to "$avgRamUsage MB",
                "Peak_RAM" to "$peakRamUsage MB",
                "App_Avg_RAM" to "$appAvgRamUsage MB",
                "App_Peak_RAM" to "$peakAppRamUsage MB",
                "CPU_time" to "$cpuTimeF ms",
            )

            if (peer != null) {
                log["peer"] = peer
            }

            ref?.push()?.setValue(log)
            Log.d("FirebaseRTDB", "Activity log sent to Firebase - Thread: ${Thread.currentThread().name}")
        }



        private fun broadcaster(activityCode: String) {
            when {
                activityCode.startsWith("KEYGEN") -> broadcastThis(KEYGEN_DONE)
                activityCode.startsWith("INTERSECTION") && activityCode.endsWith("1") -> broadcastThis(INTERSECTION_STEP_1)
                activityCode.startsWith("INTERSECTION") && activityCode.endsWith("2") -> broadcastThis(INTERSECTION_STEP_2)
                activityCode.startsWith("INTERSECTION") && activityCode.endsWith("F") -> broadcastThis(INTERSECTION_STEP_F)
            }
        }


        private fun broadcastThis(acitvityCode: String) {
            val intent = Intent(acitvityCode)
            instance?.sendBroadcast(intent)
        }

        @SuppressLint("SimpleDateFormat")
        fun logResult(result: List<Int>?, size: Int, peer: String, implementation: String) {
            if (!instance?.authenticated!!) {
                broadcaster("INTERSECTION_STEP_F")
                return
            }
            val formattedId = NetworkService.getNode()?.id?.replace(".", "-")
            val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(Date())
            val ref = instance?.realtimeDatabase?.getReference("logs/$formattedId/intersection_results")
            val log: HashMap<String, Any?> = hashMapOf(
                "id" to instance?.id,
                "timestamp" to timestamp,
                "version" to VERSION_NAME,
                "type" to "Android " + android.os.Build.VERSION.RELEASE,
                "peer" to peer,
                "implementation" to implementation,
                "size" to size,
            )
            if (result != null) {
                log["result"] = result
                broadcaster("INTERSECTION_STEP_F")
            } else {
                broadcaster("CARDINALITY_DONE")
            }
            ref?.push()?.setValue(log)
            Log.d("FirebaseRTDB", "Result log sent to Firebase")
        }
        private suspend fun getRamUsage(): Int? = withContext(Dispatchers.IO) {
            val activityManager = instance?.getSystemService(ACTIVITY_SERVICE) as? ActivityManager
            activityManager?.let {
                val memInfo = ActivityManager.MemoryInfo()
                it.getMemoryInfo(memInfo)
                val availableMem = memInfo.availMem / 0x100000L
                val totalMem = memInfo.totalMem / 0x100000L
                val memUse = totalMem - availableMem
                memUse.toInt()
            }
        }

        private suspend fun getAppRamUsage(): Int? = withContext(Dispatchers.IO) {
            val activityManager = instance?.getSystemService(ACTIVITY_SERVICE) as? ActivityManager
            activityManager?.let {
                val memInfo = ActivityManager.MemoryInfo()
                it.getMemoryInfo(memInfo)
                val pid = android.os.Process.myPid()
                val memoryInfo = it.getProcessMemoryInfo(intArrayOf(pid))
                memoryInfo[0].totalPss / 1024
            }
        }


        fun startLogging() {
            startLoggingRam()
        }

        fun stopLogging() {
            stopLoggingRam()
        }

        private fun startLoggingRam() {
            val loggingObj = LoggingObj()
            threadLocalLoggingObj.set(loggingObj)
            val job = CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    val ram = getRamUsage()
                    val ramApp = getAppRamUsage()
                    loggingObj.ramUsage.add(ram ?: 0)
                    loggingObj.appRamUsage.add(ramApp ?: 0)
                    delay(100)
                }
            }
            Log.d("Threading", "Logging started for thread ${Thread.currentThread().name}")
            Log.d("LogService", "LoggingObj: $loggingObj")
            jobs[Thread.currentThread()] = job
        }

        private fun stopLoggingRam() {
            jobs[Thread.currentThread()]?.cancel()
            jobs.remove(Thread.currentThread())
            Log.d("Threading", "Logging stopped for thread ${Thread.currentThread().name}")
        }



        var instance: LogService? = null  // Singleton
        }
}