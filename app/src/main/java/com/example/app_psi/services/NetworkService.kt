package com.example.app_psi.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
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
        val peers = listOf("192.168.1.65:5001, 192.168.1.2:5001, 192.168.1.3:5001")
        node = Node(id, 5001, peers)
        node.start()
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

    fun pingDevice(device: String): Boolean {
        return node.pingDevice(device)
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
            // TODO: Usando getNode() obtener lo que haya guardado en el nodo
            return "1 minute ago"
        }

        fun sendLargeMessage(device: String) {
            // TODO
        }

        fun sendSmallMessage(device: String) {
            TODO("Not yet implemented")
        }

        fun getStatus(): String {
            if (instance == null) return "Stopped"
            return "Running"
        }

    }
}