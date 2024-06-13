package uk.arias.app_psi.handlers;

import android.os.Debug;
import android.util.Log;

import androidx.annotation.NonNull;

import uk.arias.app_psi.helpers.CSHelper;
import uk.arias.app_psi.collections.Polynomials;
import uk.arias.app_psi.collections.Size;
import uk.arias.app_psi.network.Device;
import uk.arias.app_psi.network.Node;
import com.google.gson.internal.LinkedTreeMap;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OPEHandler extends IntersectionHandler {

    public OPEHandler() {
        super();
    }

    public void intersectionFirstStep(Device device, String peerId, @NonNull CSHelper handler) {
        String impName = handler.getImplementationName();
        getLogger().logStart();
        long startTime = System.currentTimeMillis();
        long startCpuTime = Debug.threadCpuTimeNanos(); // Tiempo de CPU al inicio de la operación
        assert Node.getInstance() != null;
        logIntersectionStart(Node.getInstance().getId(), peerId, impName, null);
        List<Integer> myDataList = new ArrayList<>(Node.getInstance().getMyData());
        List<BigInteger> coefs = Polynomials.polyFromRoots(myDataList, BigInteger.valueOf(-1), BigInteger.ONE);
        ArrayList<String> encryptedCoeffs = handler.encryptRoots(coefs, handler.getCryptoSystem());
        LinkedTreeMap<String, String> publicKeyDict = handler.serializePublicKey();
        sendJsonMessage(device, encryptedCoeffs, impName + " OPE", "2", publicKeyDict);
        long cpuTime = Debug.threadCpuTimeNanos() - startCpuTime; // Tiempo de CPU utilizado por la operación
        long endTime = System.currentTimeMillis();
        Integer myDataSize = Size.getListSizeInBytes(myDataList);
        Integer encryptedCoeffsSize = Size.getListSizeInBytes(encryptedCoeffs);
        getLogger().logStop();
        getLogger().logActivity("INTERSECTION_" + impName + "_OPE_1", (endTime - startTime) / 1000.0, peerId, cpuTime, myDataSize, encryptedCoeffsSize);
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
        assert Node.getInstance() != null;
        List<Integer> myDataList = new ArrayList<>(Node.getInstance().getMyData());
        ArrayList<String> encryptedEval = handler.getOPEEvalList(coefs, myDataList, peerPubKeyReconstructed.get("n"));
        Log.d("Node", Node.getInstance().getId() + " (You) - Intersection with " + peer + " - Encrypted evaluation: " + encryptedEval);
        sendJsonMessage(device, encryptedEval, impName + " OPE", "F", null);
        long cpuTime = Debug.threadCpuTimeNanos();
        long endTime = System.currentTimeMillis();
        int encEvalSize = Size.getListSizeInBytes(encryptedEval);
        getLogger().logStop();
        getLogger().logActivity("INTERSECTION_" + impName + "_OPE_2", (endTime - startTime) / 1000.0, peer, cpuTime, null, encEvalSize);
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
            assert Node.getInstance() != null;
            if (Node.getInstance().getMyData().contains(element.intValue())) {
                intersection.add(element.intValue());
            }
        }
        // Guardamos el resultado, sincronizada por si se hace un broadcast, que no se vayan a perder resultados
        synchronized (Objects.requireNonNull(Node.getInstance()).getResults()) {
            Node.getInstance().getResults().put(peer + " " + impName + " OPE", intersection);
        }
        long cpuTime = Debug.threadCpuTimeNanos() - startCpuTime;
        long end_time = System.currentTimeMillis();
        getLogger().logStop();
        getLogger().logActivity("INTERSECTION_" + impName + "_OPE_F", (end_time - start_time) / 1000.0, peer, cpuTime, null, null);
        int size = intersection.size();
        assert peer != null;
        getLogger().logResult(intersection, size, peer, impName + " OPE");
        System.out.println("Node " + Node.getInstance().getId() + " (You) - Intersection with " + peer + " - Result: " + intersection);
    }
}
