package com.example.app_psi.handlers;

import com.example.app_psi.implementations.CryptoImplementation;
import com.example.app_psi.implementations.CryptoSystem;
import com.example.app_psi.objects.Device;
import com.example.app_psi.objects.Node;
import com.example.app_psi.proxies.ActivityLogger;
import com.example.app_psi.proxies.LogActivityProxy;
import com.example.app_psi.proxies.RealActivityLogger;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;

import java.util.*;
import java.util.logging.Level;

import static com.example.app_psi.DbConstants.*;

import android.os.Debug;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Nullable;

public class SchemeHandler {

    private final Map<CryptoImplementation, CSHandler> CSHandlers = new HashMap<>();

    private final IntersectionHandler intersectionHandler = new IntersectionHandler();

    public SchemeHandler() {
        CSHandlers.put(CryptoImplementation.PAILLIER, new PaillierHandler(DFL_BIT_LENGTH));
        CSHandlers.put(CryptoImplementation.DAMGARD_JURIK, new DamgardJurikHandler(DFL_BIT_LENGTH, DFL_EXPANSION_FACTOR));
    }

    public String startIntersection(Device device, String peerId, @NonNull String cryptoSystem, String operationType) {
        CSHandler handler = null;
        CryptoImplementation cryptoImpl = CryptoImplementation.fromString(cryptoSystem);
        if (cryptoImpl != null) {
            handler = CSHandlers.get(cryptoImpl);
        } else {
            Node.getInstance().getLogger().log(Level.SEVERE, "Unknown implementation: " + cryptoSystem);
        }

        return intersectionStarter(device, peerId, cryptoSystem, operationType, handler);
    }

    private String intersectionStarter(Device device, String peerId, @NonNull String cryptoSystem, String operationType, CSHandler handler) {
        if (handler != null) {
            if (operationType.equals("PSI-Domain")) {
                return intersectionHandler.intersectionFirstStep(device, peerId, handler);
            } else if (operationType.equals("PSI-CA") || operationType.equals("OPE")) {
                return intersectionHandler.OPEIntersectionFirstStep(device, peerId, operationType, handler);
            } else {
                throw new IllegalArgumentException("Invalid operation type: " + operationType);
            }
        } else {
            throw new IllegalArgumentException("Invalid cryptoSystem: " + cryptoSystem);
        }
    }

    public void launchTest(Device device, String peerId, @Nullable Integer tr, @Nullable String impl, @Nullable String type) {
        if (impl != null) {
            assert tr != null;
            assert type != null;
            CryptoImplementation cryptoImpl = CryptoImplementation.fromString(impl);
            CSHandler handler = CSHandlers.get(cryptoImpl);
            for (int i = 0; i < tr; i++) {
                intersectionStarter(device, peerId, impl, type, handler);
            }
        } else {
            for (int i = 0; i < TEST_ROUNDS; i++) {
                intersectionHandler.intersectionFirstStep(device, peerId, Objects.requireNonNull(CSHandlers.get(CryptoImplementation.PAILLIER)));
                intersectionHandler.intersectionFirstStep(device, peerId, Objects.requireNonNull(CSHandlers.get(CryptoImplementation.DAMGARD_JURIK)));
                intersectionHandler.OPEIntersectionFirstStep(device, peerId, "PSI", Objects.requireNonNull(CSHandlers.get(CryptoImplementation.PAILLIER)));
                intersectionHandler.OPEIntersectionFirstStep(device, peerId, "PSI", Objects.requireNonNull(CSHandlers.get(CryptoImplementation.DAMGARD_JURIK)));
                intersectionHandler.OPEIntersectionFirstStep(device, peerId, "PSI-CA", Objects.requireNonNull(CSHandlers.get(CryptoImplementation.PAILLIER)));
                intersectionHandler.OPEIntersectionFirstStep(device, peerId, "PSI-CA", Objects.requireNonNull(CSHandlers.get(CryptoImplementation.DAMGARD_JURIK)));
            }
        }
    }

