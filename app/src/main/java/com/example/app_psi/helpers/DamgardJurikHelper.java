package com.example.app_psi.helpers;

import static com.example.app_psi.collections.DbConstants.DFL_EXPANSION_FACTOR;
import static com.example.app_psi.collections.Polynomials.hornerEvalCrypt;

import androidx.annotation.NonNull;

import com.example.app_psi.implementations.DamgardJurik;
import com.google.gson.internal.LinkedTreeMap;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class DamgardJurikHelper extends CSHelper {

    public DamgardJurikHelper(int bitLengthVal, int expansionFactor) {
        super(new DamgardJurik(bitLengthVal, expansionFactor));
    }

    // Operaciones de serialización, deserialización, cifrado de sets y cálculo de intersecciones
    // Serialización clave pública
    public LinkedTreeMap<String, String> serializePublicKey() {
        LinkedTreeMap<String, String> publicKeyDict = new LinkedTreeMap<>();
        publicKeyDict.put("n", ((DamgardJurik)super.getCryptoSystem()).getN().toString());
        publicKeyDict.put("s", String.valueOf(((DamgardJurik)super.getCryptoSystem()).getS()));
        return publicKeyDict;
    }

    // Reconstruir clave pública
    public LinkedTreeMap<String, BigInteger> reconstructPublicKey(LinkedTreeMap<String, String> publicKeyDict) {
        BigInteger newN = new BigInteger(Objects.requireNonNull(publicKeyDict.get("n")));
        BigInteger newS = new BigInteger(Objects.requireNonNull(publicKeyDict.get("s")));
        LinkedTreeMap<String, BigInteger> pubkey = new LinkedTreeMap<>();
        pubkey.put("n", newN);
        pubkey.put("s", newS);
        return pubkey;
    }

    // Sacar el conjunto multiplicado
    public LinkedTreeMap<String, String> getMultipliedSet(@NonNull LinkedTreeMap<String, BigInteger> encSet, Set<Integer> nodeSet, BigInteger n) {
        LinkedTreeMap<String, String> result = new LinkedTreeMap<>();
        DamgardJurik djsender = new DamgardJurik(n, DFL_EXPANSION_FACTOR);
        for (Map.Entry<String, BigInteger> entry : encSet.entrySet()) {
            int element = Integer.parseInt(entry.getKey());
            if (!nodeSet.contains(element)) {
                BigInteger encryptedZero = djsender.Encrypt(BigInteger.ZERO);
                result.put(entry.getKey(), String.valueOf(encryptedZero));
            } else {
                result.put(entry.getKey(), String.valueOf(djsender.multiplyEncryptedByScalar(entry.getValue(), BigInteger.ONE)));
            }
        }
        return result;
    }

    public ArrayList<String> handleOPESecondStep(ArrayList<BigInteger> encryptedCoeff, @NonNull List<Integer> mySet, BigInteger n) {
        ArrayList<String> encryptedResult = new ArrayList<>();
        SecureRandom rand = new SecureRandom();
        DamgardJurik PeerPubKey = new DamgardJurik(n, 2);
        for (Integer element : mySet) {
            BigInteger rb = BigInteger.valueOf(rand.nextInt(1000) + 1);
            BigInteger Epbj = hornerEvalCrypt(encryptedCoeff, BigInteger.valueOf(element), PeerPubKey);
            BigInteger result = PeerPubKey.Encrypt(BigInteger.valueOf(element));
            BigInteger mult = PeerPubKey.multiplyEncryptedByScalar(Epbj, rb);
            result = PeerPubKey.addEncryptedNumbers(result, mult);
            encryptedResult.add(result.toString());
        }
        return encryptedResult;
    }

    public ArrayList<String> getEvaluationSet(List<BigInteger> encryptedCoeff, @NonNull List<Integer> mySet, BigInteger n) {
        ArrayList<String> evaluations = new ArrayList<>();
        DamgardJurik PeerPubKey = new DamgardJurik(n, 2);
        SecureRandom rand = new SecureRandom();
        for (Integer element : mySet) {
            BigInteger rb = BigInteger.valueOf(rand.nextInt(1000) + 1);
            BigInteger Epbj = hornerEvalCrypt(encryptedCoeff, BigInteger.valueOf(element), PeerPubKey);
            BigInteger mult = PeerPubKey.multiplyEncryptedByScalar(Epbj, rb);
            BigInteger result = PeerPubKey.addEncryptedNumbers(PeerPubKey.Encrypt(BigInteger.valueOf(0)), mult);
            evaluations.add(result.toString());
        }
        // Shuffle the evaluations
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
        return "DamgardJurik";
    }
}
