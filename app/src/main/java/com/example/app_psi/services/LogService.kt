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
import java.text.SimpleDateFormat
import java.util.Date

class LogService: Service() {

    lateinit var node: Node
    lateinit var id: String
    lateinit var realtimeDatabase: FirebaseDatabase
    private val LOG_INTERVAL = 10000L
    private val FB_RES_INTERVAL = 20000L

    override fun onCreate() {
        super.onCreate()
        instance = this
        realtimeDatabase = FirebaseDatabase.getInstance()
        node = NetworkService.getNode()!!
        id = node.id
        generalLog()
        resLog()
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

    private fun resLog() {
        val handler = Handler(Looper.getMainLooper())
        val resRunnable: Runnable = object : Runnable {
            @SuppressLint("SimpleDateFormat")
            override fun run() {
                val ramUsage: String = getRAMUsage()
                //val cpuUsage: String = getCPUUsage()
                val timestamp: String = SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(Date())
                // Mandamos la info a la bd
                sendResourceLog(id, timestamp, "Android","RAM", ramUsage)
                //sendResourceLog(id, timestamp, "CPU", cpuUsage)
                handler.postDelayed(this, FB_RES_INTERVAL)
            }
        }
        handler.post(resRunnable)
    }

    private fun sendResourceLog(id: String, timestamp: String, t: String, s: String, ramUsage: String) {
        // Lo subimos a la Firebase realtime database
        val formattedId = id.replace(".", "-")
        val ref = realtimeDatabase.getReference("logs/$formattedId/resources")
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        val androidVersion = android.os.Build.VERSION.RELEASE
        val formattedVersion = "$t $androidVersion"
        val log = hashMapOf(
            "id" to id,
            "timestamp" to timestamp,
            "version" to version,
            "type" to formattedVersion,
            "usage" to s,
            "value" to ramUsage
        )
        ref.push().setValue(log)
        Log.d(ContentValues.TAG, "Resources log sent to Firebase")
    }

    private fun getRAMUsage(): String {
        val memInfo = ActivityManager.MemoryInfo()
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        activityManager.getMemoryInfo(memInfo)
        val availableMem = memInfo.availMem / 0x100000L  // 0x100000L == 1048576L == 1024 * 1024 == 1MB
        val totalMem = memInfo.totalMem / 0x100000L
        val memUse = totalMem - availableMem
        return "$memUse MB / $totalMem MB - ${memUse * 100 / totalMem}%"
    }
    //private fun getCPUUsage(): String {} - De momento no se va a implementar por no poder leer el /proc/stat que era como se hacía pero se ha bloqueado por temas de seguridad.


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        fun logActivity(acitvityCode: String, time: Any, version: String) {
            val formattedId = NetworkService.getNode()?.id?.replace(".", "-")
            val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(Date())
            val ref = instance?.realtimeDatabase?.getReference("logs/$formattedId/activities")
            val log = hashMapOf(
                "id" to instance?.id,
                "timestamp" to timestamp,
                "version" to version,
                "type" to "Android " + android.os.Build.VERSION.RELEASE,
                "activity_code" to acitvityCode,
                "time" to time
            )
            ref?.push()?.setValue(log)
            Log.d(ContentValues.TAG, "Activity log sent to Firebase")
        }

        var instance: LogService? = null
        }
}