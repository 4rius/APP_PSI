package com.example.app_psi.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.app_psi.DbConstants.DbConstants.ACTION_SERVICE_CREATED
import com.example.app_psi.DbConstants.DbConstants.DFL_PORT
import com.example.app_psi.objects.Node
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.Enumeration


class NetworkService: Service() {

    lateinit var node: Node
    lateinit var id: String

    override fun onCreate() {
        super.onCreate()
        instance = this
        id = getLocalIp()!!
        val peers = ArrayList<String>()
        peers.add("192.168.1.3:5001")
        peers.add("192.168.1.2:5001")
        node = Node(id, DFL_PORT, peers)
        node.start()
        // Mandar un broadcast para que la MainActivity sepa que el servicio se ha creado
        val intent = Intent(ACTION_SERVICE_CREATED)
        sendBroadcast(intent)
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

    }
}