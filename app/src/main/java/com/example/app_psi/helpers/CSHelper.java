package com.example.app_psi.helpers;

import androidx.annotation.NonNull;

import com.example.app_psi.implementations.CryptoSystem;
import com.google.gson.internal.LinkedTreeMap;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class CSHelper {

    private final CryptoSystem cs;

    public CSHelper(CryptoSystem cs) {
        this.cs = cs;
    }

    public abstract LinkedTreeMap<String, BigInteger> encryptMyData(Set<Integer> myData, int domain);

    public ArrayList<BigInteger> encryptRoots(@NonNull List<BigInteger> mySet, CryptoSystem cs) {
        ArrayList<BigInteger> result = new ArrayList<>();
        for (BigInteger element : mySet) {
            result.add(cs.Encrypt(element));
        }
        return result;
    }

    public abstract LinkedTreeMap<String, String> serializePublicKey();

    public LinkedTreeMap<String, BigInteger> getEncryptedSet(@NonNull LinkedTreeMap<String, String> serializedEncryptedSet) {
        LinkedTreeMap<String, BigInteger> encryptedSet = new LinkedTreeMap<>();
        for (Map.Entry<String, String> entry : serializedEncryptedSet.entrySet()) {
            encryptedSet.put(entry.getKey(), new BigInteger(entry.getValue()));
        }
        return encryptedSet;
    }
    public abstract LinkedTreeMap<String, BigInteger> getMultipliedSet(LinkedTreeMap<String, BigInteger> encSet, Set<Integer> nodeSet, BigInteger n);
    public LinkedTreeMap<String, BigInteger> handleMultipliedSet(LinkedTreeMap<String, String> serializedMultipliedSet, CryptoSystem cs) {
        LinkedTreeMap<String, BigInteger> evalMap = getEncryptedSet(serializedMultipliedSet);
        for (Map.Entry<String, BigInteger> entry : evalMap.entrySet()) {
            BigInteger decryptedValue = cs.Decrypt(entry.getValue());
            entry.setValue(decryptedValue);
        }
        return evalMap;
    }

    public abstract LinkedTreeMap<String, BigInteger> reconstructPublicKey(LinkedTreeMap<String, String> peerPubKey);
    public abstract ArrayList<BigInteger> handleOPESecondStep(ArrayList<BigInteger> encryptedCoeff, List<Integer> mySet, BigInteger n);

    public abstract ArrayList<BigInteger> getEvaluationSet(List<BigInteger> encryptedCoeff, List<Integer> mySet, BigInteger n);

    public ArrayList<BigInteger> decryptEval(@NonNull ArrayList<BigInteger> encryptedEval, CryptoSystem cs) {
        ArrayList<BigInteger> result = new ArrayList<>();
        for (BigInteger element : encryptedEval) {
            result.add(cs.Decrypt(element));
        }
        return result;
    }

    public abstract String getImplementationName();

    public CryptoSystem getCryptoSystem() {
        return cs;
    }
}
