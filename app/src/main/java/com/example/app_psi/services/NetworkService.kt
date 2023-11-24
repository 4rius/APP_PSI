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

    private lateinit var node: Node
    private lateinit var id: String
    private lateinit var warden: String

    override fun onCreate() {
        super.onCreate()
        id = getLocalIp()!!
        // Peers list of 192.168.1.49:5001, 192.168.1.2:5001, 192.168.1.3:5001
        val peers = listOf("192.168.1.49:5001, 192.168.1.2:5001, 192.168.1.3:5001")
        node = Node(id, 5001, peers)
        node.start()
        if (node.findWarden() != null) {
            warden = node.findWarden()!!
        }
        else {
            warden = id
        }
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

    fun getWarden(): String {
        return warden
    }

    fun pingDevice(device: String): Boolean {
        val apiUrl = "http://$warden:5001/api/ping/$device"
        // response is a json where the reply is on the "status" field should be a POST request
        return node.pingDevice(apiUrl)
    }

    override fun onDestroy() {
        super.onDestroy()
        node.join()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}