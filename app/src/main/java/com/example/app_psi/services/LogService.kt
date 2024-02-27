package com.example.app_psi.services

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.example.app_psi.DbConstants.DFL_DOMAIN
import com.example.app_psi.DbConstants.DFL_SET_SIZE
import com.example.app_psi.DbConstants.INTERSECTION_STEP_1
import com.example.app_psi.DbConstants.INTERSECTION_STEP_2
import com.example.app_psi.DbConstants.INTERSECTION_STEP_F
import com.example.app_psi.DbConstants.KEYGEN_DONE
import com.example.app_psi.DbConstants.LOG_INTERVAL
import com.example.app_psi.DbConstants.VERSION
import com.example.app_psi.objects.Node
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger


class LogService: Service() {

    private lateinit var node: Node
    lateinit var id: String
    lateinit var realtimeDatabase: FirebaseDatabase

    override fun onCreate() {
        super.onCreate()
        instance = this
        realtimeDatabase = FirebaseDatabase.getInstance()
        node = NetworkService.getNode()!!
        id = node.id
        generalLog()
        logSetup(DFL_DOMAIN, DFL_SET_SIZE)
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
                Log.d(ContentValues.TAG, message)
                handler.postDelayed(this, LOG_INTERVAL)
            }
        }
        handler.post(logRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    class LoggingObj {
        var ram_usage = ArrayList<Int>()
        var app_ram_usage = ArrayList<Int>()
        var avg_ram_usage = 0
        var app_avg_ram_usage = 0
        var peak_ram_usage = 0
        var peak_app_ram_usage = 0
    }

    companion object {

        private val threadLocalLoggingObj = ThreadLocal<LoggingObj>()
        private val jobs = mutableMapOf<Thread, Job>()


        @SuppressLint("SimpleDateFormat")
        fun logSetup(domainSize: Int, setSize: Int) {
            val formattedId = NetworkService.getNode()?.id?.replace(".", "-")
            val ref = instance?.realtimeDatabase?.getReference("logs/$formattedId/setup")
            val log = hashMapOf(
                "id" to instance?.id,
                "version" to VERSION,
                "type" to "Android " + android.os.Build.VERSION.RELEASE,
                "timestamp" to SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(Date()),
                "Domain" to domainSize,
                "Set_size" to setSize
            )
            ref?.push()?.setValue(log)
            Log.d(ContentValues.TAG, "Setup log sent to Firebase")
        }
        @SuppressLint("SimpleDateFormat")
        fun logActivity(acitvityCode: String, time: Any, version: String, peer: String?= null, cpuTime: Long) {
            val formattedId = NetworkService.getNode()?.id?.replace(".", "-")
            val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(Date())
            val ref = instance?.realtimeDatabase?.getReference("logs/$formattedId/activities")
            val cpuTimeF = cpuTime / 1_000_000.0
            val loggingObj = threadLocalLoggingObj.get()

            val log = hashMapOf(
                "id" to instance?.id,
                "timestamp" to timestamp,
                "version" to version,
                "type" to "Android " + android.os.Build.VERSION.RELEASE,
                "activity_code" to acitvityCode,
                "time" to time,
                "Avg_RAM" to "${loggingObj?.avg_ram_usage} MB",
                "Peak_RAM" to "${loggingObj?.peak_ram_usage} MB",
                "App_Avg_RAM" to "${loggingObj?.app_avg_ram_usage} MB",
                "App_Peak_RAM" to "${loggingObj?.peak_app_ram_usage} MB",
                "CPU_time" to "$cpuTimeF ms",
            )

            if (peer != null) {
                log["peer"] = peer
            }

            ref?.push()?.setValue(log)
            Log.d(ContentValues.TAG, "Activity log sent to Firebase - Thread: ${Thread.currentThread().name}")
            broadcaster(acitvityCode)
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
        fun logResult(result: List<Int>, size: Int, version: String, peer: String, implementation: String) {
            val formattedId = NetworkService.getNode()?.id?.replace(".", "-")
            val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(Date())
            val ref = instance?.realtimeDatabase?.getReference("logs/$formattedId/intersection_results")
            val log = hashMapOf(
                "id" to instance?.id,
                "timestamp" to timestamp,
                "version" to version,
                "type" to "Android " + android.os.Build.VERSION.RELEASE,
                "peer" to peer,
                "implementation" to implementation,
                "result" to result,
                "size" to size,
            )
            ref?.push()?.setValue(log)
            Log.d(ContentValues.TAG, "Intersection result log sent to Firebase")
            broadcaster("INTERSECTION_STEP_F")
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
                    loggingObj.ram_usage.add(ram ?: 0)
                    loggingObj.app_ram_usage.add(ramApp ?: 0)
                    delay(100)
                }
            }
            Log.d(ContentValues.TAG, "Logging started for thread ${Thread.currentThread().name}")
            Log.d(ContentValues.TAG, "LoggingObj: $loggingObj")
            jobs[Thread.currentThread()] = job
        }

        private fun stopLoggingRam() {
            val loggingObj = threadLocalLoggingObj.get()
            Log.d(ContentValues.TAG, "RAW RAM USAGE: ${loggingObj?.ram_usage}")
            if (loggingObj != null) {
                synchronized(loggingObj.ram_usage) {
                    loggingObj.avg_ram_usage = if (loggingObj.ram_usage.isNotEmpty()) loggingObj.ram_usage.sum() / loggingObj.ram_usage.size else 0
                    loggingObj.peak_ram_usage = loggingObj.ram_usage.maxOrNull() ?: 0
                    loggingObj.app_avg_ram_usage = if (loggingObj.app_ram_usage.isNotEmpty()) loggingObj.app_ram_usage.sum() / loggingObj.app_ram_usage.size else 0
                    loggingObj.peak_app_ram_usage = loggingObj.app_ram_usage.maxOrNull() ?: 0
                }
            }
            jobs[Thread.currentThread()]?.cancel()
            jobs.remove(Thread.currentThread())
            Log.d(ContentValues.TAG, "Logging stopped for thread ${Thread.currentThread().name}")
        }



        var instance: LogService? = null  // Singleton
        }
}