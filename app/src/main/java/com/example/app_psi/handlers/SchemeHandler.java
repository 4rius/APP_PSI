package com.example.app_psi.handlers;

import com.example.app_psi.CryptoImplementation;
import com.example.app_psi.objects.Device;
import com.example.app_psi.objects.Node;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;

import java.util.*;
import java.util.logging.Level;

import static com.example.app_psi.DbConstants.*;

import androidx.annotation.NonNull;

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

        if (handler != null) {
            if (operationType.equals("PSI-Domain")) {
                return intersectionHandler.intersectionFirstStep(device, peerId, handler);
            } else if (operationType.equals("PSI-CA") || operationType.equals("OPE")) {
                return intersectionHandler.OPEIntersectionFirstStep(device, peerId, operationType, handler);
            } else {
                throw new IllegalArgumentException("Invalid operation type: " + operationType);
            }
        } else {
            // Handle invalid cryptoSystem
            throw new IllegalArgumentException("Invalid cryptoSystem: " + cryptoSystem);
        }
    }

    public void launchTest(Device device, String peerId) {
        for (int i = 0; i < TEST_ROUNDS; i++) {
            intersectionHandler.intersectionFirstStep(device, peerId, Objects.requireNonNull(CSHandlers.get(CryptoImplementation.PAILLIER)));
            intersectionHandler.intersectionFirstStep(device, peerId, Objects.requireNonNull(CSHandlers.get(CryptoImplementation.DAMGARD_JURIK)));
            intersectionHandler.OPEIntersectionFirstStep(device, peerId, "PSI", Objects.requireNonNull(CSHandlers.get(CryptoImplementation.PAILLIER)));
            intersectionHandler.OPEIntersectionFirstStep(device, peerId, "PSI", Objects.requireNonNull(CSHandlers.get(CryptoImplementation.DAMGARD_JURIK)));
            intersectionHandler.OPEIntersectionFirstStep(device, peerId, "PSI-CA", Objects.requireNonNull(CSHandlers.get(CryptoImplementation.PAILLIER)));
            intersectionHandler.OPEIntersectionFirstStep(device, peerId, "PSI-CA", Objects.requireNonNull(CSHandlers.get(CryptoImplementation.DAMGARD_JURIK)));
        }
    }

    /** @noinspection unchecked*/
    public void handleIntersectionSecondStep(Device device, String peer, String implementation, @NonNull LinkedTreeMap<String, Object> peerData) {
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
        String cryptoScheme = (String) peerData.remove("cryptpscheme");
        if (cryptoScheme == null) {
            Node.getInstance().getLogger().log(Level.SEVERE, "Missing cryptpscheme field in the final step message");
            return;
        }
        CSHandler handler;
        CryptoImplementation cryptoImpl = CryptoImplementation.fromString(cryptoScheme);
        handler = CSHandlers.get(cryptoImpl);

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

        if (peerData.containsKey("implementation") && peerData.containsKey("peer")) {
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
        } else if (message.startsWith("{")) {
            handleFinalStep(peerData);
        } else {
            Node.getInstance().getLogger().log(Level.SEVERE, "Invalid message format: " + message);
        }
    }

    // Para aprovehcar el logging que tiene IntersectioHandler
    public void keygen(@NonNull String s) {
        switch (s) {
            case "Paillier":
                intersectionHandler.keygen(Objects.requireNonNull(CSHandlers.get(CryptoImplementation.PAILLIER)).getCryptoSystem());
                break;
            case "Damgard-Jurik":
                intersectionHandler.keygen(Objects.requireNonNull(CSHandlers.get(CryptoImplementation.DAMGARD_JURIK)).getCryptoSystem());
                break;
            default:
                throw new IllegalArgumentException("Invalid cryptoSystem: " + s);
        }
    }
}
