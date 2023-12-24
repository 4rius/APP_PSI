package com.example.app_psi.objects;

import com.example.app_psi.implementations.Paillier;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.reflect.TypeToken;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.lang.reflect.Type;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public class Node {
    private boolean running = true;
    private final String id;
    private final int port;
    private final List<String> peers;
    private final ZContext context;
    private final ZMQ.Socket routerSocket;
    private final Map<String, Device> devices = new HashMap<>();
    public final Paillier paillier = new Paillier(1024, 64); // Objeto Paillier con los métodos de claves, cifrado e intersecciones
    public Set<Integer> myData; // Conjunto de datos del nodo (set de 10 números aleatorios)
    private final int domain = 40;  // Dominio de los números aleatorios sobre los que se trabaja
    public HashMap<String, Object> results;  // Resultados de las intersecciones

    public Node(String id, int port, List<String> peers) {
        this.myData = new HashSet<>();
        Random random = new Random();
        for (int i = 0; i < 10; i++) {
            myData.add(random.nextInt(domain));
        }
        this.results = new HashMap<>();
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
            } else if (message.startsWith("{")) {
                handleMessage(message);
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
                        System.out.println(device + " - Ping OK");
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

    public void handleMessage(String message) {
        // Convertir el JSON en un JsonObject
        Gson gson = new Gson();
        LinkedTreeMap<String, Object> peerData = gson.fromJson(message, LinkedTreeMap.class);
        JsonObject jsonObject = gson.toJsonTree(peerData).getAsJsonObject();

        // Convertir JsonObject de nuevo a LinkedTreeMap
        /*  Evitar el error java.lang.ClassCastException que estás experimentando.
            Al convertir el LinkedTreeMap a un JsonObject y luego de nuevo a un LinkedTreeMap,
            Gson controla bien la deserialización del objeto JSON.
         */
        Type type = new TypeToken<LinkedTreeMap<String, Object>>(){}.getType();
        peerData = gson.fromJson(jsonObject, type);

        if (peerData.containsKey("implementation") && peerData.containsKey("peer")) {
            try {
                String peer = (String) peerData.remove("peer");
                String implementation = (String) peerData.remove("implementation");
                LinkedTreeMap<String, String> peerPubKey = (LinkedTreeMap<String, String>) peerData.remove("pubkey");
                assert peerPubKey != null;
                BigInteger peerPubKeyReconstructed = paillier.reconstructPublicKey(peerPubKey);
                LinkedTreeMap<String, BigInteger> encryptedSet = paillier.getEncryptedSet((LinkedTreeMap<String, String>) peerData.remove("data"));
                System.out.println("Node " + id + " (You) - Calculating intersection with " + peer + " - " + implementation);
                if (implementation.equals("Paillier")) {
                    LinkedTreeMap<String, BigInteger> multipliedSet = paillier.getMultipliedSet(encryptedSet, myData, peerPubKeyReconstructed);
                    // Serializamos y mandamos de vuelta el resultado
                    LinkedTreeMap<String, String> serializedMultipliedSet = new LinkedTreeMap<>();
                    for (Map.Entry<String, BigInteger> entry : multipliedSet.entrySet()) {
                        serializedMultipliedSet.put(entry.getKey(), entry.getValue().toString());
                    }
                    System.out.println("Node " + id + " (You) - Intersection with " + peer + " - Multiplied set: " + multipliedSet);
                    LinkedTreeMap<String, Object> messageToSend = new LinkedTreeMap<>();
                    messageToSend.put("data", serializedMultipliedSet);
                    messageToSend.put("peer", id);
                    messageToSend.put("cryptpscheme", implementation);
                    Objects.requireNonNull(devices.get(peer)).socket.send(gson.toJson(messageToSend));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        else if (message.startsWith("{")) {
            try {
                String cryptoScheme = (String) peerData.remove("cryptpscheme");
                assert cryptoScheme != null;
                if (cryptoScheme.equals("Paillier")) {
                    paillierIntersectionFinalStep(String.valueOf(peerData));
                }
            } catch (JsonSyntaxException e) {
                System.out.println("Received message is not a valid JSON.");
            }
        }
    }


    public String paillierIntersection(String device) {
        // Ciframos el conjunto de datos del nodo
        if (devices.containsKey(device)) {
            Device device1 = devices.get(device);
            System.out.println("Node " + id + " (You) - Intersection with " + device + " - Paillier");
            // Cifrar los datos del nodo
            LinkedTreeMap<String, BigInteger> myEncryptedSet = paillier.encryptMyData(myData, domain);
            // Serializamos la clave pública
            LinkedTreeMap<String, String> publicKeyDict = paillier.serializePublicKey();
            // Creamos con Gson un objeto que contenga el conjunto cifrado, la clave pública y la implementacion
            Gson gson = new Gson();
            HashMap<String, Object> message = new HashMap<>();
            message.put("data", myEncryptedSet);
            message.put("pubkey", publicKeyDict);
            message.put("implementation", "Paillier");
            message.put("peer", id);
            String jsonMessage = gson.toJson(message);
            // Enviamos el mensaje
            assert device1 != null;
            device1.socket.send(jsonMessage);
            return "Intersection with " + device + " - Paillier - Waiting for response...";
        }
        return "Intersection with " + device + " - Paillier - Device not found";
    }

    public void paillierIntersectionFinalStep(String jsonPeerData) {
        // Convertir el JSON en un LinkedTreeMap
        Gson gson = new Gson();
        Type type = new TypeToken<LinkedTreeMap<String, Object>>(){}.getType();
        LinkedTreeMap<String, Object> peerData = gson.fromJson(jsonPeerData, type);

        LinkedTreeMap<String, String> multipliedSet = (LinkedTreeMap<String, String>) peerData.remove("data");
        LinkedTreeMap<String, BigInteger> encMultipliedSet = paillier.recvMultipliedSet(multipliedSet);
        String device = (String) peerData.remove("peer");
        // Desciframos los datos del peer
        for (Map.Entry<String, BigInteger> entry : encMultipliedSet.entrySet()) {
            BigInteger decryptedValue = paillier.Decryption(entry.getValue());
            entry.setValue(decryptedValue);
        }
        // Guardamos el resultado
        results.put(device, multipliedSet);
        // Cogemos solo los valores que sean 1, que representan la intersección
        LinkedTreeMap<String, BigInteger> intersection = new LinkedTreeMap<>();
        for (Map.Entry<String, BigInteger> entry : encMultipliedSet.entrySet()) {
            if (entry.getValue().equals(BigInteger.ONE)) {
                intersection.put(entry.getKey(), entry.getValue());
            }
        }
        System.out.println("Node " + id + " (You) - Intersection with " + device + " - Result: " + intersection);
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
