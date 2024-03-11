package com.example.app_psi.implementations;

import static com.example.app_psi.implementations.Polynomials.hornerEvalCrypt;

import androidx.annotation.NonNull;

import com.google.gson.internal.LinkedTreeMap;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class DamgardJurik implements CryptoSystem {

    private BigInteger n;  // Módulo, clave pública
    private BigInteger nPowSPlusOne;  // n^s+1 la diferencia con Paillier es que nSquared es n^2
    private BigInteger ns;  // n^s
    private BigInteger g;  // Generador, clave pública, puede ser n+1
    private BigInteger lambda;  // Lambda, clave privada
    private final int s;  // Factor de expansión

    // Constructor
    public DamgardJurik(int bitLength, int expansionFactor) {
        this.s = expansionFactor;
        keyGeneration(bitLength);
    }

    // Constructor para cifrar lo que mande un nodo
    public DamgardJurik(BigInteger n, int expansionFactor) {
        this.n = n;
        this.s = expansionFactor;
        this.ns = n.pow(s);
        this.g = n.add(BigInteger.ONE);
        this.nPowSPlusOne = n.pow(s + 1);
    }

    // Generación de claves
    public void keyGeneration(int bitLength) {
        SecureRandom random = new SecureRandom();
        BigInteger p, q;
        do {
            p = BigInteger.probablePrime(bitLength / 2, random);
            q = BigInteger.probablePrime(bitLength / 2, random);
        } while (p.equals(q));

        n = p.multiply(q);
        ns = n.pow(s);
        nPowSPlusOne = n.pow(s + 1);
        lambda = p.subtract(BigInteger.ONE).multiply(q.subtract(BigInteger.ONE)).divide(p.subtract(BigInteger.ONE).gcd(q.subtract(BigInteger.ONE)));

        g = n.add(BigInteger.ONE);
        while (isValidGenerator(g)) {
            System.out.println("g is not good enough. Trying again...");
            g = g.add(BigInteger.ONE);
        }
    }


    // Comprueba si g es válido, si g es coprimo con n y si n divide el orden de g
    private boolean isValidGenerator(BigInteger g) {
        BigInteger exp = n.pow(s + 1);
        return !g.modPow(lambda, exp).subtract(BigInteger.ONE).divide(n).gcd(n).equals(BigInteger.ONE);
    }

    // Cifrado
    public BigInteger Encrypt(BigInteger plaintext) {
        BigInteger r;
        do {
            r = new BigInteger(n.bitLength(), new SecureRandom());
        } while (r.compareTo(n) >= 0);
        return g.modPow(plaintext, nPowSPlusOne).multiply(r.modPow(ns, nPowSPlusOne)).mod(nPowSPlusOne);
    }

    // Descifrado
    public BigInteger Decrypt(BigInteger ciphertext) {
        BigInteger u = g.modPow(lambda, nPowSPlusOne).subtract(BigInteger.ONE).divide(n).modInverse(n);
        return ciphertext.modPow(lambda, nPowSPlusOne).subtract(BigInteger.ONE).divide(n).multiply(u).mod(n);
    }


    // Getters para los valores de la clave pública
    public BigInteger getN() {
        return n;
    }

    public BigInteger getG() {
        return g;
    }

    // Suma homomórfica de dos textos cifrados
    public BigInteger addEncryptedNumbers(BigInteger ciphertext1, BigInteger ciphertext2) {
        return ciphertext1.multiply(ciphertext2).mod(nPowSPlusOne);
    }

    // Multiplicación homomórfica de un texto cifrado por un escalar
    public BigInteger multiplyEncryptedByScalar(BigInteger ciphertext, BigInteger scalar) {
        return ciphertext.modPow(scalar, nPowSPlusOne);
    }



    // Operaciones de serialización, deserialización, cifrado de sets y cálculo de intersecciones
    // Serialización clave pública
    public LinkedTreeMap<String, String> serializePublicKey() {
        LinkedTreeMap<String, String> publicKeyDict = new LinkedTreeMap<>();
        publicKeyDict.put("n", n.toString());
        publicKeyDict.put("s", String.valueOf(s));
        return publicKeyDict;
    }

    // Reconstruir clave pública
    public LinkedTreeMap<String, BigInteger> reconstructPublicKey(LinkedTreeMap<String, String> publicKeyDict) {
        BigInteger newN = new BigInteger(publicKeyDict.get("n"));
        BigInteger newS = new BigInteger(publicKeyDict.get("s"));
        LinkedTreeMap<String, BigInteger> pubkey = new LinkedTreeMap<>();
        pubkey.put("n", newN);
        pubkey.put("s", newS);
        return pubkey;
    }

    public LinkedTreeMap<String, BigInteger> getEncryptedSet(LinkedTreeMap<String, String> serializedEncryptedSet) {
        LinkedTreeMap<String, BigInteger> encryptedSet = new LinkedTreeMap<>();
        for (Map.Entry<String, String> entry : serializedEncryptedSet.entrySet()) {
            encryptedSet.put(entry.getKey(), new BigInteger(entry.getValue()));
        }
        return encryptedSet;
    }

    // El dominio podría ser el tamaño del conjunto de datos cuando se haga con OPEs
    public LinkedTreeMap<String, BigInteger> encryptMyData(Set<Integer> mySet, int domain) {
        LinkedTreeMap<String, BigInteger> result = new LinkedTreeMap<>();
        for (int element = 0; element < domain; element++) {
            if (!mySet.contains(element)) {
                result.put(Integer.toString(element), Encrypt(BigInteger.ZERO));
            } else {
                result.put(Integer.toString(element), Encrypt(BigInteger.ONE));
            }
        }
        return result;
    }

    public ArrayList<BigInteger> encryptMySet(Set<Integer> mySet) {
        ArrayList<BigInteger> result = new ArrayList<>();
        for (int element : mySet) {
            result.add(Encrypt(BigInteger.valueOf(element)));
        }
        return result;
    }

    public ArrayList<BigInteger> encryptRoots(List<BigInteger> mySet) {
        ArrayList<BigInteger> result = new ArrayList<>();
        for (BigInteger element : mySet) {
            result.add(Encrypt(element));
        }
        return result;
    }

    public LinkedTreeMap<String, BigInteger> recvMultipliedSet(LinkedTreeMap<String, String> serializedMultipliedSet) {
        return getEncryptedSet(serializedMultipliedSet);
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
                // Sale 1... result.put(entry.getKey(), paillierSender.homomorphicMultiply(entry.getValue(), BigInteger.ZERO, paillierSender.nsquare));
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
            BigInteger rb = new BigInteger(1000, rand).add(BigInteger.ONE);
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
            BigInteger rb = new BigInteger(1000, rand).add(BigInteger.ONE);
            BigInteger Epbj = hornerEvalCrypt(encryptedCoeff, BigInteger.valueOf(element), PeerPubKey);
            BigInteger mult = PeerPubKey.multiplyEncryptedByScalar(Epbj, rb);
            BigInteger result = PeerPubKey.addEncryptedNumbers(PeerPubKey.Encrypt(BigInteger.ZERO), mult);
            evaluations.add(result);
        }
        // Shuffle the evaluations
        Collections.shuffle(evaluations, new SecureRandom());
        return evaluations;
    }
}
