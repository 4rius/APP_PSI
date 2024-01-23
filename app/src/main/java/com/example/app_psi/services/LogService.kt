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
                handler.postDelayed(this, LOG_INTERVAL)
            }
        }
        handler.post(logRunnable)
    }
    //private fun getCPUUsage(): String {} - De momento no se va a implementar por no poder leer el /proc/stat que era como se hacía pero se ha bloqueado por temas de seguridad.


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
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
                    "RAM" to getRamUsage()
                )
                ref?.push()?.setValue(log)
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
                "RAM" to getRamUsage()
            )
            ref?.push()?.setValue(log)
            Log.d(ContentValues.TAG, "Activity log sent to Firebase")
        }

        private fun getRamUsage(): String {
            val memInfo = ActivityManager.MemoryInfo()
            val activityManager = instance?.getSystemService(ACTIVITY_SERVICE) as ActivityManager
            activityManager.getMemoryInfo(memInfo)
            val availableMem = memInfo.availMem / 0x100000L  // 0x100000L == 1048576L == 1024 * 1024 == 1MB
            val totalMem = memInfo.totalMem / 0x100000L
            val memUse = totalMem - availableMem
            return "$memUse MB / $totalMem MB - ${memUse * 100 / totalMem}%"
        }

        var instance: LogService? = null
        }
}