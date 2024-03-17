package com.example.app_psi.handlers;

import android.os.Debug;
import android.util.Log;
import com.example.app_psi.implementations.CryptoSystem;
import com.example.app_psi.implementations.DamgardJurik;
import com.example.app_psi.implementations.Paillier;
import com.example.app_psi.implementations.Polynomials;
import com.example.app_psi.objects.Device;
import com.example.app_psi.objects.Node;
import com.example.app_psi.proxies.ActivityLogger;
import com.example.app_psi.proxies.LogActivityProxy;
import com.example.app_psi.services.LogService;
import com.example.app_psi.proxies.RealActivityLogger;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;

import static com.example.app_psi.DbConstants.*;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Contract;

public class SchemeHandler {

    private final CryptoSystem paillier; // Objeto Paillier con los métodos de claves, cifrado e intersecciones
    private final CryptoSystem damgardJurik; // Objeto DamgardJurik con los métodos de claves, cifrado e intersecciones
    private final ThreadPoolExecutor executor; // Executor para lanzar hilos
    private final ActivityLogger logger;

    public SchemeHandler() {
        this.paillier = new Paillier(DFL_BIT_LENGTH);
        this.damgardJurik = new DamgardJurik(DFL_BIT_LENGTH, DFL_EXPANSION_FACTOR);
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
        this.logger = new LogActivityProxy(new RealActivityLogger());
    }

