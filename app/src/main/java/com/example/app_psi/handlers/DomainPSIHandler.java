package com.example.app_psi.handlers;

import android.os.Debug;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.app_psi.helpers.IntersectionHelper;
import com.example.app_psi.objects.Device;
import com.example.app_psi.objects.Node;
import com.google.gson.internal.LinkedTreeMap;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DomainPSIHandler extends IntersectionHelper {

    public DomainPSIHandler() {
        super();
    }

    public void intersectionFirstStep(Device device, String peerId, @NonNull CSHandler handler) {
        String impName = handler.getImplementationName();
        getLogger().logStart();
        long startTime = System.currentTimeMillis();
        long startCpuTime = Debug.threadCpuTimeNanos();
        Log.d("Node", Node.getInstance().getId() + " (You) - Intersection with " + peerId + " - " + impName);
        LinkedTreeMap<String, BigInteger> encryptedSet = handler.encryptMyData(Node.getInstance().getMyData(), Node.getInstance().getDomain());
        LinkedTreeMap<String, String> publicKeyDict = handler.serializePublicKey();
        sendJsonMessage(device, encryptedSet, impName, "2", publicKeyDict);
        long cpuTime = Debug.threadCpuTimeNanos() - startCpuTime;
        long endTime = System.currentTimeMillis();
        getLogger().logStop();
        getLogger().logActivity("INTERSECTION_" + impName + "_1", (endTime - startTime) / 1000.0, peerId, cpuTime);
    }

    @Override
    public void intersectionSecondStep(Device device, String peer, LinkedTreeMap<String, String> peerPubKey, ArrayList<String> data, CSHandler handler) {
        throw new UnsupportedOperationException("Not supported for this implementation");
    }

    public void intersectionSecondStep(Device device, String peer, LinkedTreeMap<String, String> peerPubKey, LinkedTreeMap<String, String> data, @NonNull CSHandler handler) {
        String impName = handler.getImplementationName();
        getLogger().logStart();
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
        getLogger().logStop();
        getLogger().logActivity("INTERSECTION_" + impName + "_2", (end_time - start_time) / 1000.0, peer, cpuTime);
    }



    /** @noinspection unchecked*/
    public void intersectionFinalStep(@NonNull LinkedTreeMap<String, Object> peerData, @NonNull CSHandler handler) {
        String impName = handler.getImplementationName();
        getLogger().logStart();
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
        getLogger().logStop();
        getLogger().logActivity("INTERSECTION_" + impName + "_F", (end_time - start_time) / 1000.0, peer, cpuTime);
        int size = intersection.size();
        assert peer != null;
        getLogger().logResult(intersection, size, peer, impName);
        System.out.println("Node " + Node.getInstance().getId() + " (You) - Intersection with " + peer + " - Result: " + intersection);
    }
}
