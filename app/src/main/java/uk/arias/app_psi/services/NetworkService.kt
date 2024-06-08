package uk.arias.app_psi.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import uk.arias.app_psi.R
import uk.arias.app_psi.collections.DbConstants.ACTION_SERVICE_CREATED
import uk.arias.app_psi.collections.DbConstants.ACTION_STATUS_UPDATED
import uk.arias.app_psi.collections.DbConstants.CARDINALITY_DONE
import uk.arias.app_psi.collections.DbConstants.DFL_PORT
import uk.arias.app_psi.collections.DbConstants.INTERSECTION_STEP_1
import uk.arias.app_psi.collections.DbConstants.INTERSECTION_STEP_2
import uk.arias.app_psi.collections.DbConstants.INTERSECTION_STEP_F
import uk.arias.app_psi.collections.DbConstants.KEYGEN_DONE
import uk.arias.app_psi.collections.DbConstants.KEYGEN_ERROR
import uk.arias.app_psi.network.Node
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.Enumeration
import java.util.concurrent.ThreadPoolExecutor


class NetworkService: Service() {

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
                    sendNotification("Find details on the results section", "INTERSECTION FOUND")
                }
                CARDINALITY_DONE -> {
                    sendNotification("Find details on the results section", "CARDINALITY FOUND")
                }
            }
        }
    }

    private fun sendNotification(s: String, t: String) {
        Log.d("Notification", "Sending notification: $t - $s")
        val notificationId = 1
        val channelId = "Activities"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(t)
            .setContentText(s)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Da igual llamarlo las veces que quiera porque al tener el mismo id, no se crea de nuevo
        val channel = NotificationChannel(channelId, "Info about activities", NotificationManager.IMPORTANCE_DEFAULT)
        notificationManager.createNotificationChannel(channel)

        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private fun createNode(port: Int? = null) {
        if (getLocalIp() == null) {
            Toast.makeText(this, R.string.no_local_ip, Toast.LENGTH_LONG).show()
            return
        }
        id = getLocalIp()!!
        val peers = ArrayList<String>()
        // Peers can be added while creating the node, this can be useful when deploying on a large number of devices
        //peers.add("192.168.1.155")
        //peers.add("192.168.1.2")
        Node.createNode(id, port?: DFL_PORT, peers)
        Node.getInstance()?.start()
        val intent = Intent(ACTION_STATUS_UPDATED)
        sendBroadcast(intent)
    }

    private fun destroyNode() {
        Node.getInstance()?.stop()
        val intent = Intent(ACTION_STATUS_UPDATED)
        sendBroadcast(intent)
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate() {
        super.onCreate()
        instance = this
        createNode()
        // Mandar un broadcast para que la MainActivity sepa que el servicio se ha creado
        val intent = Intent(ACTION_SERVICE_CREATED)
        sendBroadcast(intent)
        registerReceiver(receiver, IntentFilter(KEYGEN_DONE), RECEIVER_EXPORTED)
        registerReceiver(receiver, IntentFilter(KEYGEN_ERROR), RECEIVER_EXPORTED)
        registerReceiver(receiver, IntentFilter(INTERSECTION_STEP_1), RECEIVER_EXPORTED)
        registerReceiver(receiver, IntentFilter(INTERSECTION_STEP_2), RECEIVER_EXPORTED)
        registerReceiver(receiver, IntentFilter(INTERSECTION_STEP_F), RECEIVER_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_REDELIVER_INTENT
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

    private fun isValidIP(peer: String): Boolean {
        // IPv4 or IPv6
        return peer.matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$".toRegex()) || peer.matches("^\\[.*]$".toRegex())
    }

    override fun onDestroy() {
        super.onDestroy()
        Node.getInstance()?.stop()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {

        private var instance: NetworkService? = null


        fun getNode(): Node? {
            return Node.getInstance()
        }

        fun getLastSeen(device: String): String {
            return getNode()?.getLastSeen(device) ?: "Unknown"
        }

        fun pingDevice(device: String): Boolean {
            return getNode()?.pingDevice(device) ?: false
        }

        fun getStatus(): String {
            return if (Node.getInstance() == null) "Inactive"
            else if (getNode()?.isRunning == true) "Connected"
            else "Connecting"
        }

        fun addPeer(peer: String) {
            if (getNode() == null) {
                Toast.makeText(instance, R.string.node_not_initialized, Toast.LENGTH_SHORT).show()
                return
            }
            when {
                peer.isEmpty() -> {
                    Toast.makeText(instance, R.string.empty_peer, Toast.LENGTH_SHORT).show()
                }
                peer == getNode()?.id -> {
                    Toast.makeText(instance, R.string.cannot_add_yourself, Toast.LENGTH_SHORT).show()
                }
                else -> {
                    if (instance?.isValidIP(peer) == true) {
                        getNode()?.addPeer(peer)
                        Toast.makeText(instance, "Added $peer", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(instance, R.string.invalid_ip, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        fun getExecutors(): ArrayList<ThreadPoolExecutor> {
            return Node.getInstance()?.executors ?: ArrayList()
        }

        fun connect(port: Int?) {
            if (getNode() == null) {
                instance?.createNode(port)
            }
            else {
                Toast.makeText(instance, R.string.node_already_initialized, Toast.LENGTH_SHORT).show()
            }
            Log.d("Node", "Node started")
        }

        fun disconnect() {
            if (getNode() != null) instance?.destroyNode()
            Log.d("Node", "Node destroyed")
        }

        fun findIntersectionPaillierDomain(device: String): String {
            return getNode()?.startIntersection(device, "Paillier", "PSI-Domain") ?: "Error"
        }

        fun discoverPeers() {
            if (getNode() == null) {
                Toast.makeText(instance, R.string.node_not_initialized, Toast.LENGTH_SHORT).show()
                return
            }
            getNode()?.discoverPeers()
        }

        fun findIntersectionDJDomain(s: String) {
            getNode()?.startIntersection(s, "DamgardJurik", "PSI-Domain") ?: "Error"
        }

        fun findIntersectionPaillierOPE(s: String) {
            getNode()?.startIntersection(s, "Paillier", "OPE")
        }

        fun findIntersectionDJOPE(s: String) {
            getNode()?.startIntersection(s, "DamgardJurik", "OPE")
        }

        fun findCardinalityPaillier(s: String) {
            getNode()?.startIntersection(s, "Paillier", "PSI-CA")
        }

        fun findCardinalityDJ(s: String) {
            getNode()?.startIntersection(s, "DamgardJurik", "PSI-CA")
        }

        fun launchTest(s: String, tr: Int? = null, impl: String? = null, type: String? = null) {
            getNode()?.launchTest(s, tr, impl, type)
        }

        fun keygen(s: String, bitLength: Int) {
            if (getNode() == null) {
                Toast.makeText(instance, R.string.node_not_initialized, Toast.LENGTH_SHORT).show()
                return
            }
            getNode()?.keygen(s, bitLength)
        }

    }
}