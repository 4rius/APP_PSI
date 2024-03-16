package com.example.app_psi.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.app_psi.DbConstants.ACTION_SERVICE_CREATED
import com.example.app_psi.DbConstants.CARDINALITY_DONE
import com.example.app_psi.DbConstants.DFL_PORT
import com.example.app_psi.DbConstants.INTERSECTION_STEP_1
import com.example.app_psi.DbConstants.INTERSECTION_STEP_2
import com.example.app_psi.DbConstants.INTERSECTION_STEP_F
import com.example.app_psi.DbConstants.KEYGEN_DONE
import com.example.app_psi.DbConstants.KEYGEN_ERROR
import com.example.app_psi.R
import com.example.app_psi.handlers.SchemeHandler
import com.example.app_psi.objects.Node
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.*


class NetworkService: Service() {

    lateinit var node: Node
    lateinit var id: String
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                KEYGEN_DONE -> {
                    sendNotification("Key generation done", "KEYGEN_DONE")
                }
                KEYGEN_ERROR -> {
                    sendNotification("Key generation error", "KEYGEN_ERROR")
                }
                INTERSECTION_STEP_1 -> {
                    sendNotification("Intersection step 1 done", "INTERSECTION STEP 1")
                }
                INTERSECTION_STEP_2 -> {
                    sendNotification("Intersection step 2 done", "INTERSECTION STEP 2")
                }
                INTERSECTION_STEP_F -> {
                    sendNotification("Find details on the results option", "INTERSECTION FOUND")
                }
                CARDINALITY_DONE -> {
                    sendNotification("Find details on the results option", "CARDINALITY FOUND")
                }
            }
        }
    }

    private fun sendNotification(s: String, t: String) {
        val notificationId = 1
        val channelId = "channel_id"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(t)
            .setContentText(s)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Channel description", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(notificationId, notificationBuilder.build())
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate() {
        super.onCreate()
        instance = this
        id = getLocalIp()!!
        val peers = ArrayList<String>()
        peers.add("192.168.1.155:5001")
        peers.add("192.168.1.2:5001")
        node = Node(id, DFL_PORT, peers)
        node.start()
        // Mandar un broadcast para que la MainActivity sepa que el servicio se ha creado
        val intent = Intent(ACTION_SERVICE_CREATED)
        sendBroadcast(intent)
        registerReceiver(receiver, IntentFilter(KEYGEN_DONE), RECEIVER_EXPORTED)
        registerReceiver(receiver, IntentFilter(KEYGEN_ERROR), RECEIVER_EXPORTED)
        registerReceiver(receiver, IntentFilter(INTERSECTION_STEP_1), RECEIVER_EXPORTED)
        registerReceiver(receiver, IntentFilter(INTERSECTION_STEP_2), RECEIVER_EXPORTED)
        registerReceiver(receiver, IntentFilter(INTERSECTION_STEP_F), RECEIVER_EXPORTED)
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

    override fun onDestroy() {
        super.onDestroy()
        node.stop()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {

        private var instance: NetworkService? = null

        fun getNode(): Node? {
            return instance?.node
        }

        fun getIntersectionHandler(): SchemeHandler {
            return instance?.node?.intersectionHandler ?: SchemeHandler()
        }

        fun getLastSeen(device: String): String {
            return instance?.node?.getLastSeen(device) ?: "Unknown"
        }

        fun pingDevice(device: String): Boolean {
            return instance?.node?.pingDevice(device) ?: false
        }

        fun getStatus(): String {
            return if (instance == null) "Inactive"
            else if (instance?.node?.isRunning == true) "Connected"
            else "Connecting"
        }

        fun findNetwork() {
            TODO("Not yet implemented")
        }

        fun disconnect() {
            TODO("Not yet implemented")
        }

        fun findIntersection(device: String): String {
            return instance?.node?.intPaillier(device) ?: "Error"
        }

        fun discoverPeers() {
            instance?.node?.discoverPeers()
        }

        fun findIntersectionDJ(s: String) {
            instance?.node?.intDamgardJurik(s) ?: "Error"
        }

        fun findIntersectionPaillierOPE(s: String) {
            instance?.node?.intPaillierOPE(s) ?: "Error"
        }

        fun findIntersectionDJOPE(s: String) {
            instance?.node?.intDamgardJurikOPE(s) ?: "Error"
        }

        fun findCardinalityPaillier(s: String) {
            instance?.node?.intPaillierOPECA(s) ?: "Error"
        }

        fun findCardinalityDJ(s: String) {
            instance?.node?.intDamgardJurikOPECA(s) ?: "Error"
        }

        fun launchTest(s: String) {
            instance?.node?.launchTest(s)
        }

    }
}