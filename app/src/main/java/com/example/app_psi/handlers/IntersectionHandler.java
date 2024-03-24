package com.example.app_psi.handlers;

import android.os.Debug;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.app_psi.implementations.Polynomials;
import com.example.app_psi.objects.Device;
import com.example.app_psi.objects.Node;
import com.example.app_psi.proxies.ActivityLogger;
import com.example.app_psi.proxies.LogActivityProxy;
import com.example.app_psi.proxies.RealActivityLogger;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class IntersectionHandler {

    private final ThreadPoolExecutor executor; // Executor para lanzar hilos
    private final ActivityLogger logger;

    public IntersectionHandler() {
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
        this.logger = new LogActivityProxy(new RealActivityLogger());
    }

    private void sendJsonMessage(@NonNull Device device, Object resSet, String impName, String step, @Nullable LinkedTreeMap<String, String> publicKeyDict) {
        HashMap<String, Object> message = new HashMap<>();
        message.put("data", resSet);
        if (publicKeyDict != null) message.put("pubkey", publicKeyDict);
        message.put("implementation", impName);
        message.put("peer", Node.getInstance().getId());
        message.put("step", step);
        Gson gson = new Gson();
        device.socket.send(gson.toJson(message));
    }

    private void runInBackground(Runnable task) {
        executor.execute(task);
    }

    private void logIntersectionStart(String id, String peerId, String schemeName, String type) {
        Log.d("Node", id + " (You) - Intersection with " + peerId + " - " + schemeName + " " + type + " OPE");
    }

    @NonNull
    @Contract(pure = true)
    private String getImplementationLabel(String schemeName, @NonNull String type) {
        if (type.equals("PSI-CA")) {
            return schemeName + " PSI-CA OPE";
        } else {
            return schemeName + " OPE";
        }
    }

    public String OPEIntersectionFirstStep(Device device, String peerId, String type, CSHandler handler) {
        runInBackground(() -> {
            String impName = handler.getImplementationName();
            logger.logStart();
            long startTime = System.currentTimeMillis();
            long startCpuTime = Debug.threadCpuTimeNanos(); // Tiempo de CPU al inicio de la operación
            logIntersectionStart(Node.getInstance().getId(), peerId, impName, type);
            List<Integer> myDataList = new ArrayList<>(Node.getInstance().getMyData());
            List<BigInteger> roots = Polynomials.polyFromRoots(myDataList, BigInteger.valueOf(-1), BigInteger.ONE);
            ArrayList<BigInteger> encryptedRoots = handler.encryptRoots(roots, handler.getCryptoSystem());
            LinkedTreeMap<String, String> publicKeyDict = handler.serializePublicKey();
            sendJsonMessage(device, encryptedRoots, getImplementationLabel(impName, type), "2", publicKeyDict);
            long cpuTime = Debug.threadCpuTimeNanos() - startCpuTime; // Tiempo de CPU utilizado por la operación
            long endTime = System.currentTimeMillis();
            logger.logStop();
            logger.logActivity("INTERSECTION_" + impName + "_OPE_1", (endTime - startTime) / 1000.0, peerId, cpuTime);
        });
        return "Intersection with " + peerId + " - " + handler.getImplementationName() + " " + type + " OPE - Waiting for response...";
    }

    public void OPEIntersectionSecondStep(Device device, String peer, LinkedTreeMap<String, String> peerPubKey, ArrayList<String> data, CSHandler handler) {
        runInBackground(() -> {
            String impName = handler.getImplementationName();
            logger.logStart();
            long startTime = System.currentTimeMillis();
            LinkedTreeMap<String, BigInteger> peerPubKeyReconstructed = handler.reconstructPublicKey(peerPubKey);
            ArrayList<BigInteger> coefs = new ArrayList<>();
            for (String element : data) {
                coefs.add(new BigInteger(element));
            }
            List<Integer> myDataList = new ArrayList<>(Node.getInstance().getMyData());
            ArrayList<BigInteger> encryptedEval = handler.handleOPESecondStep(coefs, myDataList, peerPubKeyReconstructed.get("n"));
            Log.d("Node", Node.getInstance().getId() + " (You) - Intersection with " + peer + " - Encrypted evaluation: " + encryptedEval);
            sendJsonMessage(device, encryptedEval, impName + " OPE", "F", null);
            long cpuTime = Debug.threadCpuTimeNanos();
            long endTime = System.currentTimeMillis();
            logger.logStop();
            logger.logActivity("INTERSECTION_" + impName + "_OPE_2", (endTime - startTime) / 1000.0, peer, cpuTime);
        });
    }

    /** @noinspection unchecked*/
    public void OPEIntersectionFinalStep(LinkedTreeMap<String, Object> peerData, CSHandler handler) {
        runInBackground(() -> {
            String impName = handler.getImplementationName();
            logger.logStart();
            long start_time = System.currentTimeMillis();
            long startCpuTime = Debug.threadCpuTimeNanos();
            ArrayList<String> stringData = (ArrayList<String>) peerData.remove("data");
            ArrayList<BigInteger> encryptedEval = new ArrayList<>();
            assert stringData != null;
            for (String element : stringData) {
                encryptedEval.add(new BigInteger(element));
            }
            String peer = (String) peerData.remove("peer");
            ArrayList<BigInteger> decryptedEval = handler.decryptEval(encryptedEval, handler.getCryptoSystem());
            List<Integer> intersection = new ArrayList<>();
            for (BigInteger element : decryptedEval) {
                if (Node.getInstance().getMyData().contains(element.intValue())) {
                    intersection.add(element.intValue());
                }
            }
            // Guardamos el resultado, sincronizada por si se hace un broadcast, que no se vayan a perder resultados
            synchronized (Node.getInstance().getResults()) {
                Node.getInstance().getResults().put(peer + " " + impName + " OPE", intersection);
            }
            long cpuTime = Debug.threadCpuTimeNanos() - startCpuTime;
            long end_time = System.currentTimeMillis();
            logger.logStop();
            logger.logActivity("INTERSECTION_" + impName + "_OPE_F", (end_time - start_time) / 1000.0, peer, cpuTime);
            int size = intersection.size();
            assert peer != null;
            logger.logResult(intersection, size, peer, impName + " OPE");
            System.out.println("Node " + Node.getInstance().getId() + " (You) - Intersection with " + peer + " - Result: " + intersection);
        });
    }

    public String intersectionFirstStep(Device device, String peerId, CSHandler handler) {
        String impName = handler.getImplementationName();
        runInBackground(() -> {
            logger.logStart();
            long startTime = System.currentTimeMillis();
            long startCpuTime = Debug.threadCpuTimeNanos();
            Log.d("Node", Node.getInstance().getId() + " (You) - Intersection with " + peerId + " - " + impName);
            LinkedTreeMap<String, BigInteger> encryptedSet = handler.encryptMyData(Node.getInstance().getMyData(), Node.getInstance().getDomain());
            LinkedTreeMap<String, String> publicKeyDict = handler.serializePublicKey();
            sendJsonMessage(device, encryptedSet, impName, "2", publicKeyDict);
            long cpuTime = Debug.threadCpuTimeNanos() - startCpuTime;
            long endTime = System.currentTimeMillis();
            logger.logStop();
            logger.logActivity("INTERSECTION_" + impName + "_1", (endTime - startTime) / 1000.0, peerId, cpuTime);
        });
        return "Intersection with " + peerId + " - " + impName + " - Waiting for response...";
    }

    public void intersectionSecondStep(Device device, String peer, LinkedTreeMap<String, String> peerPubKey, LinkedTreeMap<String, String> data, CSHandler handler) {
        runInBackground(() -> {
            String impName = handler.getImplementationName();
            logger.logStart();
            long start_time = System.currentTimeMillis();
            long startCpuTime = Debug.threadCpuTimeNanos();
            LinkedTreeMap<String, BigInteger> peerPubKeyReconstructed = handler.reconstructPublicKey(peerPubKey);
            BigInteger n = peerPubKeyReconstructed.get("n");
            LinkedTreeMap<String, BigInteger> encryptedSet = handler.getEncryptedSet(data);
            LinkedTreeMap<String, BigInteger> multipliedSet = handler.getMultipliedSet(encryptedSet, Node.getInstance().getMyData(), n);
            System.out.println("Node " + Node.getInstance().getId() + " (You) - Intersection with " + peer + " - Multiplied set: " + multipliedSet);
            sendJsonMessage(device, multipliedSet, impName, "F", null);
            long cpuTime = Debug.threadCpuTimeNanos() - startCpuTime;
            long end_time = System.currentTimeMillis();
            logger.logStop();
            logger.logActivity("INTERSECTION_" + impName + "_2", (end_time - start_time) / 1000.0, peer, cpuTime);
        });
    }

    /** @noinspection unchecked*/
    public void intersectionFinalStep(LinkedTreeMap<String, Object> peerData, CSHandler handler) {
        runInBackground(() -> {
            String impName = handler.getImplementationName();
            logger.logStart();
            long start_time = System.currentTimeMillis();
            long startCpuTime = Debug.threadCpuTimeNanos();
            LinkedTreeMap<String, String> multipliedSet = (LinkedTreeMap<String, String>) peerData.remove("data");
            LinkedTreeMap<String, BigInteger> evalMap = handler.handleMultipliedSet(multipliedSet, handler.getCryptoSystem());
            String peer = (String) peerData.remove("peer");
            // Cogemos solo los valores que sean 1, que representan la intersección
            List<Integer> intersection = new ArrayList<>();
            for (Map.Entry<String, BigInteger> entry : evalMap.entrySet()) {
                if (entry.getValue().equals(BigInteger.ONE)) {
                    intersection.add(Integer.parseInt(entry.getKey()));
                }
            }
            // Guardamos el resultado
            synchronized (Node.getInstance().getResults()) {
                Node.getInstance().getResults().put(peer + " " + impName, intersection);
            }
            long cpuTime = Debug.threadCpuTimeNanos() - startCpuTime;
            long end_time = System.currentTimeMillis();
            logger.logStop();
            logger.logActivity("INTERSECTION_" + impName + "_F", (end_time - start_time) / 1000.0, peer, cpuTime);
            int size = intersection.size();
            assert peer != null;
            logger.logResult(intersection, size, peer, impName);
            System.out.println("Node " + Node.getInstance().getId() + " (You) - Intersection with " + peer + " - Result: " + intersection);
        });
    }

    public void CAOPEIntersectionSecondStep(Device device, String peer, LinkedTreeMap<String, String> peerPubKey, ArrayList<String> data, CSHandler handler) {
        runInBackground(() -> {
            String impName = handler.getImplementationName();
            logger.logStart();
            long start_time = System.currentTimeMillis();
            long startCpuTime = Debug.threadCpuTimeNanos();
            LinkedTreeMap<String, BigInteger> peerPubKeyReconstructed = handler.reconstructPublicKey(peerPubKey);
            // Obtenemos las raíces cifradas del peer
            ArrayList<BigInteger> coefs = new ArrayList<>();
            for (String element : data) {
                coefs.add(new BigInteger(element));
            }
            // Evaluamos el polinomio con las raíces del peer
            List<Integer> myDataList = new ArrayList<>(Node.getInstance().getMyData());
            ArrayList<BigInteger> encryptedEval = handler.getEvaluationSet(coefs, myDataList, peerPubKeyReconstructed.get("n"));
            System.out.println("Node " + Node.getInstance().getId() + " (You) - PSI-CA with " + peer + " - Encrypted evalutaion: " + encryptedEval);
            // Shuffle the encrypted evaluation to not reveal positional information
            Collections.shuffle(encryptedEval);
            sendJsonMessage(device, encryptedEval, impName + " PSI-CA OPE", "F", null);
            long cpuTime = Debug.threadCpuTimeNanos() - startCpuTime;
            long end_time = System.currentTimeMillis();
            logger.logStop();
            logger.logActivity("CARDINALITY_" + impName + "_OPE_2", (end_time - start_time) / 1000.0, peer, cpuTime);
        });
    }

    /** @noinspection unchecked*/
    public void CAOPEIntersectionFinalStep(LinkedTreeMap<String, Object> peerData, CSHandler handler) {
        runInBackground(() -> {
            String impName = handler.getImplementationName();
            logger.logStart();
            long start_time = System.currentTimeMillis();
            long startCpuTime = Debug.threadCpuTimeNanos();
            ArrayList<String> stringData = (ArrayList<String>) peerData.remove("data");
            ArrayList<BigInteger> encryptedEval = new ArrayList<>();
            assert stringData != null;
            for (String element : stringData) {
                encryptedEval.add(new BigInteger(element));
            }
            ArrayList<BigInteger> decryptedEval = handler.decryptEval(encryptedEval, handler.getCryptoSystem());
            int result = 0;
            for (BigInteger element : decryptedEval) {
                // Cada 0 representa un elemento que está en el conjunto
                if (element.equals(BigInteger.ZERO)) {
                    result++;
                }
            }
            String id = Node.getInstance().getId();
            synchronized (Node.getInstance().getResults()) {
                Node.getInstance().getResults().put(id + " " + impName + " PSI-CA OPE", result);
            }
            long cpuTime = Debug.threadCpuTimeNanos() - startCpuTime;
            long end_time = System.currentTimeMillis();
            logger.logStop();
            logger.logActivity("CARDINALITY_" + impName + "_F", (end_time - start_time) / 1000.0, id, cpuTime);
            logger.logResult(null, result, id, impName + "_PSI-CA_OPE");
            System.out.println("Node " + id + " (You) - " + impName + " PSI-CA with " + id + " - Result: " + result);
        });
    }
}
