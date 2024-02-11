package com.example.app_psi.implementations;

import com.google.gson.internal.LinkedTreeMap;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Set;

public class DamgardJurik {

    private BigInteger n;  // Módulo, clave pública
    private BigInteger nPowSPlusOne;  // n^s+1 la diferencia con Paillier es que nSquared es n^2
    private BigInteger g;  // Generador, clave pública, puede ser n+1
    private BigInteger lambda;  // Lambda, clave privada
    private int s;  // Factor de expansión

    // Constructor
    public DamgardJurik(int bitLength, int expansionFactor) {
        this.s = expansionFactor;
        keyGeneration(bitLength);
    }

    // Constructor para cifrar lo que mande un nodo
    public DamgardJurik(BigInteger n, int expansionFactor) {
        this.n = n;
        this.s = expansionFactor;
        this.nPowSPlusOne = n.pow(s + 1);
        while (!isValidGenerator(g)) {
            System.out.println("g is not good enough. Trying again...");
            g = g.add(BigInteger.ONE);
        }
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
        nPowSPlusOne = n.pow(s + 1);
        lambda = p.subtract(BigInteger.ONE).multiply(q.subtract(BigInteger.ONE)).divide(p.subtract(BigInteger.ONE).gcd(q.subtract(BigInteger.ONE)));

        g = n.add(BigInteger.ONE);
        while (!isValidGenerator(g)) {
            System.out.println("g is not good enough. Trying again...");
            g = g.add(BigInteger.ONE);
        }
    }

    // Comprueba si g es válido, si g es coprimo con n y si n divide el orden de g
    private boolean isValidGenerator(BigInteger g) {
        BigInteger exp = n.pow(s + 1);
        return g.modPow(lambda, exp).subtract(BigInteger.ONE).divide(n).gcd(n).equals(BigInteger.ONE);
    }

    // Cifrado
    public BigInteger Encrypt(BigInteger plaintext) {
        SecureRandom random = new SecureRandom();
        BigInteger r = new BigInteger(n.bitLength(), random).mod(n);
        return g.modPow(plaintext, n.pow(s + 1))
                .multiply(r.modPow(n.pow(s), n.pow(s + 1)))
                .mod(n.pow(s + 1));
    }

    // Descifrado
    public BigInteger Decrypt(BigInteger ciphertext) {
        BigInteger u = ciphertext.modPow(lambda, nPowSPlusOne).subtract(BigInteger.ONE).divide(n);
        BigInteger v = g.modPow(lambda, nPowSPlusOne).subtract(BigInteger.ONE).divide(n).modInverse(n);
        return u.multiply(v).mod(n);
    }

    // Getters para los valores de la clave pública
    public BigInteger getN() {
        return n;
    }

    public BigInteger getG() {
        return g;
    }

    // Suma homomórfica de dos textos cifrados
    public BigInteger addCiphertexts(BigInteger ciphertext1, BigInteger ciphertext2) {
        return ciphertext1.multiply(ciphertext2).mod(nPowSPlusOne);
    }

    // Multiplicación homomórfica de un texto cifrado por un escalar
    public BigInteger multiplyCiphertextByScalar(BigInteger ciphertext, BigInteger scalar) {
        return ciphertext.modPow(scalar, nPowSPlusOne);
    }



    // Operaciones de serialización, deserialización, cifrado de sets y cálculo de intersecciones
    // Serialización clave pública
    public LinkedTreeMap<String, String> serializePublicKey() {
        LinkedTreeMap<String, String> publicKeyDict = new LinkedTreeMap<>();
        publicKeyDict.put("n", n.toString());
        publicKeyDict.put("g", g.toString());
        return publicKeyDict;
    }

    // Reconstruir clave pública
    public LinkedTreeMap<String, BigInteger> reconstructPublicKey(LinkedTreeMap<String, String> publicKeyDict) {
        n = new BigInteger(publicKeyDict.get("n"));
        g = new BigInteger(publicKeyDict.get("g"));
        LinkedTreeMap<String, BigInteger> pubkey = new LinkedTreeMap<>();
        pubkey.put("n", n);
        pubkey.put("g", g);
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

    public LinkedTreeMap<String, BigInteger> recvMultipliedSet(LinkedTreeMap<String, String> serializedMultipliedSet) {
        return getEncryptedSet(serializedMultipliedSet);
    }

    // Sacar el conjunto multiplicado
    public LinkedTreeMap<String, BigInteger> getMultipliedSet(LinkedTreeMap<String, BigInteger> encSet, Set<Integer> nodeSet, BigInteger n) {
        LinkedTreeMap<String, BigInteger> result = new LinkedTreeMap<>();
        DamgardJurik djsender = new DamgardJurik(n, 2);
        for (Map.Entry<String, BigInteger> entry : encSet.entrySet()) {
            int element = Integer.parseInt(entry.getKey());
            if (!nodeSet.contains(element)) {
                BigInteger encryptedZero = djsender.Encrypt(BigInteger.ZERO);
                result.put(entry.getKey(), encryptedZero);
                // Sale 1... result.put(entry.getKey(), paillierSender.homomorphicMultiply(entry.getValue(), BigInteger.ZERO, paillierSender.nsquare));
            } else {
                result.put(entry.getKey(), djsender.multiplyCiphertextByScalar(entry.getValue(), BigInteger.ONE));
            }
        }
        return result;
    }
}
