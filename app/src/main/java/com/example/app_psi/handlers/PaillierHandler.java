package com.example.app_psi.handlers;

import static com.example.app_psi.implementations.Polynomials.hornerEvalCrypt;

import androidx.annotation.NonNull;

import com.example.app_psi.implementations.CryptoSystem;
import com.example.app_psi.implementations.Paillier;
import com.google.gson.internal.LinkedTreeMap;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class PaillierHandler implements CSHandler {

    private final Paillier paillier;

    public PaillierHandler(int bitLengthVal) {
        paillier = new Paillier(bitLengthVal);
    }

    // Serializar clave pública
    public LinkedTreeMap<String, String> serializePublicKey() {
        LinkedTreeMap<String, String> publicKeyDict = new LinkedTreeMap<>();
        publicKeyDict.put("n", paillier.getN().toString());
        return publicKeyDict;
    }

    // Reconstruir clave pública
    public LinkedTreeMap<String, BigInteger>  reconstructPublicKey(@NonNull LinkedTreeMap<String, String> publicKeyDict) {
        LinkedTreeMap<String, BigInteger> publicKey = new LinkedTreeMap<>();
        publicKey.put("n", new BigInteger(Objects.requireNonNull(publicKeyDict.get("n"))));
        return publicKey;
    }

    // Sacar el conjunto multiplicado
    public LinkedTreeMap<String, BigInteger> getMultipliedSet(@NonNull LinkedTreeMap<String, BigInteger> encSet, Set<Integer> nodeSet, BigInteger n) {
        LinkedTreeMap<String, BigInteger> result = new LinkedTreeMap<>();
        Paillier paillierSender = new Paillier(n);
        for (Map.Entry<String, BigInteger> entry : encSet.entrySet()) {
            int element = Integer.parseInt(entry.getKey());
            if (!nodeSet.contains(element)) {
                BigInteger encryptedZero = paillierSender.Encrypt(BigInteger.ZERO);
                result.put(entry.getKey(), encryptedZero);
            } else {
                result.put(entry.getKey(), paillierSender.multiplyEncryptedByScalar(entry.getValue(), BigInteger.ONE));
            }
        }
        return result;
    /*En el sistema criptográfico de Paillier, la multiplicación de un número cifrado por un número
    sin cifrar se realiza mediante la exponenciación, no mediante la multiplicación ordinaria.
    Esto es lo que permite que el sistema mantenga su propiedad de homomorfismo. Esto lo tengo que probar
    porque en la phe venía directamente sobrecargado, aquí hay que lidiar con ello.*/
    }

    public ArrayList<BigInteger> handleOPESecondStep(ArrayList<BigInteger> encryptedCoeff, @NonNull List<Integer> mySet, BigInteger n) {
        ArrayList<BigInteger> encryptedResult = new ArrayList<>();
        Paillier PeerPubKey = new Paillier(n);
        SecureRandom rand = new SecureRandom();
        for (int element : mySet) {
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
        Paillier PeerPubKey = new Paillier(n);
        SecureRandom rand = new SecureRandom();
        for (int element : mySet) {
            BigInteger rb = BigInteger.valueOf(rand.nextInt(1000) + 1);
            BigInteger Epbj = hornerEvalCrypt(encryptedCoeff, BigInteger.valueOf(element), PeerPubKey);
            BigInteger mult = PeerPubKey.multiplyEncryptedByScalar(Epbj, rb);
            BigInteger result = PeerPubKey.addEncryptedNumbers(PeerPubKey.Encrypt(BigInteger.valueOf(0)), mult);
            evaluations.add(result);
        }
        Collections.shuffle(evaluations, new SecureRandom());
        return evaluations;
    }

    public LinkedTreeMap<String, BigInteger> encryptMyData(Set<Integer> myData, int domain) {
        LinkedTreeMap<String, BigInteger> result = new LinkedTreeMap<>();
        for (int element = 0; element < domain; element++) {
            if (!myData.contains(element)) {
                result.put(Integer.toString(element), paillier.Encrypt(BigInteger.ZERO));
            } else {
                result.put(Integer.toString(element), paillier.Encrypt(BigInteger.ONE));
            }
        }
        return result;
    }

    public String getImplementationName() {
        return "Paillier";
    }

    public CryptoSystem getCryptoSystem() {
        return paillier;
    }
}
