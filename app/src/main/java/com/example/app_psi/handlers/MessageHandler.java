package com.example.app_psi.handlers;

import com.example.app_psi.objects.Device;
import com.example.app_psi.objects.Node;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.internal.LinkedTreeMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;

public class MessageHandler {
    private final Map<String, BiConsumer<Device, LinkedTreeMap<String, Object>>> implementationMap;
    private final Map<String, Consumer<LinkedTreeMap<String, Object>>> cryptoSchemeMap;

    public MessageHandler() {
        implementationMap = new HashMap<>();
        implementationMap.put("Paillier", this::processPaillier);
        implementationMap.put("DamgardJurik", this::processDamgardJurik);
        implementationMap.put("Damgard-Jurik", this::processDamgardJurik);
        implementationMap.put("Paillier OPE", this::processPaillierOPE);
        implementationMap.put("Damgard-Jurik OPE", this::processDamgardJurikOPE);
        implementationMap.put("DamgardJurik OPE", this::processDamgardJurikOPE);
        implementationMap.put("Paillier CA OPE", this::processPaillierCAOPE);
        implementationMap.put("Damgard-Jurik CA OPE", this::processDamgardJurikCAOPE);
        implementationMap.put("DamgardJurik CA OPE", this::processDamgardJurikCAOPE);

        cryptoSchemeMap = new HashMap<>();
        cryptoSchemeMap.put("Paillier", this::finalStepPaillier);
        cryptoSchemeMap.put("DamgardJurik", this::finalStepDamgardJurik);
    }

    public void handleMessage(String message) {
        Gson gson = new Gson();
        LinkedTreeMap<String, Object> peerData = gson.fromJson(message, LinkedTreeMap.class);

        if (peerData.containsKey("implementation") && peerData.containsKey("peer")) {
            processIntersection(peerData);
        } else if (message.startsWith("{")) {
            processFinalStep(peerData);
        }
    }

    private void processIntersection(LinkedTreeMap<String, Object> peerData) {
        try {
            String peer = (String) peerData.remove("peer");
            Device device = Node.getInstance().getDevicesMap().get(peer);
            if (device == null) {
                throw new Exception("Device not found");
            }
            String implementation = (String) peerData.remove("implementation");

            if (implementationMap.containsKey(implementation)) {
                Objects.requireNonNull(implementationMap.get(implementation)).accept(device, peerData);
            } else {
                throw new Exception("Implementation not supported");
            }
        } catch (Exception e) {
            Node.getInstance().getLogger().log(Level.SEVERE, "Error while processing message", e);
        }
    }

    private void processPaillier(Device device, LinkedTreeMap<String, String> peerData) {
        String peer = peerData.remove("peer");
        LinkedTreeMap<String, String> peerPubKey = (LinkedTreeMap<String, String>) peerData.remove("pubkey");
        assert peerPubKey != null;
        Node.getInstance().getIntersectionHandler().intersectionSecondStep(device, peer, peerPubKey, peerData, Node.getInstance().getIntersectionHandler().getPaillier());
    }

    private void processDamgardJurik(Device device, LinkedTreeMap<String, Object> peerData) {
        Node.getInstance().getIntersectionHandler().intersectionSecondStep(device, peerData, Node.getInstance().getIntersectionHandler().getDamgardJurik());
    }

    private void processPaillierOPE(Device device, LinkedTreeMap<String, Object> peerData) {
        Node.getInstance().getIntersectionHandler().OPEIntersectionSecondStep(device, peerData, Node.getInstance().getIntersectionHandler().getPaillier());
    }

    private void processDamgardJurikOPE(Device device, LinkedTreeMap<String, Object> peerData) {
        Node.getInstance().getIntersectionHandler().OPEIntersectionSecondStep(device, peerData, Node.getInstance().getIntersectionHandler().getDamgardJurik());
    }

    private void processPaillierCAOPE(Device device, LinkedTreeMap<String, Object> peerData) {
        Node.getInstance().getIntersectionHandler().CAOPEIntersectionSecondStep(device, peerData, Node.getInstance().getIntersectionHandler().getPaillier());
    }

    private void processDamgardJurikCAOPE(Device device, LinkedTreeMap<String, Object> peerData) {
        Node.getInstance().getIntersectionHandler().CAOPEIntersectionSecondStep(device, peerData, Node.getInstance().getIntersectionHandler().getDamgardJurik());
    }

    private void processFinalStep(LinkedTreeMap<String, Object> peerData) {
        try {
            String cryptoScheme = (String) peerData.remove("cryptpscheme");
            assert cryptoScheme != null;

            if (cryptoSchemeMap.containsKey(cryptoScheme)) {
                Objects.requireNonNull(cryptoSchemeMap.get(cryptoScheme)).accept(peerData);
            } else {
                throw new Exception("Crypto scheme not supported");
            }
        } catch (Exception e) {
            System.out.println("Received message is not a valid JSON.");
        }
    }

    private void finalStepPaillier(LinkedTreeMap<String, Object> peerData) {
        Node.getInstance().getIntersectionHandler().intersectionFinalStep(peerData, Node.getInstance().getIntersectionHandler().getPaillier());
    }

    private void finalStepDamgardJurik(LinkedTreeMap<String, Object> peerData) {
        Node.getInstance().getIntersectionHandler().intersectionFinalStep(peerData, Node.getInstance().getIntersectionHandler().getDamgardJurik());
    }

    private void finalStepPaillierOPE(LinkedTreeMap<String, Object> peerData) {
        Node.getInstance().getIntersectionHandler().OPEIntersectionFinalStep(peerData, Node.getInstance().getIntersectionHandler().getPaillier());
    }

    private void finalStepDamgardJurikOPE(LinkedTreeMap<String, Object> peerData) {
        Node.getInstance().getIntersectionHandler().OPEIntersectionFinalStep(peerData, Node.getInstance().getIntersectionHandler().getDamgardJurik());
    }

    private void finalStepPaillierCAOPE(LinkedTreeMap<String, Object> peerData) {
        Node.getInstance().getIntersectionHandler().CAOPEIntersectionFinalStep(peerData, Node.getInstance().getIntersectionHandler().getPaillier());
    }

    private void finalStepDamgardJurikCAOPE(LinkedTreeMap<String, Object> peerData) {
        Node.getInstance().getIntersectionHandler().CAOPEIntersectionFinalStep(peerData, Node.getInstance().getIntersectionHandler().getDamgardJurik());
    }
}

