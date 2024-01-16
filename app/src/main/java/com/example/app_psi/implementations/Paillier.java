package com.example.app_psi.implementations;

import com.google.gson.internal.LinkedTreeMap;

import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;
import java.security.SecureRandom;
import java.util.Set;

public class Paillier {
    public BigInteger p, q, lambda; // Variables para almacenar los números primos y lambda
    public BigInteger n; // Clave pública
    public BigInteger nsquare; // n al cuadrado, se usa en el cifrado y descifrado
    private BigInteger g; // Número que se usa en el cifrado y descifrado
    private int bitLength; // Longitud de los números primos

    // Constructor que genera las claves
    public Paillier(int bitLengthVal, int certainty) {
        KeyGeneration(bitLengthVal, certainty);
    }

    // Constructor para cifrar lo que mande un nodo
    public Paillier(BigInteger n) {
        this.n = n;
        this.nsquare = n.multiply(n);
        this.g = n.add(BigInteger.ONE);  // g puede ser n+1
    }

    // Método para generar las claves, certeza es el grado de certeza con el que queremos que los números generados sean primos
    // A más certeza, más tiempo de computación en generar las claves
    public void KeyGeneration(int bitLengthVal, int certainty) {
        bitLength = bitLengthVal;
        SecureRandom r = new SecureRandom();
        p = new BigInteger(bitLength / 2, certainty, r); // Genera un número primo p
        q = new BigInteger(bitLength / 2, certainty, r); // Genera un número primo q
        // Asegurar que p y q son diferentes, sino, n sería primo y se rompería la seguridad del esquema
        while (q.compareTo(p) == 0) {
            q = new BigInteger(bitLength / 2, certainty, r);
        }
        n = p.multiply(q); // Calcula n = p * q, que se usa como la clave pública
        nsquare = n.multiply(n); // Calcula n al cuadrado
        // Cogemos g como n + 1 para asegurar que g es un número aleatorio en [1, n^2] y para optimizar el cálculo del cifrado y descifrado
        g = n.add(BigInteger.ONE);
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
    public BigInteger Encryption(BigInteger m) {
        SecureRandom r = new SecureRandom();
        BigInteger randNum;
        // Asegurar que randNum es coprimo con n y menor que n
        do {
            randNum = new BigInteger(n.bitLength(), r);
        } while (randNum.compareTo(n) >= 0 || !randNum.gcd(n).equals(BigInteger.ONE));
        return g.modPow(m, nsquare).multiply(randNum.modPow(n, nsquare)).mod(nsquare);
    }

    // Descifrado de un número
    public BigInteger Decryption(BigInteger c) {
        BigInteger a = g.modPow(lambda, nsquare).subtract(BigInteger.ONE).divide(n);
        if (!a.gcd(n).equals(BigInteger.ONE)) {
            throw new ArithmeticException("BigInteger not invertible. COMPROBAR n y lambda!!");
        } else {
            BigInteger u = a.modInverse(n);
            return c.modPow(lambda, nsquare).subtract(BigInteger.ONE).divide(n).multiply(u).mod(n);
        }
    }

    public BigInteger homomorphicMultiply(BigInteger cipherText, BigInteger multiplier, BigInteger nSquared) {
        return cipherText.modPow(multiplier, nSquared);
    }


    // Serializar clave pública
    public LinkedTreeMap<String, String> serializePublicKey() {
        LinkedTreeMap<String, String> publicKeyDict = new LinkedTreeMap<>();
        publicKeyDict.put("n", n.toString());
        return publicKeyDict;
    }

    // Reconstruir clave pública
    public BigInteger reconstructPublicKey(LinkedTreeMap<String, String> publicKeyDict) {
        return new BigInteger(Objects.requireNonNull(publicKeyDict.get("n")));
    }

    public LinkedTreeMap<String, BigInteger> getEncryptedSet(LinkedTreeMap<String, String> serializedEncryptedSet) {
        LinkedTreeMap<String, BigInteger> encryptedSet = new LinkedTreeMap<>();
        for (Map.Entry<String, String> entry : serializedEncryptedSet.entrySet()) {
            encryptedSet.put(entry.getKey(), new BigInteger(entry.getValue()));
        }
        return encryptedSet;
    }

    public LinkedTreeMap<String, BigInteger> encryptMyData(Set<Integer> mySet, int domain) {
        LinkedTreeMap<String, BigInteger> result = new LinkedTreeMap<>();
        for (int element = 0; element < domain; element++) {
            if (!mySet.contains(element)) {
                result.put(Integer.toString(element), Encryption(BigInteger.ZERO));
            } else {
                result.put(Integer.toString(element), Encryption(BigInteger.ONE));
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
        Paillier paillierSender = new Paillier(n);
        for (Map.Entry<String, BigInteger> entry : encSet.entrySet()) {
            int element = Integer.parseInt(entry.getKey());
            if (!nodeSet.contains(element)) {
                BigInteger encryptedZero = paillierSender.Encryption(BigInteger.ZERO);
                result.put(entry.getKey(), encryptedZero);
                // Sale 1... result.put(entry.getKey(), paillierSender.homomorphicMultiply(entry.getValue(), BigInteger.ZERO, paillierSender.nsquare));
            } else {
                result.put(entry.getKey(), paillierSender.homomorphicMultiply(entry.getValue(), BigInteger.ONE, paillierSender.nsquare));
            }
        }
        return result;
    /*En el sistema criptográfico de Paillier, la multiplicación de un número cifrado por un número
    sin cifrar se realiza mediante la exponenciación, no mediante la multiplicación ordinaria.
    Esto es lo que permite que el sistema mantenga su propiedad de homomorfismo. Esto lo tengo que probar
    porque en la phe venía directamente sobrecargado, aquí hay que lidiar con ello.*/
    }
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
