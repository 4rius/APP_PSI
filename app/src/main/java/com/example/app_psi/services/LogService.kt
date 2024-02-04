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
import com.example.app_psi.objects.Node
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


class LogService: Service() {

    private lateinit var node: Node
    lateinit var id: String
    lateinit var realtimeDatabase: FirebaseDatabase
    private val logInterval = 10000L

    override fun onCreate() {
        super.onCreate()
        instance = this
        realtimeDatabase = FirebaseDatabase.getInstance()
        node = NetworkService.getNode()!!
        id = node.id
        generalLog()
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
                handler.postDelayed(this, logInterval)
            }
        }
        handler.post(logRunnable)
    }
    //private fun getCPUUsage(): String {} - De momento no se va a implementar por no poder leer el /proc/stat que era como se hacía pero se ha bloqueado por temas de seguridad.


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {

        private var isLoggingCPU = false
        private var cpu_usage = ArrayList<Float>()
        private var ram_usage = ArrayList<Int>()
        private var app_ram_usage = ArrayList<Int>()
        private var avg_cpu_time: Float = 0.0F
        private var avg_ram_usage = 0
        private var app_avg_ram_usage = 0
        private var peak_cpu_time: Float = 0.0F
        private var peak_ram_usage = 0
        private var peak_app_ram_usage = 0
        private var ram_job: Job? = null

        private fun clean() {
            isLoggingCPU = false
            cpu_usage = ArrayList()
            ram_usage = ArrayList()
            app_ram_usage = ArrayList()
            avg_cpu_time = 0.0F
            avg_ram_usage = 0
            app_avg_ram_usage = 0
            peak_cpu_time = 0.0F
            peak_ram_usage = 0
            peak_app_ram_usage = 0
        }
        @SuppressLint("SimpleDateFormat")
        fun logActivity(acitvityCode: String, time: Any, version: String, peer: String?= null) {
            val formattedId = NetworkService.getNode()?.id?.replace(".", "-")
            val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(Date())
            val ref = instance?.realtimeDatabase?.getReference("logs/$formattedId/activities")
            if (peer != null) {
                val log = hashMapOf(
                    "id" to instance?.id,
                    "timestamp" to timestamp,
                    "version" to version,
                    "type" to "Android " + android.os.Build.VERSION.RELEASE,
                    "activity_code" to acitvityCode,
                    "peer" to peer,
                    "time" to time,
                    "Avg_RAM" to getRamInfo(),
                    "Peak_RAM" to "$peak_ram_usage MB",
                    "App_Avg_RAM" to "$app_avg_ram_usage MB",
                    "App_Peak_RAM" to "$peak_app_ram_usage MB",
                    "Avg_CPU_time" to "$avg_cpu_time ms",
                    "Peak_CPU_time" to "$peak_cpu_time ms"
                )
                ref?.push()?.setValue(log)
                clean()
                Log.d(ContentValues.TAG, "Activity log sent to Firebase")
                return
            }
            val log = hashMapOf(
                "id" to instance?.id,
                "timestamp" to timestamp,
                "version" to version,
                "type" to "Android " + android.os.Build.VERSION.RELEASE,
                "activity_code" to acitvityCode,
                "time" to time,
                "Avg_RAM" to getRamInfo(),
                "Peak_RAM" to "$peak_ram_usage MB",
                "App_Avg_RAM" to "$app_avg_ram_usage MB",
                "App_Peak_RAM" to "$peak_app_ram_usage MB",
                "Avg_CPU_time" to "$avg_cpu_time ms",
                "Peak_CPU_time" to "$peak_cpu_time ms"
            )
            ref?.push()?.setValue(log)
            clean()
            Log.d(ContentValues.TAG, "Activity log sent to Firebase")
        }

        private fun getRamInfo(): String {
            val memInfo = ActivityManager.MemoryInfo()
            val activityManager = instance?.getSystemService(ACTIVITY_SERVICE) as ActivityManager
            activityManager.getMemoryInfo(memInfo)
            val totalMem = memInfo.totalMem / 0x100000L  // 0x100000L == 1048576L == 1024 * 1024 == 1MB
            totalMem.toFloat()
            return "$avg_ram_usage MB / $totalMem MB"
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

        private fun getCpuTime(): Float {
            // Obtiene el tiempo de CPU al inicio
            val startTime = android.os.Debug.threadCpuTimeNanos()
            Thread.sleep(100)
            // Obtiene el tiempo de CPU al final
            val endTime = android.os.Debug.threadCpuTimeNanos()
            // Calcula el tiempo de CPU consumido durante el intervalo
            val cpuTime = endTime - startTime
            return cpuTime / 1e6f  // 1e6f == 1000000f == 1000 * 1000 == 1 ms
        }


        fun startLogging() {
            isLoggingCPU = true
            startLoggingCpu()
            startLoggingRam()
        }

        fun stopLogging() {
            isLoggingCPU = false
            stopLoggingCpu()
            stopLoggingRam()
        }

        private fun startLoggingCpu() {
            Thread {
                while (true) {
                    val cpu = getCpuTime()
                    synchronized(cpu_usage) {
                        cpu_usage.add(cpu)
                    }
                    if (!isLoggingCPU) break
                    Thread.sleep(100)
                }
            }.start()
        }

        private fun startLoggingRam() {
            ram_job = CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    val ram = getRamUsage()
                    val ramApp = getAppRamUsage()
                    synchronized(ram_usage) {
                        ram?.let { ram_usage.add(it) }
                        ramApp?.let { app_ram_usage.add(it) }
                    }
                    delay(100)
                }
            }
        }

        private fun stopLoggingCpu() {
            synchronized(cpu_usage) {  // Synchronize to prevent concurrent modification
                // 2 decimal places
                avg_cpu_time = (cpu_usage.sum() / cpu_usage.size)
                peak_cpu_time = cpu_usage.maxOrNull()!!
            }
        }

        private fun stopLoggingRam() {
            ram_job?.cancel()
            synchronized(ram_usage) {
                avg_ram_usage = if (ram_usage.isNotEmpty()) ram_usage.sum() / ram_usage.size else 0
                peak_ram_usage = ram_usage.maxOrNull() ?: 0
                app_avg_ram_usage = if (app_ram_usage.isNotEmpty()) app_ram_usage.sum() / app_ram_usage.size else 0
                peak_app_ram_usage = app_ram_usage.maxOrNull() ?: 0
            }
        }



        var instance: LogService? = null
        }
}