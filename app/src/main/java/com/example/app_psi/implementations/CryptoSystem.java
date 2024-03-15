package com.example.app_psi.implementations;

import androidx.annotation.NonNull;

import com.google.gson.internal.LinkedTreeMap;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public interface CryptoSystem {
    BigInteger multiplyEncryptedByScalar(BigInteger encryptedNumber, BigInteger scalar);
    BigInteger addEncryptedNumbers(BigInteger encryptedNumber1, BigInteger encryptedNumber2);
    BigInteger addEncryptedAndScalar(@NonNull BigInteger a, BigInteger b);

    ArrayList<BigInteger> encryptMySet(Set<Integer> mySet);

    default LinkedTreeMap<String, BigInteger> encryptMyData(Set<Integer> myData, int domain) {
        LinkedTreeMap<String, BigInteger> result = new LinkedTreeMap<>();
        for (int element = 0; element < domain; element++) {
            if (!myData.contains(element)) {
                result.put(Integer.toString(element), Encrypt(BigInteger.ZERO));
            } else {
                result.put(Integer.toString(element), Encrypt(BigInteger.ONE));
            }
        }
        return result;
    }

    ArrayList<BigInteger> encryptRoots(List<BigInteger> mySet);

    LinkedTreeMap<String, String> serializePublicKey();

    LinkedTreeMap<String, BigInteger> getEncryptedSet(LinkedTreeMap<String, String> serializedEncryptedSet);
    LinkedTreeMap<String, BigInteger> getMultipliedSet(LinkedTreeMap<String, BigInteger> encSet, Set<Integer> nodeSet, BigInteger n);
    LinkedTreeMap<String, BigInteger> recvMultipliedSet(LinkedTreeMap<String, String> serializedMultipliedSet);

    LinkedTreeMap<String, BigInteger> reconstructPublicKey(LinkedTreeMap<String, String> peerPubKey);
    ArrayList<BigInteger> handleOPESecondStep(ArrayList<BigInteger> encryptedCoeff, List<Integer> mySet, BigInteger n);

    ArrayList<BigInteger> getEvaluationSet(List<BigInteger> encryptedCoeff, List<Integer> mySet, BigInteger n);

    BigInteger Decrypt(BigInteger encryptedNumber);
    BigInteger Encrypt(BigInteger number);

    void keyGeneration(int bitLength);
}