    /** @noinspection unchecked*/
    private void handleIntersectionSecondStep(Device device, String peer, String implementation, @NonNull LinkedTreeMap<String, Object> peerData) {
        LinkedTreeMap<String, String> peerPubKey = (LinkedTreeMap<String, String>) peerData.remove("pubkey");
        CSHandler handler;
        CryptoImplementation cryptoImpl = CryptoImplementation.fromString(implementation);
        handler = CSHandlers.get(cryptoImpl);

        if (implementation.contains("PSI-CA")) {
            intersectionHandler.CAOPEIntersectionSecondStep(device, peer, peerPubKey, (ArrayList<String>) peerData.remove("data"), handler);
        } else if (implementation.contains("OPE")) {
            intersectionHandler.OPEIntersectionSecondStep(device, peer, peerPubKey, (ArrayList<String>) peerData.remove("data"), handler);
        } else {
            intersectionHandler.intersectionSecondStep(device, peer, peerPubKey, (LinkedTreeMap<String, String>) peerData.remove("data"), handler);
        }
    }

    public void handleFinalStep(@NonNull LinkedTreeMap<String, Object> peerData) {
        String cryptoScheme = (String) peerData.remove("implementation");
        if (cryptoScheme == null) {
            Node.getInstance().getLogger().log(Level.SEVERE, "Missing cryptpscheme field in the final step message");
            return;
        }
        CSHandler handler;
        CryptoImplementation cryptoImpl = CryptoImplementation.fromString(cryptoScheme);
        handler = CSHandlers.get(cryptoImpl);
        assert handler != null;

        if (cryptoScheme.contains("PSI-CA")) {
            intersectionHandler.CAOPEIntersectionFinalStep(peerData, handler);
        } else if (cryptoScheme.contains("OPE")) {
            intersectionHandler.OPEIntersectionFinalStep(peerData, handler);
        } else {
            intersectionHandler.intersectionFinalStep(peerData, handler);
        }
    }

    /** @noinspection unchecked*/
    public void handleMessage(String message) {
        Gson gson = new Gson();
        LinkedTreeMap<String, Object> peerData = gson.fromJson(message, LinkedTreeMap.class);

        if (peerData.get("step").equals("2")) {
            handleSecondStep(peerData);
        } else if (peerData.get("step").equals("F")) {
            handleFinalStep(peerData);
        } else {
            Node.getInstance().getLogger().log(Level.SEVERE, "Invalid message format: " + message);
        }
    }

    private void handleSecondStep(@NonNull LinkedTreeMap<String, Object> peerData) {
        String peer = (String) peerData.remove("peer");
        Device device = Node.getInstance().getDevicesMap().get(peer);
        if (device == null) {
            Node.getInstance().getLogger().log(Level.SEVERE, "Device not found for peer: " + peer);
            return;
        }
        String implementation = (String) peerData.remove("implementation");

        assert implementation != null;
        CryptoImplementation cryptoImpl = CryptoImplementation.fromString(implementation);
        if (cryptoImpl != null) {
            handleIntersectionSecondStep(device, peer, implementation, peerData);
        } else {
            Node.getInstance().getLogger().log(Level.SEVERE, "Unknown implementation: " + implementation);
        }
    }

    // Para aprovehcar el logging que tiene IntersectioHandler
    public void keygen(@NonNull String s) {
        new Thread(() -> {
            ActivityLogger logger = new LogActivityProxy(new RealActivityLogger());
            CryptoSystem cs;
            try {
                cs = Objects.requireNonNull(CSHandlers.get(CryptoImplementation.fromString(s))).getCryptoSystem();
            } catch (NullPointerException e) {
                Node.getInstance().getLogger().log(Level.SEVERE, "Unknown implementation: " + s);
                return;
            }
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
            logger.logActivity("KEYGEN_" + cs.getClass().getSimpleName(), duration / 1000.0, null, cpuTime);
        }).start();
    }
}
