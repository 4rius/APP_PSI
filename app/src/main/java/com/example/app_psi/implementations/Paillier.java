package com.example.app_psi.implementations;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.security.SecureRandom;
import java.util.Set;

public class Paillier {

    private BigInteger p, q, lambda; // Variables para almacenar los números primos y lambda
    public BigInteger n; // Clave pública
    public BigInteger nsquare; // n al cuadrado, se usa en el cifrado y descifrado
    private BigInteger g; // Número que se usa en el cifrado y descifrado
    private int bitLength; // Longitud de los números primos

    // Constructor que genera las claves
    public Paillier(int bitLengthVal, int certainty) {
        KeyGeneration(bitLengthVal, certainty);
    }

    // Método para generar las claves, certeza es el grado de certeza con el que queremos que los números generados sean primos
    // A más certeza, más tiempo de computación en generar las claves
    public void KeyGeneration(int bitLengthVal, int certainty) {
        bitLength = bitLengthVal;
        SecureRandom r = new SecureRandom();
        p = new BigInteger(bitLength / 2, certainty, r); // Genera un número primo p
        q = new BigInteger(bitLength / 2, certainty, r); // Genera un número primo q
        n = p.multiply(q); // Calcula n = p * q, que se usa como la clave pública
        nsquare = n.multiply(n); // Calcula n al cuadrado
        g = new BigInteger("2"); // Establece g como 2 por simplicidad y eficiencia
        lambda = p.subtract(BigInteger.ONE).multiply(q.subtract(BigInteger.ONE)).divide(
                p.subtract(BigInteger.ONE).gcd(q.subtract(BigInteger.ONE))); // Calcula lambda
        // Comprueba si g es válido, si no lo es, termina el programa
        // n tiene que poder dividir el orden de g
        if (g.modPow(lambda, nsquare).subtract(BigInteger.ONE).divide(n).gcd(n).intValue() != 1) {
            System.out.println("g is not good enough. Trying again...");
            System.exit(1);
        }
    }

    // Cifrado de un número
    public BigInteger Encryption(BigInteger m, BigInteger r) {
        return g.modPow(m, nsquare).multiply(r.modPow(n, nsquare)).mod(nsquare);
    }

    // Cifrado de un número con otra clave pública
    public BigInteger Encryption(BigInteger m, BigInteger r, BigInteger n) {
        return g.modPow(m, n.multiply(n)).multiply(r.modPow(n, n.multiply(n))).mod(n.multiply(n));
    }

    // Descifrado de un número
    public BigInteger Decryption(BigInteger c) {
        BigInteger u = g.modPow(lambda, nsquare).subtract(BigInteger.ONE).divide(n).modInverse(n);
        return c.modPow(lambda, nsquare).subtract(BigInteger.ONE).divide(n).multiply(u).mod(n);
    }

    // Serializar clave pública
    public HashMap<String, String> serializePublicKey() {
        HashMap<String, String> publicKeyDict = new HashMap<>();
        publicKeyDict.put("n", n.toString());
        return publicKeyDict;
    }

    // Reconstruir clave pública
    public void reconstructPublicKey(HashMap<String, String> publicKeyDict) {
        n = new BigInteger(Objects.requireNonNull(publicKeyDict.get("n")));
        nsquare = n.multiply(n);
    }

    public HashMap<String, BigInteger> getEncryptedSet(HashMap<String, BigInteger> serializedEncryptedSet) {
        HashMap<String, BigInteger> encryptedSet = new HashMap<>();
        for (Map.Entry<String, BigInteger> entry : serializedEncryptedSet.entrySet()) {
            encryptedSet.put(entry.getKey(), new BigInteger(entry.getValue().toString()));
        }
        return encryptedSet;
    }

    public HashMap<String, BigInteger> encryptMyData(Set<Integer> mySet, int domain) {
        HashMap<String, BigInteger> result = new HashMap<>();
        for (int element = 0; element < domain; element++) {
            if (!mySet.contains(element)) {
                result.put(Integer.toString(element), Encryption(BigInteger.ZERO, BigInteger.ONE));
            } else {
                result.put(Integer.toString(element), Encryption(BigInteger.ONE, BigInteger.ONE));
            }
        }
        return result;
    }

    public HashMap<String, BigInteger> recvMultipliedSet(HashMap<String, BigInteger> serializedMultipliedSet) {
        return getEncryptedSet(serializedMultipliedSet);
    }

    // Sacar el conjunto multiplicado
    public HashMap<String, BigInteger> getMultipliedSet(HashMap<String, BigInteger> encSet, Set<Integer> nodeSet) {
        HashMap<String, BigInteger> result = new HashMap<>();
        for (Map.Entry<String, BigInteger> entry : encSet.entrySet()) {
            int element = Integer.parseInt(entry.getKey());
            if (!nodeSet.contains(element)) {
                // Multiplicamos por el 0 siguiendo la propiedad de homomorfismo de Paillier
                result.put(entry.getKey(), entry.getValue().modPow(BigInteger.ZERO, nsquare));
            } else {
                result.put(entry.getKey(), entry.getValue().modPow(BigInteger.ONE, nsquare));
            }
        }
        return result;
    }
    /*En el sistema criptográfico de Paillier, la multiplicación de un número cifrado por un número
    sin cifrar se realiza mediante la exponenciación, no mediante la multiplicación ordinaria.
    Esto es lo que permite que el sistema mantenga su propiedad de homomorfismo. Esto lo tengo que probar
    porque en la phe venía directamente sobrecargado, aquí hay que lidiar con ello.*/
}


    /*public static void main(String[] args) {
        // Generación de claves
        Paillier paillier = new Paillier(1024, 64);
        BigInteger publicKey = paillier.n;

        // Cifrar los elementos de A
        Map<Integer, BigInteger> encryptedSet = encryptMyData(new int[]{1, 2, 3}, publicKey, 10);

        // Recibir el set
        Map<Integer, BigInteger> multipliedSet = recvMultipliedSet(encryptedSet, new int[]{2, 3, 4});
    }*/
