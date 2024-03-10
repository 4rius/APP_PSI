package com.example.app_psi.handlers;

import static com.example.app_psi.DbConstants.VERSION;

import android.os.Debug;

import com.example.app_psi.implementations.CryptoSystem;
import com.example.app_psi.implementations.Polynomials;
import com.example.app_psi.objects.Device;
import com.example.app_psi.services.LogService;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IntersectionHandler {

    public String OPEIntersectionFirstStep(Device device, CryptoSystem cs, String id, Set<Integer> myData, String peerId, String type) {
        new Thread(() -> {
            LogService.Companion.startLogging();
            Long start_time = System.currentTimeMillis();
            Debug.startMethodTracing();
            System.out.println("Node " + id + " (You) - Intersection with " + device + " - " + cs.getClass().getSimpleName() + " OPE");
            // Obtenemos las raíces del polinomio
            List<Integer> myDataList = new ArrayList<>(myData);
            List<BigInteger> roots = Polynomials.polyFromRoots(myDataList, BigInteger.valueOf(-1), BigInteger.ONE);
            // Ciframos las raíces
            ArrayList<BigInteger> encryptedRoots = cs.encryptRoots(roots);
            // Serializamos la clave pública
            LinkedTreeMap<String, String> publicKeyDict = cs.serializePublicKey();
            // Creamos con Gson un objeto que contenga el conjunto cifrado, la clave pública y la implementacion
            Gson gson = new Gson();
            HashMap<String, Object> message = new HashMap<>();
            message.put("data", encryptedRoots);
            message.put("pubkey", publicKeyDict);
            if (type.equals("PSI-CA")) message.put("implementation", cs.getClass().getSimpleName() + " PSI-CA OPE");
            else message.put("implementation", cs.getClass().getSimpleName() + " OPE");
            message.put("peer", id);
            String jsonMessage = gson.toJson(message);
            // Enviamos el mensaje
            device.socket.send(jsonMessage);
            Debug.stopMethodTracing();
            long cpuTime = Debug.threadCpuTimeNanos();
            Long end_time = System.currentTimeMillis();
            LogService.Companion.stopLogging();
            LogService.Companion.logActivity("INTERSECTION_" + cs.getClass().getSimpleName() + "_OPE_1", (end_time - start_time) / 1000.0, VERSION, peerId, cpuTime);
        }).start();
        return "Intersection with " + device + " - " + cs.getClass().getSimpleName() + " OPE - Waiting for response...";
    }

    public void OPEIntersectionSecondStep(Device device, String peer, LinkedTreeMap<String, String> peerPubKey, ArrayList<String> data, CryptoSystem cs, String id, Set<Integer> myData) {
        new Thread(() -> {
            LogService.Companion.startLogging();
            Long start_time = System.currentTimeMillis();
            Debug.startMethodTracing();
            LinkedTreeMap<String, BigInteger> peerPubKeyReconstructed = cs.reconstructPublicKey(peerPubKey);
            // Obtenemos las raíces cifradas del peer
            ArrayList<BigInteger> coefs = new ArrayList<>();
            for (String element : data) {
                coefs.add(new BigInteger(element));
            }

            // Evaluamos el polinomio con las raíces del peer
            List<Integer> myDataList = new ArrayList<>(myData);
            ArrayList<BigInteger> encryptedEval = cs.handleOPESecondStep(coefs, myDataList, peerPubKeyReconstructed.get("n"));

            System.out.println("Node " + id + " (You) - Intersection with " + peer + " - Encrypted evalutaion: " + encryptedEval);
            LinkedTreeMap<String, Object> messageToSend = new LinkedTreeMap<>();
            messageToSend.put("data", encryptedEval);
            messageToSend.put("peer", id);
            messageToSend.put("cryptpscheme", cs.getClass().getSimpleName() + " OPE");
            Gson gson = new Gson();
            device.socket.send(gson.toJson(messageToSend));
            Debug.stopMethodTracing();
            long cpuTime = Debug.threadCpuTimeNanos();
            Long end_time = System.currentTimeMillis();
            LogService.Companion.stopLogging();
            LogService.Companion.logActivity("INTERSECTION_" + cs.getClass().getSimpleName() + "_OPE_2", (end_time - start_time) / 1000.0, VERSION, peer, cpuTime);
        }).start();
    }

    public void OPEIntersectionFinalStep(LinkedTreeMap<String, Object> peerData, CryptoSystem cs, String id, Set<Integer> myData, HashMap<String, Object> results) {
        new Thread(() -> {
            LogService.Companion.startLogging();
            Long start_time = System.currentTimeMillis();
            Debug.startMethodTracing();
            ArrayList<String> stringData = (ArrayList<String>) peerData.remove("data");
            ArrayList<BigInteger> encryptedEval = new ArrayList<>();
            for (String element : stringData) {
                encryptedEval.add(new BigInteger(element));
            }
            String peer = (String) peerData.remove("peer");
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
            // Guardamos el resultado, sincronizada por si se hace un broadcast, que no se vayan a perder resultados
            synchronized (results) {
                results.put(peer + " " + cs.getClass().getSimpleName() + " OPE", intersection);
            }
            Debug.stopMethodTracing();
            long cpuTime = Debug.threadCpuTimeNanos();
            Long end_time = System.currentTimeMillis();
            LogService.Companion.stopLogging();
            LogService.Companion.logActivity("INTERSECTION_" + cs.getClass().getSimpleName() + "_OPE_F", (end_time - start_time) / 1000.0, VERSION, peer, cpuTime);
            int size = intersection.size();
            LogService.Companion.logResult(intersection, size, VERSION, peer, cs.getClass().getSimpleName() + "_OPE");
            System.out.println("Node " + id + " (You) - Intersection with " + peer + " - Result: " + intersection);
        }).start();
    }

    public String intersectionFirstStep(Device device, CryptoSystem cs, String id, Set<Integer> myData, String peerId, int domain) {
        new Thread(() -> {
            LogService.Companion.startLogging();
            Long start_time = System.currentTimeMillis();
            Debug.startMethodTracing();
            System.out.println("Node " + id + " (You) - Intersection with " + peerId + " - " + cs.getClass().getSimpleName());
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
            device.socket.send(jsonMessage);
            Debug.stopMethodTracing();
            long cpuTime = Debug.threadCpuTimeNanos();
            Long end_time = System.currentTimeMillis();
            LogService.Companion.stopLogging();
            LogService.Companion.logActivity("INTERSECTION_" + cs.getClass().getSimpleName() + "_1", (end_time - start_time) / 1000.0, VERSION, peerId, cpuTime);
        }).start();
        return "Intersection with " + peerId + " - " + cs.getClass().getSimpleName() + " - Waiting for response...";
    }

    public void intersectionSecondStep(Device device, String peer, LinkedTreeMap<String, String> peerPubKey, LinkedTreeMap<String, String> data, CryptoSystem cs, String id, Set<Integer> myData) {
        new Thread(() -> {
            LogService.Companion.startLogging();
            Long start_time = System.currentTimeMillis();
            Debug.startMethodTracing();
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
            device.socket.send(gson.toJson(messageToSend));
            Debug.stopMethodTracing();
            long cpuTime = Debug.threadCpuTimeNanos();
            Long end_time = System.currentTimeMillis();
            LogService.Companion.stopLogging();
            LogService.Companion.logActivity("INTERSECTION_" + cs.getClass().getSimpleName() + "_2", (end_time - start_time) / 1000.0, VERSION, peer, cpuTime);
        }).start();
    }

    public void intersectionFinalStep(LinkedTreeMap<String, Object> peerData, CryptoSystem cs, String id, HashMap<String, Object> results) {
        new Thread(() -> {
            LogService.Companion.startLogging();
            Long start_time = System.currentTimeMillis();
            Debug.startMethodTracing();
            LinkedTreeMap<String, String> multipliedSet = (LinkedTreeMap<String, String>) peerData.remove("data");
            LinkedTreeMap<String, BigInteger> encMultipliedSet = cs.recvMultipliedSet(multipliedSet);
            String peer = (String) peerData.remove("peer");
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
            synchronized (results) {
                results.put(peer + " " + cs.getClass().getSimpleName(), intersection);
            }
            Debug.stopMethodTracing();
            long cpuTime = Debug.threadCpuTimeNanos();
            Long end_time = System.currentTimeMillis();
            LogService.Companion.stopLogging();
            LogService.Companion.logActivity("INTERSECTION_" + cs.getClass().getSimpleName() + "_F", (end_time - start_time) / 1000.0, VERSION, peer, cpuTime);
            int size = intersection.size();
            LogService.Companion.logResult(intersection, size, VERSION, peer, cs.getClass().getSimpleName());
            System.out.println("Node " + id + " (You) - Intersection with " + peer + " - Result: " + intersection);
        }).start();
    }

    public void CAOPEIntersectionSecondStep(Device device, String peer, LinkedTreeMap<String, String> peerPubKey, ArrayList<String> data, CryptoSystem cs, String id, Set<Integer> myData) {
        new Thread(() -> {
            LogService.Companion.startLogging();
            Long start_time = System.currentTimeMillis();
            Debug.startMethodTracing();
            LinkedTreeMap<String, BigInteger> peerPubKeyReconstructed = cs.reconstructPublicKey(peerPubKey);
            // Obtenemos las raíces cifradas del peer
            ArrayList<BigInteger> coefs = new ArrayList<>();
            for (String element : data) {
                coefs.add(new BigInteger(element));
            }
            // Evaluamos el polinomio con las raíces del peer
            List<Integer> myDataList = new ArrayList<>(myData);
            ArrayList<BigInteger> encryptedEval = cs.getEvaluationSet(coefs, myDataList, peerPubKeyReconstructed.get("n"));
            System.out.println("Node " + id + " (You) - Intersection with " + peer + " - Encrypted evalutaion: " + encryptedEval);
            LinkedTreeMap<String, Object> messageToSend = new LinkedTreeMap<>();
            messageToSend.put("data", encryptedEval);
            messageToSend.put("peer", id);
            messageToSend.put("cryptpscheme", cs.getClass().getSimpleName() + " PSI-CA OPE");
            Gson gson = new Gson();
            device.socket.send(gson.toJson(messageToSend));
            Debug.stopMethodTracing();
            long cpuTime = Debug.threadCpuTimeNanos();
            Long end_time = System.currentTimeMillis();
            LogService.Companion.stopLogging();
            LogService.Companion.logActivity("CARDINALITY_" + cs.getClass().getSimpleName() + "_OPE_2", (end_time - start_time) / 1000.0, VERSION, peer, cpuTime);
        }).start();
    }

    public void CAOPEIntersectionFinalStep(LinkedTreeMap<String, Object> peerData, CryptoSystem cs, String id, HashMap<String, Object> results) {
        new Thread(() -> {
            LogService.Companion.startLogging();
            Long start_time = System.currentTimeMillis();
            Debug.startMethodTracing();
            ArrayList<String> stringData = (ArrayList<String>) peerData.remove("data");
            ArrayList<BigInteger> encryptedEval = new ArrayList<>();
            for (String element : stringData) {
                encryptedEval.add(new BigInteger(element));
            }
            ArrayList<BigInteger> decryptedEval = new ArrayList<>();
            for (BigInteger element : encryptedEval) {
                decryptedEval.add(cs.Decrypt(element));
            }
            int result = 0;
            for (BigInteger element : decryptedEval) {
                // Cada 0 representa un elemento que está en el conjunto
                if (element.equals(BigInteger.ZERO)) {
                    result++;
                }
            }
            synchronized (results) {
                results.put(id + " " + cs.getClass().getSimpleName() + " PSI-CA OPE", result);
            }
            Debug.stopMethodTracing();
            long cpuTime = Debug.threadCpuTimeNanos();
            Long end_time = System.currentTimeMillis();
            LogService.Companion.stopLogging();
            LogService.Companion.logActivity("CARDINALITY_" + cs.getClass().getSimpleName() + "_F", (end_time - start_time) / 1000.0, VERSION, id, cpuTime);
            LogService.Companion.logResult(null, result, VERSION, id, cs.getClass().getSimpleName() + "_PSI-CA_OPE");
            System.out.println("Node " + id + " (You) - PSI-CA with " + id + " - Result: " + result);
        }).start();
    }
}
