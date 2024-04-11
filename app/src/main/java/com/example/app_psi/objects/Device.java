package com.example.app_psi.objects;

import org.zeromq.ZMQ;

public class Device {
    public final ZMQ.Socket socket;
    String lastSeen;

    Device(ZMQ.Socket socket, String lastSeen) {
        this.socket = socket;
        this.lastSeen = lastSeen;
    }
}
