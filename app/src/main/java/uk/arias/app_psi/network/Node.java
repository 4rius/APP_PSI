package uk.arias.app_psi.network;


import static uk.arias.app_psi.collections.DbConstants.DFL_DOMAIN;
import static uk.arias.app_psi.collections.DbConstants.DFL_SET_SIZE;
import static uk.arias.app_psi.collections.DbConstants.NODE_INIT;

import android.annotation.SuppressLint;
import android.util.Log;

import androidx.annotation.NonNull;

import uk.arias.app_psi.services.LogService;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressLint("SimpleDateFormat")
public final class Node {
    private static Node instance = null;
    private boolean running = true;
    private final String id;
    private final int port;
    private final ArrayList<String> peers;
    private final ZContext context;
    private final ZMQ.Socket routerSocket;
    private final Map<String, Device> devices = new HashMap<>();
    private final Set<Integer> myData; // Conjunto de datos del nodo
    private int domain = DFL_DOMAIN;  // Dominio de los números aleatorios sobre los que se trabaja
    private final HashMap<String, Object> results;  // Resultados de las intersecciones
    private final JSONHandler jsonHandler = new JSONHandler();
    private final Logger logger = Logger.getLogger(Node.class.getName());
    private final ExecutorService executor;
    private Node(String id, int port, ArrayList<String> peers) {
        this.myData = new HashSet<>();
        Random random = new Random();
        while (myData.size() < DFL_SET_SIZE) {
            myData.add(random.nextInt(DFL_DOMAIN));
        }
        this.results = new HashMap<>();
        this.id = id;
        this.port = port;
        this.peers = peers;
        this.context = new ZContext();
        context.setRcvHWM(2000);
        context.setSndHWM(2000);
        this.routerSocket = context.createSocket(SocketType.ROUTER);
        this.routerSocket.setIPv6(true);
        this.routerSocket.bind("tcp://" + id + ":" + port);
        PriorityBlockingQueue<Runnable> queue = new PriorityBlockingQueue<>();
        this.executor = new ThreadPoolExecutor(
                10, // Número de hilos
                10, // Número máximo de hilos
                1, // Tiempo de vida de los hilos, lo que tardan en morir si están ociosos
                TimeUnit.MINUTES,
                queue // Cola de tareas
        );
        System.out.println("Node " + id + " (You) listening on port " + port);
    }

    @NonNull
    @Contract("_, _, _ -> new")
    public static Node createNode(String id, int port, ArrayList<String> peers) {
        if (instance == null) {
            instance = new Node(id, port, peers);
        } else {
            throw new IllegalStateException(NODE_INIT);
        }
        return instance;
    }

    @androidx.annotation.Nullable
    @Contract(pure = true)
    public static Node getInstance() {
        if (instance == null) {
            return null;
        }
        return instance;
    }

