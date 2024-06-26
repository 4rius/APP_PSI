package uk.arias.app_psi.handlers;

import android.os.Debug;

import androidx.annotation.NonNull;

import uk.arias.app_psi.collections.Size;
import uk.arias.app_psi.helpers.CSHelper;
import uk.arias.app_psi.collections.Polynomials;
import uk.arias.app_psi.network.Device;
import uk.arias.app_psi.network.Node;
import com.google.gson.internal.LinkedTreeMap;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OPECAHandler extends IntersectionHandler {

    public OPECAHandler() {
        super();
    }

    public void intersectionFirstStep(Device device, String peerId, @NonNull CSHelper handler) {
        String impName = handler.getImplementationName();
        getLogger().logStart();
        long startTime = System.currentTimeMillis();
        long startCpuTime = Debug.threadCpuTimeNanos(); // Tiempo de CPU al inicio de la operación
        assert Node.getInstance() != null;
        logIntersectionStart(Node.getInstance().getId(), peerId, impName, "PSI-CA");
        List<Integer> myDataList = new ArrayList<>(Node.getInstance().getMyData());
        List<BigInteger> coefs = Polynomials.polyFromRoots(myDataList, BigInteger.valueOf(-1), BigInteger.ONE);
        ArrayList<String> encryptedCoeffs = handler.encryptRoots(coefs, handler.getCryptoSystem());
        LinkedTreeMap<String, String> publicKeyDict = handler.serializePublicKey();
        sendJsonMessage(device, encryptedCoeffs, impName + " PSI-CA OPE", "2", publicKeyDict);
        long cpuTime = Debug.threadCpuTimeNanos() - startCpuTime; // Tiempo de CPU utilizado por la operación
        long endTime = System.currentTimeMillis();
        int myDataSize = Size.getListSizeInBytes(myDataList);
        int encryptedCoeffsSize = Size.getListSizeInBytes(encryptedCoeffs);
        getLogger().logStop();
        getLogger().logActivity("CARDINALITY_" + impName + "_OPE_1", (endTime - startTime) / 1000.0, peerId, cpuTime, myDataSize, encryptedCoeffsSize);
    }

    public void intersectionSecondStep(Device device, String peer, LinkedTreeMap<String, String> peerPubKey, @NonNull ArrayList<String> data, @NonNull CSHelper handler) {
        String impName = handler.getImplementationName();
        getLogger().logStart();
        long start_time = System.currentTimeMillis();
        long startCpuTime = Debug.threadCpuTimeNanos();
        LinkedTreeMap<String, BigInteger> peerPubKeyReconstructed = handler.reconstructPublicKey(peerPubKey);
        // Obtenemos las raíces cifradas del peer
        ArrayList<BigInteger> coefs = new ArrayList<>();
        for (String element : data) {
            coefs.add(new BigInteger(element));
        }
        // Evaluamos el polinomio con las raíces del peer
        assert Node.getInstance() != null;
        List<Integer> myDataList = new ArrayList<>(Node.getInstance().getMyData());
        ArrayList<String> encryptedEval = handler.getCardinalityEvalList(coefs, myDataList, peerPubKeyReconstructed.get("n"));
        System.out.println("Node " + Node.getInstance().getId() + " (You) - PSI-CA with " + peer + " - Encrypted evalutaion: " + encryptedEval);
        // Shuffle the encrypted evaluation to not reveal positional information
        Collections.shuffle(encryptedEval);
        sendJsonMessage(device, encryptedEval, impName + " PSI-CA OPE", "F", null);
        long cpuTime = Debug.threadCpuTimeNanos() - startCpuTime;
        long end_time = System.currentTimeMillis();
        int encEvalSize = Size.getListSizeInBytes(encryptedEval);
        getLogger().logStop();
        getLogger().logActivity("CARDINALITY_" + impName + "_OPE_2", (end_time - start_time) / 1000.0, peer, cpuTime, null, encEvalSize);
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
        ArrayList<BigInteger> decryptedEval = handler.decryptEval(encryptedEval, handler.getCryptoSystem());
        int result = 0;
        for (BigInteger element : decryptedEval) {
            // Cada 0 representa un elemento que está en el conjunto
            if (element.equals(BigInteger.ZERO)) {
                result++;
            }
        }
        assert Node.getInstance() != null;
        String id = Node.getInstance().getId();
        synchronized (Node.getInstance().getResults()) {
            Node.getInstance().getResults().put(id + " " + impName + " PSI-CA OPE", result);
        }
        long cpuTime = Debug.threadCpuTimeNanos() - startCpuTime;
        long end_time = System.currentTimeMillis();
        getLogger().logStop();
        getLogger().logActivity("CARDINALITY_" + impName + "_OPE_F", (end_time - start_time) / 1000.0, id, cpuTime, null, null);
        getLogger().logResult(null, result, id, impName + "_PSI-CA_OPE");
        System.out.println("Node " + id + " (You) - " + impName + " PSI-CA with " + id + " - Result: " + result);
    }
}
