package com.example.app_psi.services

import android.annotation.SuppressLint
import android.app.Service
import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.util.Log
import com.example.app_psi.objects.Node
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.Enumeration


class NetworkService: Service() {

    lateinit var node: Node
    lateinit var id: String
    private var handler: Handler? = null
    private val LOG_INTERVAL = 10000L

    override fun onCreate() {
        super.onCreate()
        instance = this
        id = getLocalIp()!!
        val peers = listOf("192.168.1.112:5001", "192.168.1.43:5001", "192.168.1.3:5001")
        node = Node(id, 5001, peers)
        node.start()
        generalLog()
        // Mandar un broadcast para que la MainActivity sepa que el servicio se ha creado
        val intent = Intent(ACTION_SERVICE_CREATED)
        sendBroadcast(intent)
    }

    private fun generalLog() {
        handler = Handler()
        val logRunnable = object : Runnable {
            @SuppressLint("SimpleDateFormat")
            override fun run() {
                val dateFormatted = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(java.util.Date())
                val devices = getNode()?.devices
                val port = getNode()?.port
                val fullId = getNode()?.fullId
                var isRunning = getNode()?.isRunning.toString()
                isRunning = if (isRunning == "true") "Running"
                else "Stopped"
                val message = "[$dateFormatted] - ID: $fullId - Port: $port - Devices: $devices - Status: $isRunning"
                Log.d(TAG, message)

                handler?.postDelayed(this, LOG_INTERVAL)
            }
        }

        handler?.postDelayed(logRunnable, LOG_INTERVAL)
    }

    private fun getLocalIp(): String? {
        try {
            val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface: NetworkInterface = interfaces.nextElement()
                val addresses: Enumeration<InetAddress> = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr: InetAddress = addresses.nextElement()
                    if (!addr.isLoopbackAddress && !addr.isLinkLocalAddress && addr.isSiteLocalAddress) {
                        // Si es ipv6, lo tenemos que devolver con corchetes
                        if (addr.hostAddress?.contains(":") == true) {
                            return "[${addr.hostAddress}]"
                        }
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: SocketException) {
            e.printStackTrace()
        }
        return null
    }

    fun getDevices(): List<String> {
        return node.devices
    }

    override fun onDestroy() {
        super.onDestroy()
        node.join()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {

        private var instance: NetworkService? = null
        const val ACTION_SERVICE_CREATED = "com.example.app_psi.receivers.ACTION_SERVICE_CREATED"
        const val ACTION_STATUS_UPDATED = "com.example.app_psi.receivers.ACTION_STATUS_UPDATED"

        fun getNode(): Node? {
            return instance?.node
        }
        fun getLastSeen(device: String): String {
            return instance?.node?.getLastSeen(device) ?: "Unknown"
        }

        fun pingDevice(device: String): Boolean {
            return instance?.node?.pingDevice(device) ?: false
        }

        fun sendSmallMessage(device: String) {
            TODO("Not yet implemented")
        }

        fun getStatus(): String {
            return if (instance == null) "Inactive"
            else if (instance?.node?.isRunning == true) "Connected"
            else "Connecting"
        }

        fun sendLargeMessageToAll() {
            TODO("Not yet implemented")
        }

        fun sendSmallMessageToAll() {
            TODO("Not yet implemented")
        }

        fun findNetwork() {
            TODO("Not yet implemented")
        }

        fun disconnect() {
            TODO("Not yet implemented")
        }

        fun findIntersection(device: String): String {
            return instance?.node?.paillierIntersection(device) ?: "Error"
        }

    }
}