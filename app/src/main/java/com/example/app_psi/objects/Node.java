package com.example.app_psi.objects;

import static com.example.app_psi.DbConstants.DbConstants.DFL_BIT_LENGTH;
import static com.example.app_psi.DbConstants.DbConstants.DFL_DOMAIN;
import static com.example.app_psi.DbConstants.DbConstants.DFL_EXPANSION_FACTOR;
import static com.example.app_psi.DbConstants.DbConstants.DFL_SET_SIZE;
import static com.example.app_psi.DbConstants.DbConstants.VERSION;

import android.annotation.SuppressLint;
import android.util.Log;

import com.example.app_psi.implementations.CryptoSystem;
import com.example.app_psi.implementations.DamgardJurik;
import com.example.app_psi.implementations.Paillier;
import com.example.app_psi.implementations.Polynomials;
import com.example.app_psi.services.LogService;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.internal.LinkedTreeMap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

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

@SuppressLint("SimpleDateFormat")
public class Node {
    private boolean running = true;
    private final String id;
    private final int port;
    private final ArrayList<String> peers;
    private final ZContext context;
    private final ZMQ.Socket routerSocket;
    private final Map<String, Device> devices = new HashMap<>();
    public final Paillier paillier = new Paillier(DFL_BIT_LENGTH); // Objeto Paillier con los métodos de claves, cifrado e intersecciones
    public final DamgardJurik damgardJurik = new DamgardJurik(DFL_BIT_LENGTH, DFL_EXPANSION_FACTOR); // Objeto DamgardJurik con los métodos de claves, cifrado e intersecciones
    public Set<Integer> myData; // Conjunto de datos del nodo (set de 10 números aleatorios)
    private final int domain = DFL_DOMAIN;  // Dominio de los números aleatorios sobre los que se trabaja
    public HashMap<String, Object> results;  // Resultados de las intersecciones
    public Node(String id, int port, ArrayList<String> peers) {
        this.myData = new HashSet<>();
        Random random = new Random();
        for (int i = 0; i < DFL_SET_SIZE; i++) {
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
                dealerSocket.send("DISCOVER: Node " + id + " is looking for peers");
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
            if (message.endsWith("is pinging you!")) {
                handlePing(message, dayTime);
            } else if (message.startsWith("DISCOVER:")) {
                handleDiscovery(message, dayTime);
            } else if (message.startsWith("DISCOVER_ACK:")) {
                handleDiscoverAck(message, dayTime);
            } else if (message.startsWith("{")) {
                handleMessage(message);
            } else if (message.startsWith("Added ")) {
                handleAdded(message, dayTime);
            } else {
                handleUnknownMessage(message, dayTime);
            }
        }
        routerSocket.close();
        context.close();
    }

    private void handlePing(String message, String dayTime) {
        String peer = message.split(" ")[0];
        Device device = devices.get(peer);
        if (device != null) {
            device.lastSeen = dayTime;
            String msg = id + " is up and running!";
            routerSocket.sendMore(peer);
            routerSocket.send(msg);
        }
    }

    private void handleDiscovery(String message, String dayTime) {
        String peer = message.split(" ")[2];
        if (!devices.containsKey(peer)) addNewDevice(peer, dayTime);
        Device device = devices.get(peer);
        assert device != null;
        device.socket.send("DISCOVER_ACK: Node " + id + " acknowledges node " + peer);
    }

    private void handleDiscoverAck(String message, String dayTime) {
        String peer = message.split(" ")[2];
        if (!devices.containsKey(peer)) addNewDevice(peer, dayTime);
    }

    private void handleAdded(String message, String dayTime) {
        String peer = message.split(" ")[8];
        Device device = devices.get(peer);
        if (device != null) {
            device.lastSeen = dayTime;
        }
    }

    private void handleUnknownMessage(String message, String dayTime) {
        System.out.println(id + " (You) received: " + message + " but don't know what to do with it");
        String peer = message.split(" ")[0];
        Device device = devices.get(peer);
        if (device != null) {
            device.lastSeen = dayTime;
        }
    }

