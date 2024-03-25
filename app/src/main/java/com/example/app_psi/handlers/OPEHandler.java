package com.example.app_psi.handlers;

import android.os.Debug;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.app_psi.helpers.CSHelper;
import com.example.app_psi.helpers.IntersectionHelper;
import com.example.app_psi.collections.Polynomials;
import com.example.app_psi.objects.Device;
import com.example.app_psi.objects.Node;
import com.google.gson.internal.LinkedTreeMap;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class OPEHandler extends IntersectionHelper {

    public OPEHandler() {
        super();
    }

    public void intersectionFirstStep(Device device, String peerId, @NonNull CSHelper handler) {
        String impName = handler.getImplementationName();
        getLogger().logStart();
        long startTime = System.currentTimeMillis();
        long startCpuTime = Debug.threadCpuTimeNanos(); // Tiempo de CPU al inicio de la operación
        logIntersectionStart(Node.getInstance().getId(), peerId, impName, null);
        List<Integer> myDataList = new ArrayList<>(Node.getInstance().getMyData());
        List<BigInteger> roots = Polynomials.polyFromRoots(myDataList, BigInteger.valueOf(-1), BigInteger.ONE);
        ArrayList<BigInteger> encryptedRoots = handler.encryptRoots(roots, handler.getCryptoSystem());
        LinkedTreeMap<String, String> publicKeyDict = handler.serializePublicKey();
        sendJsonMessage(device, encryptedRoots, impName + " OPE", "2", publicKeyDict);
        long cpuTime = Debug.threadCpuTimeNanos() - startCpuTime; // Tiempo de CPU utilizado por la operación
        long endTime = System.currentTimeMillis();
        getLogger().logStop();
        getLogger().logActivity("INTERSECTION_" + impName + "_OPE_1", (endTime - startTime) / 1000.0, peerId, cpuTime);
    }

    public void intersectionSecondStep(Device device, String peer, LinkedTreeMap<String, String> peerPubKey, @NonNull ArrayList<String> data, @NonNull CSHelper handler) {
        String impName = handler.getImplementationName();
        getLogger().logStart();
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
        getLogger().logStop();
        getLogger().logActivity("INTERSECTION_" + impName + "_OPE_2", (endTime - startTime) / 1000.0, peer, cpuTime);
    }

    @Override
    public void intersectionSecondStep(Device device, String peer, LinkedTreeMap<String, String> peerPubKey, LinkedTreeMap<String, String> data, CSHelper handler) {
        throw new UnsupportedOperationException("Not supported for this implementation");
    }

    /** @noinspection unchecked*/
    public void intersectionFinalStep(@NonNull LinkedTreeMap<String, Object> peerData, @NonNull CSHelper handler) {
        String impName = handler.getImplementationName();
        getLogger().logStart();
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
        getLogger().logStop();
        getLogger().logActivity("INTERSECTION_" + impName + "_OPE_F", (end_time - start_time) / 1000.0, peer, cpuTime);
        int size = intersection.size();
        assert peer != null;
        getLogger().logResult(intersection, size, peer, impName + " OPE");
        System.out.println("Node " + Node.getInstance().getId() + " (You) - Intersection with " + peer + " - Result: " + intersection);
    }
}
