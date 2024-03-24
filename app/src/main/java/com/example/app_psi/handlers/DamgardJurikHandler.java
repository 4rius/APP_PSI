package com.example.app_psi.handlers;

import static com.example.app_psi.implementations.Polynomials.hornerEvalCrypt;

import androidx.annotation.NonNull;

import com.example.app_psi.implementations.CryptoSystem;
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

public class DamgardJurikHandler implements CSHandler {

    private final DamgardJurik dj;

    public DamgardJurikHandler(int bitLengthVal, int expansionFactor) {
        dj = new DamgardJurik(bitLengthVal, expansionFactor);
    }

    // Operaciones de serialización, deserialización, cifrado de sets y cálculo de intersecciones
    // Serialización clave pública
    public LinkedTreeMap<String, String> serializePublicKey() {
        LinkedTreeMap<String, String> publicKeyDict = new LinkedTreeMap<>();
        publicKeyDict.put("n", dj.getN().toString());
        publicKeyDict.put("s", String.valueOf(dj.getS()));
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
    public LinkedTreeMap<String, BigInteger> getMultipliedSet(@NonNull LinkedTreeMap<String, BigInteger> encSet, Set<Integer> nodeSet, BigInteger n) {
        LinkedTreeMap<String, BigInteger> result = new LinkedTreeMap<>();
        DamgardJurik djsender = new DamgardJurik(n, 2);
        for (Map.Entry<String, BigInteger> entry : encSet.entrySet()) {
            int element = Integer.parseInt(entry.getKey());
            if (!nodeSet.contains(element)) {
                BigInteger encryptedZero = djsender.Encrypt(BigInteger.ZERO);
                result.put(entry.getKey(), encryptedZero);
            } else {
                result.put(entry.getKey(), djsender.multiplyEncryptedByScalar(entry.getValue(), BigInteger.ONE));
            }
        }
        return result;
    }

    public ArrayList<BigInteger> handleOPESecondStep(ArrayList<BigInteger> encryptedCoeff, @NonNull List<Integer> mySet, BigInteger n) {
        ArrayList<BigInteger> encryptedResult = new ArrayList<>();
        SecureRandom rand = new SecureRandom();
        DamgardJurik PeerPubKey = new DamgardJurik(n, 2);
        for (Integer element : mySet) {
            BigInteger rb = BigInteger.valueOf(rand.nextInt(1000) + 1);
            BigInteger Epbj = hornerEvalCrypt(encryptedCoeff, BigInteger.valueOf(element), PeerPubKey);
            BigInteger result = PeerPubKey.Encrypt(BigInteger.valueOf(element));
            BigInteger mult = PeerPubKey.multiplyEncryptedByScalar(Epbj, rb);
            result = PeerPubKey.addEncryptedNumbers(result, mult);
            encryptedResult.add(result);
        }
        return encryptedResult;
    }

    public ArrayList<BigInteger> getEvaluationSet(List<BigInteger> encryptedCoeff, @NonNull List<Integer> mySet, BigInteger n) {
        ArrayList<BigInteger> evaluations = new ArrayList<>();
        DamgardJurik PeerPubKey = new DamgardJurik(n, 2);
        SecureRandom rand = new SecureRandom();
        for (Integer element : mySet) {
            BigInteger rb = BigInteger.valueOf(rand.nextInt(1000) + 1);
            BigInteger Epbj = hornerEvalCrypt(encryptedCoeff, BigInteger.valueOf(element), PeerPubKey);
            BigInteger mult = PeerPubKey.multiplyEncryptedByScalar(Epbj, rb);
            BigInteger result = PeerPubKey.addEncryptedNumbers(PeerPubKey.Encrypt(BigInteger.valueOf(0)), mult);
            evaluations.add(result);
        }
        // Shuffle the evaluations
        Collections.shuffle(evaluations, new SecureRandom());
        return evaluations;
    }

    public LinkedTreeMap<String, BigInteger> encryptMyData(Set<Integer> myData, int domain) {
        LinkedTreeMap<String, BigInteger> result = new LinkedTreeMap<>();
        for (int element = 0; element < domain; element++) {
            if (!myData.contains(element)) {
                result.put(Integer.toString(element), dj.Encrypt(BigInteger.ZERO));
            } else {
                result.put(Integer.toString(element), dj.Encrypt(BigInteger.ONE));
            }
        }
        return result;
    }

    public String getImplementationName() {
        return "DamgardJurik";
    }

    public CryptoSystem getCryptoSystem() {
        return dj;
    }
}