    private void sendJsonMessage(@NonNull Device device, Object message) {
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

    public String OPEIntersectionFirstStep(Device device, @NonNull CryptoSystem cs, String peerId, String type) {
        runInBackground(() -> {
            logger.logStart();
            long startTime = System.currentTimeMillis();
            long startCpuTime = Debug.threadCpuTimeNanos(); // Tiempo de CPU al inicio de la operación
            logIntersectionStart(Node.getInstance().getId(), peerId, cs.getClass().getSimpleName(), type);
            List<Integer> myDataList = new ArrayList<>(Node.getInstance().getMyData());
            List<BigInteger> roots = Polynomials.polyFromRoots(myDataList, BigInteger.valueOf(-1), BigInteger.ONE);
            ArrayList<BigInteger> encryptedRoots = cs.encryptRoots(roots);
            LinkedTreeMap<String, String> publicKeyDict = cs.serializePublicKey();
            HashMap<String, Object> message = new HashMap<>();
            message.put("data", encryptedRoots);
            message.put("pubkey", publicKeyDict);
            message.put("implementation", getImplementationLabel(cs.getClass().getSimpleName(), type));
            message.put("peer", Node.getInstance().getId());
            sendJsonMessage(device, message);
            long cpuTime = Debug.threadCpuTimeNanos() - startCpuTime; // Tiempo de CPU utilizado por la operación
            long endTime = System.currentTimeMillis();
            logger.logStop();
            logger.logActivity("INTERSECTION_" + cs.getClass().getSimpleName() + "_OPE_1", (endTime - startTime) / 1000.0, peerId, cpuTime);
        });
        return "Intersection with " + peerId + " - " + cs.getClass().getSimpleName() + " " + type + " OPE - Waiting for response...";
    }

    public void OPEIntersectionSecondStep(Device device, String peer, LinkedTreeMap<String, String> peerPubKey, ArrayList<String> data, CryptoSystem cs) {
        runInBackground(() -> {
            logger.logStart();
            long startTime = System.currentTimeMillis();
            LinkedTreeMap<String, BigInteger> peerPubKeyReconstructed = cs.reconstructPublicKey(peerPubKey);
            ArrayList<BigInteger> coefs = new ArrayList<>();
            for (String element : data) {
                coefs.add(new BigInteger(element));
            }
            List<Integer> myDataList = new ArrayList<>(Node.getInstance().getMyData());
            ArrayList<BigInteger> encryptedEval = cs.handleOPESecondStep(coefs, myDataList, peerPubKeyReconstructed.get("n"));
            Log.d("Node", Node.getInstance().getId() + " (You) - Intersection with " + peer + " - Encrypted evaluation: " + encryptedEval);
            LinkedTreeMap<String, Object> messageToSend = new LinkedTreeMap<>();
            messageToSend.put("data", encryptedEval);
            messageToSend.put("peer", Node.getInstance().getId());
            messageToSend.put("cryptpscheme", cs.getClass().getSimpleName() + " OPE");
            sendJsonMessage(device, messageToSend);
            long cpuTime = Debug.threadCpuTimeNanos();
            long endTime = System.currentTimeMillis();
            logger.logStop();
            logger.logActivity("INTERSECTION_" + cs.getClass().getSimpleName() + "_OPE_2", (endTime - startTime) / 1000.0, peer, cpuTime);
        });
    }

    /** @noinspection unchecked*/
    public void OPEIntersectionFinalStep(LinkedTreeMap<String, Object> peerData, CryptoSystem cs) {
        runInBackground(() -> {
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
            ArrayList<BigInteger> decryptedEval = new ArrayList<>();
            for (BigInteger element : encryptedEval) {
                decryptedEval.add(cs.Decrypt(element));
            }
            List<Integer> intersection = new ArrayList<>();
            for (BigInteger element : decryptedEval) {
                if (Node.getInstance().getMyData().contains(element.intValue())) {
                    intersection.add(element.intValue());
                }
            }
            // Guardamos el resultado, sincronizada por si se hace un broadcast, que no se vayan a perder resultados
            synchronized (Node.getInstance().getResults()) {
                Node.getInstance().getResults().put(peer + " " + cs.getClass().getSimpleName() + " OPE", intersection);
            }
            long cpuTime = Debug.threadCpuTimeNanos() - startCpuTime;
            long end_time = System.currentTimeMillis();
            logger.logStop();
            logger.logActivity("INTERSECTION_" + cs.getClass().getSimpleName() + "_OPE_F", (end_time - start_time) / 1000.0, peer, cpuTime);
            int size = intersection.size();
            assert peer != null;
            logger.logResult(intersection, size, peer, cs.getClass().getSimpleName() + " OPE");
            System.out.println("Node " + Node.getInstance().getId() + " (You) - Intersection with " + peer + " - Result: " + intersection);
        });
    }

    public String intersectionFirstStep(Device device, @NonNull CryptoSystem cs, String peerId) {
        runInBackground(() -> {
            logger.logStart();
            long startTime = System.currentTimeMillis();
            long startCpuTime = Debug.threadCpuTimeNanos();
            Log.d("Node", Node.getInstance().getId() + " (You) - Intersection with " + peerId + " - " + cs.getClass().getSimpleName());
            LinkedTreeMap<String, BigInteger> encryptedSet = cs.encryptMyData(Node.getInstance().getMyData(), Node.getInstance().getDomain());
            LinkedTreeMap<String, String> publicKeyDict = cs.serializePublicKey();
            HashMap<String, Object> message = new HashMap<>();
            message.put("data", encryptedSet);
            message.put("pubkey", publicKeyDict);
            message.put("implementation", cs.getClass().getSimpleName());
            message.put("peer", Node.getInstance().getId());
            sendJsonMessage(device, message);
            long cpuTime = Debug.threadCpuTimeNanos() - startCpuTime;
            long endTime = System.currentTimeMillis();
            logger.logStop();
            logger.logActivity("INTERSECTION_" + cs.getClass().getSimpleName() + "_1", (endTime - startTime) / 1000.0, peerId, cpuTime);
        });
        return "Intersection with " + peerId + " - " + cs.getClass().getSimpleName() + " - Waiting for response...";
    }

    public void intersectionSecondStep(Device device, String peer, LinkedTreeMap<String, String> peerPubKey, LinkedTreeMap<String, String> data, CryptoSystem cs) {
        runInBackground(() -> {
            logger.logStart();
            long start_time = System.currentTimeMillis();
            long startCpuTime = Debug.threadCpuTimeNanos();
            LinkedTreeMap<String, BigInteger> peerPubKeyReconstructed = cs.reconstructPublicKey(peerPubKey);
            BigInteger n = peerPubKeyReconstructed.get("n");
            LinkedTreeMap<String, BigInteger> encryptedSet = cs.getEncryptedSet(data);
            LinkedTreeMap<String, BigInteger> multipliedSet = cs.getMultipliedSet(encryptedSet, Node.getInstance().getMyData(), n);
            // Serializamos y mandamos de vuelta el resultado
            LinkedTreeMap<String, String> serializedMultipliedSet = new LinkedTreeMap<>();
            for (Map.Entry<String, BigInteger> entry : multipliedSet.entrySet()) {
                serializedMultipliedSet.put(entry.getKey(), entry.getValue().toString());
            }
            System.out.println("Node " + Node.getInstance().getId() + " (You) - Intersection with " + peer + " - Multiplied set: " + serializedMultipliedSet);
            LinkedTreeMap<String, Object> messageToSend = new LinkedTreeMap<>();
            messageToSend.put("data", serializedMultipliedSet);
            messageToSend.put("peer", Node.getInstance().getId());
            messageToSend.put("cryptpscheme", cs.getClass().getSimpleName());
            Gson gson = new Gson();
            device.socket.send(gson.toJson(messageToSend));
            long cpuTime = Debug.threadCpuTimeNanos() - startCpuTime;
            long end_time = System.currentTimeMillis();
            logger.logStop();
            logger.logActivity("INTERSECTION_" + cs.getClass().getSimpleName() + "_2", (end_time - start_time) / 1000.0, peer, cpuTime);
        });
    }

    /** @noinspection unchecked*/
    public void intersectionFinalStep(LinkedTreeMap<String, Object> peerData, CryptoSystem cs) {
        runInBackground(() -> {
            logger.logStart();
            long start_time = System.currentTimeMillis();
            long startCpuTime = Debug.threadCpuTimeNanos();
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
            synchronized (Node.getInstance().getResults()) {
                Node.getInstance().getResults().put(peer + " " + cs.getClass().getSimpleName(), intersection);
            }
            long cpuTime = Debug.threadCpuTimeNanos() - startCpuTime;
            long end_time = System.currentTimeMillis();
            logger.logStop();
            logger.logActivity("INTERSECTION_" + cs.getClass().getSimpleName() + "_F", (end_time - start_time) / 1000.0, peer, cpuTime);
            int size = intersection.size();
            assert peer != null;
            logger.logResult(intersection, size, peer, cs.getClass().getSimpleName());
            System.out.println("Node " + Node.getInstance().getId() + " (You) - Intersection with " + peer + " - Result: " + intersection);
        });
    }

    public void CAOPEIntersectionSecondStep(Device device, String peer, LinkedTreeMap<String, String> peerPubKey, ArrayList<String> data, CryptoSystem cs) {
        runInBackground(() -> {
            logger.logStart();
            long start_time = System.currentTimeMillis();
            long startCpuTime = Debug.threadCpuTimeNanos();
            LinkedTreeMap<String, BigInteger> peerPubKeyReconstructed = cs.reconstructPublicKey(peerPubKey);
            // Obtenemos las raíces cifradas del peer
            ArrayList<BigInteger> coefs = new ArrayList<>();
            for (String element : data) {
                coefs.add(new BigInteger(element));
            }
            // Evaluamos el polinomio con las raíces del peer
            List<Integer> myDataList = new ArrayList<>(Node.getInstance().getMyData());
            ArrayList<BigInteger> encryptedEval = cs.getEvaluationSet(coefs, myDataList, peerPubKeyReconstructed.get("n"));
            System.out.println("Node " + Node.getInstance().getId() + " (You) - PSI-CA with " + peer + " - Encrypted evalutaion: " + encryptedEval);
            LinkedTreeMap<String, Object> messageToSend = new LinkedTreeMap<>();
            // Shuffle the encrypted evaluation to not reveal positional information
            //Collections.shuffle(encryptedEval); Causes exception
            messageToSend.put("data", encryptedEval);
            messageToSend.put("peer", Node.getInstance().getId());
            messageToSend.put("cryptpscheme", cs.getClass().getSimpleName() + " PSI-CA OPE");
            Gson gson = new Gson();
            device.socket.send(gson.toJson(messageToSend));
            long cpuTime = Debug.threadCpuTimeNanos() - startCpuTime;
            long end_time = System.currentTimeMillis();
            logger.logStop();
            logger.logActivity("CARDINALITY_" + cs.getClass().getSimpleName() + "_OPE_2", (end_time - start_time) / 1000.0, peer, cpuTime);
        });
    }

    /** @noinspection unchecked*/
    public void CAOPEIntersectionFinalStep(LinkedTreeMap<String, Object> peerData, CryptoSystem cs) {
        runInBackground(() -> {
            logger.logStart();
            long start_time = System.currentTimeMillis();
            long startCpuTime = Debug.threadCpuTimeNanos();
            ArrayList<String> stringData = (ArrayList<String>) peerData.remove("data");
            ArrayList<BigInteger> encryptedEval = new ArrayList<>();
            assert stringData != null;
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
            String id = Node.getInstance().getId();
            synchronized (Node.getInstance().getResults()) {
                Node.getInstance().getResults().put(id + " " + cs.getClass().getSimpleName() + " PSI-CA OPE", result);
            }
            long cpuTime = Debug.threadCpuTimeNanos() - startCpuTime;
            long end_time = System.currentTimeMillis();
            logger.logStop();
            logger.logActivity("CARDINALITY_" + cs.getClass().getSimpleName() + "_F", (end_time - start_time) / 1000.0, id, cpuTime);
            logger.logResult(null, result, id, cs.getClass().getSimpleName() + "_PSI-CA_OPE");
            System.out.println("Node " + id + " (You) - " + cs.getClass().getSimpleName() + " PSI-CA with " + id + " - Result: " + result);
        });
    }

    public void launchTest(Device device, String peerId) {
        for (int i = 0; i < TEST_ROUNDS; i++) {
            intersectionFirstStep(device, paillier, peerId);
            intersectionFirstStep(device, damgardJurik, peerId);
            OPEIntersectionFirstStep(device, paillier, peerId, "PSI");
            OPEIntersectionFirstStep(device, damgardJurik, peerId, "PSI");
            OPEIntersectionFirstStep(device, paillier, peerId, "PSI-CA");
            OPEIntersectionFirstStep(device, damgardJurik, peerId, "PSI-CA");
        }
    }

    public void keygen(CryptoSystem cs) {
        runInBackground(() -> {
            logger.logStart();
            long startTime = System.currentTimeMillis();
            long startCpuTime = Debug.threadCpuTimeNanos();
            Debug.startMethodTracing();
            cs.keyGeneration(DFL_BIT_LENGTH);
            Debug.stopMethodTracing();
            long cpuTime = Debug.threadCpuTimeNanos() - startCpuTime;
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            logger.logStop();
            LogService.Companion.logActivity("KEYGEN_" + cs.getClass().getSimpleName(), duration / 1000.0, VERSION, null, cpuTime);
            Log.d(cs.getClass().getSimpleName(), "Key generation time: " + duration / 1000.0 + " seconds");
        });
    }

    /** @noinspection unchecked*/
    public void handleIntersectionSecondStep(Device device, String peer, String implementation, LinkedTreeMap<String, String> peerPubKey, LinkedTreeMap<String, Object> peerData) {
        switch (implementation) {
            case "Paillier":
            case "DamgardJurik":
            case "Damgard-Jurik":
                intersectionSecondStep(device, peer, peerPubKey, (LinkedTreeMap<String, String>) peerData.remove("data"), getCryptoScheme(implementation));
                break;
            case "Paillier OPE":
            case "Paillier_OPE":
            case "DamgardJurik OPE":
            case "Damgard-Jurik_OPE":
                OPEIntersectionSecondStep(device, peer, peerPubKey, (ArrayList<String>) peerData.remove("data"), getCryptoScheme(implementation));
                break;
            case "Paillier PSI-CA OPE":
            case "Damgard-Jurik PSI-CA OPE":
            case "DamgardJurik PSI-CA OPE":
                CAOPEIntersectionSecondStep(device, peer, peerPubKey, (ArrayList<String>) peerData.remove("data"), getCryptoScheme(implementation));
                break;
        }
    }

    public void handleFinalStep(LinkedTreeMap<String, Object> peerData) {
        String cryptoScheme = (String) peerData.remove("cryptpscheme");
        if (cryptoScheme == null) {
            Node.getInstance().getLogger().log(Level.SEVERE, "Missing cryptpscheme field in the final step message");
            return;
        }

        switch (cryptoScheme) {
            case "Paillier":
            case "DamgardJurik":
            case "Damgard-Jurik":
                intersectionFinalStep(peerData, getCryptoScheme(cryptoScheme));
                break;
            case "Paillier OPE":
            case "Paillier_OPE":
            case "DamgardJurik OPE":
            case "Damgard-Jurik_OPE":
                OPEIntersectionFinalStep(peerData, getCryptoScheme(cryptoScheme));
                break;
            case "Paillier PSI-CA OPE":
            case "Damgard-Jurik PSI-CA OPE":
            case "DamgardJurik PSI-CA OPE":
                CAOPEIntersectionFinalStep(peerData, getCryptoScheme(cryptoScheme));
                break;
            default:
                Node.getInstance().getLogger().log(Level.SEVERE, "Unknown crypto scheme: " + cryptoScheme);
        }
    }

    private CryptoSystem getCryptoScheme(String implementation) {
        if (implementation.startsWith("Paillier")) {
            return paillier;
        } else if (implementation.startsWith("DamgardJurik") || implementation.startsWith("Damgard-Jurik")) {
            return damgardJurik;
        }
        return null;
    }

    public CryptoSystem getPaillier() {
        return paillier;
    }

    public CryptoSystem getDamgardJurik() {
        return damgardJurik;
    }
}
