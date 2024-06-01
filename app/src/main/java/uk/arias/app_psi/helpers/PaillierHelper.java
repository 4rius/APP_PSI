package uk.arias.app_psi.helpers;

import androidx.annotation.NonNull;

import uk.arias.app_psi.implementations.Paillier;
import com.google.gson.internal.LinkedTreeMap;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import uk.arias.app_psi.collections.Polynomials;

public class PaillierHelper extends CSHelper {

    public PaillierHelper(int bitLengthVal) {
        super(new Paillier(bitLengthVal));
    }

    // Serializar clave pública
    public LinkedTreeMap<String, String> serializePublicKey() {
        LinkedTreeMap<String, String> publicKeyDict = new LinkedTreeMap<>();
        publicKeyDict.put("n", ((Paillier)super.getCryptoSystem()).getN().toString());
        return publicKeyDict;
    }

    // Reconstruir clave pública
    public LinkedTreeMap<String, BigInteger>  reconstructPublicKey(@NonNull LinkedTreeMap<String, String> publicKeyDict) {
        LinkedTreeMap<String, BigInteger> publicKey = new LinkedTreeMap<>();
        publicKey.put("n", new BigInteger(Objects.requireNonNull(publicKeyDict.get("n"))));
        return publicKey;
    }

    // Sacar el conjunto multiplicado
    public LinkedTreeMap<String, String> getMultipliedSet(@NonNull LinkedTreeMap<String, BigInteger> encSet, Set<Integer> nodeSet, BigInteger n) {
        LinkedTreeMap<String, String> result = new LinkedTreeMap<>();
        Paillier paillierSender = new Paillier(n);
        for (Map.Entry<String, BigInteger> entry : encSet.entrySet()) {
            int element = Integer.parseInt(entry.getKey());
            if (!nodeSet.contains(element)) {
                BigInteger encryptedZero = paillierSender.Encrypt(BigInteger.ZERO);
                result.put(entry.getKey(), String.valueOf(encryptedZero));
            } else {
                result.put(entry.getKey(), paillierSender.multiplyEncryptedByScalar(entry.getValue(), BigInteger.valueOf(2)).toString());
            }
        }
        return result;
    }

    public ArrayList<String> getOPEEvalList(ArrayList<BigInteger> encryptedCoeff, @NonNull List<Integer> mySet, BigInteger n) {
        ArrayList<String> encryptedResult = new ArrayList<>();
        Paillier PeerPubKey = new Paillier(n);
        SecureRandom rand = new SecureRandom();
        for (int element : mySet) {
            BigInteger rb = BigInteger.valueOf(rand.nextInt(1000) + 1);
            BigInteger Epbj = Polynomials.hornerEvalCrypt(encryptedCoeff, BigInteger.valueOf(element), PeerPubKey);
            BigInteger result = PeerPubKey.Encrypt(BigInteger.valueOf(element));
            BigInteger mult = PeerPubKey.multiplyEncryptedByScalar(Epbj, rb);
            result = PeerPubKey.addEncryptedNumbers(result, mult);
            encryptedResult.add(result.toString());
        }
        return encryptedResult;
    }

    public ArrayList<String> getCardinalityEvalList(List<BigInteger> encryptedCoeff, @NonNull List<Integer> mySet, BigInteger n) {
        ArrayList<String> evaluations = new ArrayList<>();
        Paillier PeerPubKey = new Paillier(n);
        SecureRandom rand = new SecureRandom();
        for (int element : mySet) {
            BigInteger rb = BigInteger.valueOf(rand.nextInt(1000) + 1);
            BigInteger Epbj = Polynomials.hornerEvalCrypt(encryptedCoeff, BigInteger.valueOf(element), PeerPubKey);
            BigInteger mult = PeerPubKey.multiplyEncryptedByScalar(Epbj, rb);
            BigInteger result = PeerPubKey.addEncryptedNumbers(PeerPubKey.Encrypt(BigInteger.valueOf(0)), mult);
            evaluations.add(result.toString());
        }
        Collections.shuffle(evaluations, new SecureRandom());
        return evaluations;
    }

    public LinkedTreeMap<String, String> encryptMyData(Set<Integer> myData, int domain) {
        LinkedTreeMap<String, String> result = new LinkedTreeMap<>();
        for (int element = 0; element < domain; element++) {
            if (!myData.contains(element)) {
                result.put(Integer.toString(element), String.valueOf(super.getCryptoSystem().Encrypt(BigInteger.ZERO)));
            } else {
                result.put(Integer.toString(element), String.valueOf(super.getCryptoSystem().Encrypt(BigInteger.ONE)));
            }
        }
        return result;
    }

    public String getImplementationName() {
        return "Paillier";
    }
}
