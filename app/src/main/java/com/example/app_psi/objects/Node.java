package com.example.app_psi.objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Node {
    private boolean running = true;
    private final String id;
    private final int port;
    private final List<String> peers;
    private final ZContext context;
    private final ZMQ.Socket routerSocket;
    private final Map<String, Device> devices = new HashMap<>();

    public Node(String id, int port, List<String> peers) {
        this.id = id;
        this.port = port;
        this.peers = peers;
        this.context = new ZContext();
        this.routerSocket = context.createSocket(SocketType.ROUTER);
        this.routerSocket.bind("tcp://*:" + port);
        System.out.println("Node " + id + " (You) listening on port " + port);
    }

    public void start() {
        new Thread(this::startRouterSocket).start();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        for (String peer : peers) {
            System.out.println("Node " + id + " (You) connecting to Node " + peer);
            ZMQ.Socket dealerSocket = context.createSocket(SocketType.DEALER);
            dealerSocket.connect("tcp://" + peer);
            if (!devices.containsKey(peer)) {
                dealerSocket.send("Hello from Node " + id);
            }
            String peerId = extractId(peer);
            devices.put(peerId, new Device(dealerSocket, "Not seen yet"));
        }
    }

    private String extractId(String peer) {
        if (peer.startsWith("[")) {  // Si es una dirección IPv6
            return peer.substring(peer.indexOf("[") + 1, peer.indexOf("]"));
        } else {  // Si es una dirección IPv4
            return peer.split(":")[0];
        }
    }

    private void startRouterSocket() {
        while (running) {
            String sender = routerSocket.recvStr();
            String message = routerSocket.recvStr();
            System.out.println("Node " + id + " (You) received: " + message);
            String dayTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            if (message.startsWith("Hello from Node")) {
                String peer = message.split(" ")[3];
                if (!devices.containsKey(peer)) {
                    System.out.println("Added " + peer + " to my network");
                    ZMQ.Socket dealerSocket = context.createSocket(SocketType.DEALER);
                    dealerSocket.connect("tcp://" + peer + ":" + port);
                    devices.put(peer, new Device(dealerSocket, dayTime));
                }
                Device device = devices.get(peer);
                if (device != null) {
                    device.lastSeen = dayTime;
                    device.socket.send("Added " + peer + " to my network - From Node " + id);
                }
            } else if (message.endsWith("is pinging you!")) {
                String peer = message.split(" ")[0];
                Device device = devices.get(peer);
                if (device != null) {
                    device.lastSeen = dayTime;
                    routerSocket.send(sender, ZMQ.SNDMORE);
                    routerSocket.send(id + " is up and running!");
                }
            } else if (message.startsWith("Added ")) {
                String peer = message.split(" ")[8];
                Device device = devices.get(peer);
                if (device != null) {
                    device.lastSeen = dayTime;
                }
            } else {
                System.out.println(id + " (You) received: " + message + " but don't know what to do with it");
                String peer = message.split(" ")[0];
                Device device = devices.get(peer);
                if (device != null) {
                    device.lastSeen = dayTime;
                }
            }
        }
        routerSocket.close();
        context.close();
    }

    public void join() {
        running = false;
        for (Device device : devices.values()) {
            device.socket.close();
        }
        devices.clear();
    }

    public void broadcastMessage(String message) {
        for (Device device : devices.values()) {
            device.socket.send(message);
        }
    }

    public boolean pingDevice(@NotNull String device) {
        if (devices.containsKey(device)) {
            System.out.println("Pinging " + device);
            Device device1 = devices.get(device);
            if (device1 != null) {
                int attempts = 0;
                int maxAttempts = 3;
                while (attempts < maxAttempts) {
                    device1.socket.send(id + " is pinging you!");
                    String response = device1.socket.recvStr(ZMQ.DONTWAIT);
                    if (response == null) {
                        System.out.println(device + " - Ping failed, retrying...");
                    } else if (response.endsWith("is up and running!")) {
                        // Update last seen
                        device1.lastSeen = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                        return true;
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    attempts++;
                }
            }
        }
        return false;
    }

    public void pingAllDevices() {
        for (Device device : devices.values()) {
            device.socket.send(id + " is pinging you!");
        }
        // TODO: Que se actualice toda la lista con los nuevos timestamps
    }

    public List<String> getDevices() {
        return new ArrayList<>(devices.keySet());
    }

    @Nullable
    public String getLastSeen(@NotNull String device) {
        if (devices.containsKey(device)) {
            Device device1 = devices.get(device);
            if (device1 != null) {
                return device1.lastSeen;
            }
        }
        return null;
    }

    private static class Device {
        ZMQ.Socket socket;
        String lastSeen;

        Device(ZMQ.Socket socket, String lastSeen) {
            this.socket = socket;
            this.lastSeen = lastSeen;
        }
    }

    public String getId() {
        if (id.startsWith("[")) {  // Si es una dirección IPv6
            return id.substring(id.indexOf("[") + 1, id.indexOf("]"));
        }
        return id;
    }

    public String getFullId() {
        return id;
    }

    public List<String> getPeers() {
        return peers;
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }
}
