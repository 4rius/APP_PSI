package com.example.app_psi.objects;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.ArrayList;
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
            String peerId = peer.split(":")[0];
            devices.put(peerId, new Device(dealerSocket, null));
        }
    }

    private void startRouterSocket() {
        while (running) {
            String sender = routerSocket.recvStr();
            String message = routerSocket.recvStr();
            System.out.println("Node " + id + " (You) received: " + message);
            long dayTime = System.currentTimeMillis();
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

    public List<String> getDevices() {
        return new ArrayList<>(devices.keySet());
    }

    /**
     * We will only be able to ping by relying on a
     * web service running on the network.
     * We will be able to use the API of any web service running on the network.
     * The first web service that replies will be our "warden" and will answer
     * all our questions. If it goes down, we will have to find another one.
     */

    private static class Device {
        ZMQ.Socket socket;
        Long lastSeen;

        Device(ZMQ.Socket socket, Long lastSeen) {
            this.socket = socket;
            this.lastSeen = lastSeen;
        }
    }
}