    private void addNewDevice(String peer, String dayTime) {
        System.out.println("Added " + peer + " to my network");
        ZMQ.Socket dealerSocket = context.createSocket(SocketType.DEALER);
        dealerSocket.connect("tcp://" + peer + ":" + port);
        devices.put(peer, new Device(dealerSocket, dayTime));
        peers.add(peer);
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

    @SuppressWarnings("unchecked")  // Todos los casteos son seguros aunque al IDE no le guste
    public void handleMessage(String message) {
        Gson gson = new Gson();
        // Convertir el JSON en un LinkedTreeMap para realizar las operaciones sobre él
        LinkedTreeMap<String, Object> peerData = gson.fromJson(message, LinkedTreeMap.class);

        if (peerData.containsKey("implementation") && peerData.containsKey("peer")) {
            try {
                String peer = (String) peerData.remove("peer");
                String implementation = (String) peerData.remove("implementation");

                LinkedTreeMap<String, String> peerPubKey = (LinkedTreeMap<String, String>) peerData.remove("pubkey");
                assert peerPubKey != null;
                switch (implementation) {
                    case "Paillier":
                        intersectionSecondStep(peer, peerPubKey, (LinkedTreeMap<String, String>) peerData.remove("data"), paillier);
                        break;
                    case "DamgardJurik":
                    case "Damgard-Jurik":
                        intersectionSecondStep(peer, peerPubKey, (LinkedTreeMap<String, String>) peerData.remove("data"), damgardJurik);
                        break;
                    case "Paillier OPE":
                    case "Paillier_OPE":
                        OPEIntersectionSecondStep(peer, peerPubKey, (ArrayList<String>) peerData.remove("data"), paillier);
                        break;
                    case "DamgardJurik OPE":
                    case "Damgard-Jurik_OPE":
                        OPEIntersectionSecondStep(peer, peerPubKey, (ArrayList<String>) peerData.remove("data"), damgardJurik);
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        else if (message.startsWith("{")) {
            try {
                String cryptoScheme = (String) peerData.remove("cryptpscheme");
                assert cryptoScheme != null;
                switch (cryptoScheme) {
                    case "Paillier":
                        intersectionFinalStep(peerData, paillier);
                        break;
                    case "DamgardJurik":
                    case "Damgard-Jurik":
                        intersectionFinalStep(peerData, damgardJurik);
                        break;
                    case "Paillier OPE":
                    case "Paillier_OPE":
                        OPEIntersectionFinalStep(peerData, paillier);
                        break;
                    case "DamgardJurik OPE":
                    case "Damgard-Jurik_OPE":
                        OPEIntersectionFinalStep(peerData, damgardJurik);
                        break;
                }
            } catch (JsonSyntaxException e) {
                System.out.println("Received message is not a valid JSON.");
            }
        }
    }

    public String intersectionFirstStep(String device, CryptoSystem cs) {
        if (devices.containsKey(device)) {
            new Thread(() -> {
                LogService.Companion.startLogging();
                Long start_time = System.currentTimeMillis();
                Device device1 = devices.get(device);
                System.out.println("Node " + id + " (You) - Intersection with " + device + " - " + cs.getClass().getSimpleName());
                // Ciframos los datos del nodo
                LinkedTreeMap<String, BigInteger> encryptedSet = cs.encryptMyData(myData, domain);
                // Serializamos la clave pública
                LinkedTreeMap<String, String> publicKeyDict = cs.serializePublicKey();
                // Creamos con Gson un objeto que contenga el conjunto cifrado, la clave pública y la implementacion
                Gson gson = new Gson();
                HashMap<String, Object> message = new HashMap<>();
                message.put("data", encryptedSet);
                message.put("pubkey", publicKeyDict);
                message.put("implementation", cs.getClass().getSimpleName());
                message.put("peer", id);
                String jsonMessage = gson.toJson(message);
                // Enviamos el mensaje
                assert device1 != null;
                device1.socket.send(jsonMessage);
                Long end_time = System.currentTimeMillis();
                LogService.Companion.stopLogging();
                LogService.Companion.logActivity("INTERSECTION_" + cs.getClass().getSimpleName(), (end_time - start_time) / 1000.0, VERSION, device);
            }).start();
            } else {
            return "Intersection with " + device + " - " + cs.getClass().getSimpleName() + " - Device not found";
        }
        return "Intersection with " + device + " - " + cs.getClass().getSimpleName() + " - Waiting for response...";
    }

    public void intersectionSecondStep(String peer, LinkedTreeMap<String, String> peerPubKey, LinkedTreeMap<String, String> data, CryptoSystem cs) {
        LogService.Companion.startLogging();
        Long start_time = System.currentTimeMillis();
        LinkedTreeMap<String, BigInteger> peerPubKeyReconstructed = cs.reconstructPublicKey(peerPubKey);
        BigInteger n = peerPubKeyReconstructed.get("n");
        LinkedTreeMap<String, BigInteger> encryptedSet = cs.getEncryptedSet(data);
        LinkedTreeMap<String, BigInteger> multipliedSet = cs.getMultipliedSet(encryptedSet, myData, n);
        // Serializamos y mandamos de vuelta el resultado
        LinkedTreeMap<String, String> serializedMultipliedSet = new LinkedTreeMap<>();
        for (Map.Entry<String, BigInteger> entry : multipliedSet.entrySet()) {
            serializedMultipliedSet.put(entry.getKey(), entry.getValue().toString());
        }
        System.out.println("Node " + id + " (You) - Intersection with " + peer + " - Multiplied set: " + serializedMultipliedSet);
        LinkedTreeMap<String, Object> messageToSend = new LinkedTreeMap<>();
        messageToSend.put("data", serializedMultipliedSet);
        messageToSend.put("peer", id);
        messageToSend.put("cryptpscheme", cs.getClass().getSimpleName());
        Gson gson = new Gson();
        Objects.requireNonNull(devices.get(peer)).socket.send(gson.toJson(messageToSend));
        Long end_time = System.currentTimeMillis();
        LogService.Companion.stopLogging();
        LogService.Companion.logActivity("INTERSECTION_" + cs.getClass().getSimpleName() + "_2", (end_time - start_time) / 1000.0, VERSION, peer);
    }

    @SuppressWarnings("unchecked")
    public void intersectionFinalStep(LinkedTreeMap<String, Object> peerData, CryptoSystem cs) {
        LogService.Companion.startLogging();
        Long start_time = System.currentTimeMillis();
        LinkedTreeMap<String, String> multipliedSet = (LinkedTreeMap<String, String>) peerData.remove("data");
        LinkedTreeMap<String, BigInteger> encMultipliedSet = cs.recvMultipliedSet(multipliedSet);
        String device = (String) peerData.remove("peer");
        // Desciframos los datos del peer
        for (Map.Entry<String, BigInteger> entry : encMultipliedSet.entrySet()) {
            BigInteger decryptedValue = cs.Decrypt(entry.getValue());
            entry.setValue(decryptedValue);
        }
        // Cogemos solo los valores que sean 1, que representan la intersección
        List<Integer> intersection = new ArrayList<>();
        for (Map.Entry<String, BigInteger> entry : encMultipliedSet.entrySet()) {
            if (entry.getValue().equals(BigInteger.ONE)) {
                intersection.add(Integer.parseInt(entry.getKey()));
            }
        }
        // Guardamos el resultado
        results.put(device + " " + cs.getClass().getSimpleName(), intersection);
        Long end_time = System.currentTimeMillis();
        LogService.Companion.stopLogging();
        LogService.Companion.logActivity("INTERSECTION_" + cs.getClass().getSimpleName() + "_F", (end_time - start_time) / 1000.0, VERSION, device);
        int size = intersection.size();
        LogService.Companion.logResult(intersection, size, VERSION, device, cs.getClass().getSimpleName());
        System.out.println("Node " + id + " (You) - Intersection with " + device + " - Result: " + intersection);
    }

    public String OPEIntersectionFirstStep(String device, CryptoSystem cs) {
        if (devices.containsKey(device)) {
            LogService.Companion.startLogging();
            Long start_time = System.currentTimeMillis();
            Device device1 = devices.get(device);
            System.out.println("Node " + id + " (You) - Intersection with " + device + " - " + cs.getClass().getSimpleName() + " OPE");
            // Obtenemos las raíces del polinomio
            List<Integer> myData = new ArrayList<>(this.myData);
            List<BigInteger> roots = Polynomials.polyFromRoots(myData, BigInteger.valueOf(-1), BigInteger.ONE);
            // Ciframos las raíces
            ArrayList<BigInteger> encryptedRoots = cs.encryptRoots(roots);
            // Serializamos la clave pública
            LinkedTreeMap<String, String> publicKeyDict = cs.serializePublicKey();
            // Creamos con Gson un objeto que contenga el conjunto cifrado, la clave pública y la implementacion
            Gson gson = new Gson();
            HashMap<String, Object> message = new HashMap<>();
            message.put("data", encryptedRoots);
            message.put("pubkey", publicKeyDict);
            message.put("implementation", cs.getClass().getSimpleName() + " OPE");
            message.put("peer", id);
            String jsonMessage = gson.toJson(message);
            // Enviamos el mensaje
            assert device1 != null;
            device1.socket.send(jsonMessage);
            Long end_time = System.currentTimeMillis();
            LogService.Companion.stopLogging();
            LogService.Companion.logActivity("INTERSECTION_" + cs.getClass().getSimpleName() + "_OPE_1", (end_time - start_time) / 1000.0, VERSION, device);
        } else {
            return "Intersection with " + device + " - " + cs.getClass().getSimpleName() + " OPE - Device not found";
        }
        return "Intersection with " + device + " - " + cs.getClass().getSimpleName() + " OPE - Waiting for response...";
    }

    public void OPEIntersectionSecondStep(String peer, LinkedTreeMap<String, String> peerPubKey, ArrayList<String> data, CryptoSystem cs) {
        LogService.Companion.startLogging();
        Long start_time = System.currentTimeMillis();
        LinkedTreeMap<String, BigInteger> peerPubKeyReconstructed = cs.reconstructPublicKey(peerPubKey);
        // Obtenemos las raíces cifradas del peer
        ArrayList<BigInteger> coefs = new ArrayList<>();
        for (String element : data) {
            coefs.add(new BigInteger(element));
        }

        // Evaluamos el polinomio con las raíces del peer
        List<Integer> myData = new ArrayList<>(this.myData);
        ArrayList<BigInteger> encryptedEval = cs.handleOPESecondStep(coefs, myData, peerPubKeyReconstructed.get("n"));

        System.out.println("Node " + id + " (You) - Intersection with " + peer + " - Encrypted evalutaion: " + encryptedEval);
        LinkedTreeMap<String, Object> messageToSend = new LinkedTreeMap<>();
        messageToSend.put("data", encryptedEval);
        messageToSend.put("peer", id);
        messageToSend.put("cryptpscheme", cs.getClass().getSimpleName() + " OPE");
        Gson gson = new Gson();
        Objects.requireNonNull(devices.get(peer)).socket.send(gson.toJson(messageToSend));
        Long end_time = System.currentTimeMillis();
        LogService.Companion.stopLogging();
        LogService.Companion.logActivity("INTERSECTION_" + cs.getClass().getSimpleName() + "_OPE_2", (end_time - start_time) / 1000.0, VERSION, peer);
    }

    @SuppressWarnings("unchecked")
    public void OPEIntersectionFinalStep(LinkedTreeMap<String, Object> peerData, CryptoSystem cs) {
        LogService.Companion.startLogging();
        Long start_time = System.currentTimeMillis();
        ArrayList<String> stringData = (ArrayList<String>) peerData.remove("data");
        ArrayList<BigInteger> encryptedEval = new ArrayList<>();
        for (String element : stringData) {
            encryptedEval.add(new BigInteger(element));
        }
        String device = (String) peerData.remove("peer");
        ArrayList<BigInteger> decryptedEval = new ArrayList<>();
        for (BigInteger element : encryptedEval) {
            decryptedEval.add(cs.Decrypt(element));
        }
        List<Integer> intersection = new ArrayList<>();
        for (BigInteger element : decryptedEval) {
            if (myData.contains(element.intValue())) {
                intersection.add(element.intValue());
            }
        }
        // Guardamos el resultado
        results.put(device + " " + cs.getClass().getSimpleName() + " OPE", intersection);
        Long end_time = System.currentTimeMillis();
        LogService.Companion.stopLogging();
        LogService.Companion.logActivity("INTERSECTION_" + cs.getClass().getSimpleName() + "_OPE_F", (end_time - start_time) / 1000.0, VERSION, device);
        int size = intersection.size();
        LogService.Companion.logResult(intersection, size, VERSION, device, cs.getClass().getSimpleName() + "_OPE");
        System.out.println("Node " + id + " (You) - Intersection with " + device + " - Result: " + intersection);
    }

    public String intPaillierOPE(String device) {
        return OPEIntersectionFirstStep(device, paillier);
    }

    public String intDamgardJurikOPE(String device) {
        return OPEIntersectionFirstStep(device, damgardJurik);
    }

    public String intPaillier(String device) {
        return intersectionFirstStep(device, paillier);
    }

    public String intDamgardJurik(String device) {
        return intersectionFirstStep(device, damgardJurik);
    }

    public void pingAllDevices() {
        for (Device device : devices.values()) {
            device.socket.send(id + " is pinging you!");
        }
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

    public void addPeer(@NotNull String peer) {
        if (!devices.containsKey(peer)) {
            System.out.println("Node " + id + " (You) connecting to Node " + peer);
            ZMQ.Socket dealerSocket = context.createSocket(SocketType.DEALER);
            dealerSocket.connect("tcp://" + peer);
            dealerSocket.send("DISCOVER: Node " + id + " is looking for peers");
            String peerId = extractId(peer);
            devices.put(peerId, new Device(dealerSocket, "Not seen yet"));
            peers.add(peer);
        }
    }

    public void discoverPeers() throws InterruptedException {
        System.out.println("Node " + id + " (You) discovering peers on port " + port);
        ArrayList<ZMQ.Socket> sockets = new ArrayList<>();
        for (int i = 1; i < 255; i++) {
            String ip = "192.168.1." + i;
            if (!ip.equals(id) && !peers.contains(ip)) {
                System.out.println("Node " + id + " (You) Trying to connect to " + ip);
                ZMQ.Socket dealerSocket = context.createSocket(SocketType.DEALER);
                dealerSocket.connect("tcp://" + ip + ":" + port);
                dealerSocket.send("DISCOVER: Node " + id + " is looking for peers");
                sockets.add(dealerSocket);
            }
        }
        // Close all sockets
        Thread.sleep(1000);
        for (ZMQ.Socket socket : sockets) context.destroySocket(socket);
    }

    public double generatePaillierKeys() {
        return keygen(paillier);
    }

    public double generateDJKeys() {
        return keygen(damgardJurik);
    }

    public double keygen(CryptoSystem cs) {
        LogService.Companion.startLogging();
        long startTime = System.currentTimeMillis();
        cs.keyGeneration(DFL_BIT_LENGTH);
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        LogService.Companion.stopLogging();
        Log.d(cs.getClass().getSimpleName(), "Key generation time: " + duration / 1000.0 + " seconds");
        return duration / 1000.0;
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