    public void start() {
        new Thread(this::startRouterSocket).start();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "Interrupted!", e);
        }

        for (String peer : peers) {
            System.out.println("Node " + id + " (You) connecting to Node " + peer);
            ZMQ.Socket dealerSocket = context.createSocket(SocketType.DEALER);
            dealerSocket.connect("tcp://" + peer + ":" + port);
            if (!devices.containsKey(peer)) {
                dealerSocket.send("DISCOVER: Node " + id + " is looking for peers");
            }
            String peerId = extractId(peer);
            devices.put(peerId, new Device(dealerSocket, "Not seen yet"));
        }
    }



    private void startRouterSocket() {
        while (running) {
            try {
                // Recibir mensajes solo cuando haya algo que recibir
                String sender = routerSocket.recvStr();
                String message = routerSocket.recvStr();
                if (message == null) continue;
                // Los JSON tienen menos prioridad que el resto, para responder cuanto antes a otras operaciones
                executor.execute(new PriorityRunnable(message.startsWith("{") ? 0 : 1, () -> handleReceived(sender, message)));
            } catch (ZMQException e) {
                if (e.getErrorCode() == ZMQ.Error.ETERM.getCode()) {
                    // Context has been terminated
                    break;
                }
            }
        }
    }

    private void handleReceived(String sender, @NonNull String message) {
        System.out.println("Node " + id + " (You) received: " + message);
        String dayTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        if (message.endsWith("is pinging you!")) {
            handlePing(sender, message, dayTime);
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

    private void handlePing(@NonNull String sender, @NonNull String message, String dayTime) {
        String peer = message.split(" ")[0];
        Device device = devices.get(peer);
        if (device != null) {
            device.lastSeen = dayTime;
            String msg = id + " is up and running!";
            routerSocket.sendMore(sender);
            routerSocket.send(msg);
        } else {
            this.addNewDevice(peer, dayTime);
        }
    }

    private void handleDiscovery(@NonNull String message, String dayTime) {
        String peer = message.split(" ")[2];
        if (!devices.containsKey(peer) && !Objects.equals(id, peer)) addNewDevice(peer, dayTime);
        Device device = devices.get(peer);
        assert device != null;
        device.socket.send("DISCOVER_ACK: Node " + id + " acknowledges node " + peer);
    }

    private void handleDiscoverAck(@NonNull String message, String dayTime) {
        String peer = message.split(" ")[2];
        if (!devices.containsKey(peer)) addNewDevice(peer, dayTime);
        else {
            Device device = devices.get(peer);
            assert device != null;
            device.lastSeen = dayTime;
        }
    }

    private void handleAdded(@NonNull String message, String dayTime) {
        String peer = message.split(" ")[8];
        Device device = devices.get(peer);
        if (device != null) {
            device.lastSeen = dayTime;
        }
    }

    private void handleUnknownMessage(@NonNull String message, String dayTime) {
        System.out.println(id + " (You) received: " + message + " but don't know what to do with it");
        String peer = message.split(" ")[0];
        Device device = devices.get(peer);
        if (device != null) {
            device.lastSeen = dayTime;
        }
    }


    private void addNewDevice(@NonNull String peer, String dayTime) {
        System.out.println("Added " + peer + " to my network");
        ZMQ.Socket dealerSocket = context.createSocket(SocketType.DEALER);
        // format possible IPv6 addresses
        if (peer.startsWith("[")) {
            dealerSocket.setIPv6(true);
            dealerSocket.connect("tcp://" + peer + ":" + port);
        } else {
            dealerSocket.connect("tcp://" + peer + ":" + port);
        }
        devices.put(peer, new Device(dealerSocket, dayTime));
        peers.add(peer);
    }

    public void stop() {
        for (Device device : devices.values()) {
            device.socket.setLinger(0);
            device.socket.close();
        }
        routerSocket.setLinger(0);
        routerSocket.close();
        // Terminate the ZMQ context
        context.close();
        running = false;
        // Destroy the instance
        instance = null;
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
                        logger.log(Level.SEVERE, "Interrupted!", e);
                    }
                    attempts++;
                }
            }
        }
        return false;
    }

    private void handleMessage(@NonNull String message) {
        if (message.startsWith("{") && message.contains("peer")) {
            jsonHandler.handleMessage(message);
        }
        // Podría haber otras operaciones con JSON usando más condiciones

    }

    public String startIntersection(String deviceId, String cs, String type) {
        Device device = devices.get(deviceId);
        if (device != null) {
            return jsonHandler.startIntersection(device, deviceId, cs, type);
        } else {
            return "Intersection with " + deviceId + " - " + cs + " " + type + " - Device not found";
        }
    }

    @NonNull
    @Contract(" -> new")
    public List<String> getDevices() {
        return new ArrayList<>(devices.keySet());
    }

    public Map<String, Device> getDevicesMap() {
        return devices;
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
            dealerSocket.connect("tcp://" + peer + ":" + port);
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
        // Close all sockets, the ones that are connected will respond and we will add them then
        Thread.sleep(1000);
        for (ZMQ.Socket socket : sockets) {
            socket.setLinger(0);
            socket.close();
        }
    }

    public void modifySetup(int domainSize, int setSize) {
        domain = domainSize;
        myData.clear();
        Random random = new Random();
        if (setSize > domainSize) return;
        while (myData.size() < setSize) {
            myData.add(random.nextInt(domainSize));
        }
        LogService.Companion.logSetup(domainSize, setSize);
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

    public Set<Integer> getMyData() {
        return myData;
    }

    public String extractId(@NonNull String peer) {
        if (peer.startsWith("[")) {  // Si es una dirección IPv6
            return peer.substring(peer.indexOf("[") + 1, peer.indexOf("]"));
        } else {  // Si es una dirección IPv4
            return peer.split(":")[0];
        }
    }


    public void launchTest(@NotNull String device, @Nullable Integer tr, @Nullable String impl, @Nullable String type) {
        if (devices.containsKey(device)) {
            jsonHandler.launchTest(devices.get(device), device, tr, impl, type);
        } else {
            System.out.println("Device not found");
        }
    }

    public HashMap<String, Object> getResults() {
        return results;
    }

    public Logger getLogger() {
        return logger;
    }

    public int getDomain() {
        return domain;
    }

    public void keygen(@NotNull String s, int bitLength) {
        jsonHandler.keygen(s, bitLength);
    }

    public String getPublicKey(@NotNull String cs) {
        return jsonHandler.getPublicKey(cs);
    }

    @NonNull
    public ArrayList<ThreadPoolExecutor> getExecutors() {
        ArrayList<ThreadPoolExecutor> executors = new ArrayList<>();
        executors.add((ThreadPoolExecutor) executor);
        executors.add(jsonHandler.getExecutor());
        return executors;
    }

    public void sendMessage(@NonNull Device device, String message) {
        boolean sent = device.socket.send(message);

        if (!sent) {
            Log.w("Node", "HWM full - Message not sent to " + device.socket.getLastEndpoint() + " - Device is not consuming messages - Discarding it for the memory's sake");
        }
    }

}
