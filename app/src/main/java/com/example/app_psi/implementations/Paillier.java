package com.example.app_psi.implementations;

import static com.example.app_psi.implementations.Polynomials.hornerEvalCrypt;

import androidx.annotation.NonNull;

import com.google.gson.internal.LinkedTreeMap;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.security.SecureRandom;
import java.util.Set;

public class Paillier implements CryptoSystem {
    private BigInteger p, q, lambda; // Variables para almacenar los números primos y lambda
    private BigInteger n; // Clave pública
    private BigInteger nsquare; // n al cuadrado, se usa en el cifrado y descifrado
    private BigInteger g; // Número que se usa en el cifrado y descifrado

    // Constructor que genera las claves
    public Paillier(int bitLengthVal) {
        keyGeneration(bitLengthVal);
    }

    // Constructor para cifrar lo que mande un nodo
    public Paillier(@NonNull BigInteger n) {
        this.n = n;
        this.nsquare = n.multiply(n);
        this.g = n.add(BigInteger.ONE);  // g puede ser n+1
    }

    // Método para generar las claves, certeza es el grado de certeza con el que queremos que los números generados sean primos
    // A más certeza, más tiempo de computación en generar las claves
    public void keyGeneration(int bitLengthVal) {
        int certainty = 64;
        // Longitud de los números primos
        SecureRandom r = new SecureRandom();
        p = new BigInteger(bitLengthVal / 2, certainty, r); // Genera un número primo p
        q = new BigInteger(bitLengthVal / 2, certainty, r); // Genera un número primo q
        // Asegurar que p y q son diferentes, sino, n sería primo y se rompería la seguridad del esquema
        while (q.compareTo(p) == 0) {
            q = new BigInteger(bitLengthVal / 2, certainty, r);
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
    public BigInteger Encrypt(BigInteger m) {
        SecureRandom r = new SecureRandom();
        BigInteger randNum;
        // Asegurar que randNum es coprimo con n y menor que n
        do {
            randNum = new BigInteger(n.bitLength(), r);
        } while (randNum.compareTo(n) >= 0 || !randNum.gcd(n).equals(BigInteger.ONE));
        return g.modPow(m, nsquare).multiply(randNum.modPow(n, nsquare)).mod(nsquare);
    }

    // Descifrado de un número
    public BigInteger Decrypt(BigInteger c) {
        BigInteger a = g.modPow(lambda, nsquare).subtract(BigInteger.ONE).divide(n);
        if (!a.gcd(n).equals(BigInteger.ONE)) {
            throw new ArithmeticException("BigInteger not invertible. COMPROBAR n y lambda!!");
        } else {
            BigInteger u = a.modInverse(n);
            return c.modPow(lambda, nsquare).subtract(BigInteger.ONE).divide(n).multiply(u).mod(n);
        }
    }

    // Suma 2 números cifrados
    public BigInteger addEncryptedNumbers(@NonNull BigInteger a, BigInteger b) {
        return a.multiply(b).mod(nsquare);
    }

    // Suma número cifrado y escalar
    public BigInteger addEncryptedAndScalar(@NonNull BigInteger a, BigInteger b) {
        return a.multiply(g.modPow(b, nsquare)).mod(nsquare);
    }

    // Multiplica número cifrado por escalar
    public BigInteger multiplyEncryptedByScalar(@NonNull BigInteger a, BigInteger b) {
        return a.modPow(b, nsquare);
    }


    // Serializar clave pública
    public LinkedTreeMap<String, String> serializePublicKey() {
        LinkedTreeMap<String, String> publicKeyDict = new LinkedTreeMap<>();
        publicKeyDict.put("n", n.toString());
        return publicKeyDict;
    }

    // Reconstruir clave pública
    public LinkedTreeMap<String, BigInteger>  reconstructPublicKey(@NonNull LinkedTreeMap<String, String> publicKeyDict) {
        LinkedTreeMap<String, BigInteger> publicKey = new LinkedTreeMap<>();
        publicKey.put("n", new BigInteger(Objects.requireNonNull(publicKeyDict.get("n"))));
        return publicKey;
    }

    public LinkedTreeMap<String, BigInteger> getEncryptedSet(@NonNull LinkedTreeMap<String, String> serializedEncryptedSet) {
        LinkedTreeMap<String, BigInteger> encryptedSet = new LinkedTreeMap<>();
        for (Map.Entry<String, String> entry : serializedEncryptedSet.entrySet()) {
            encryptedSet.put(entry.getKey(), new BigInteger(entry.getValue()));
        }
        return encryptedSet;
    }

    public ArrayList<BigInteger> encryptRoots(@NonNull List<BigInteger> mySet) {
        ArrayList<BigInteger> result = new ArrayList<>();
        for (BigInteger element : mySet) {
            result.add(Encrypt(element));
        }
        return result;
    }

    public ArrayList<BigInteger> encryptMySet(@NonNull Set<Integer> mySet) {
        ArrayList<BigInteger> result = new ArrayList<>();
        for (int element : mySet) {
            result.add(Encrypt(BigInteger.valueOf(element)));
        }
        return result;
    }

    public LinkedTreeMap<String, BigInteger> recvMultipliedSet(LinkedTreeMap<String, String> serializedMultipliedSet) {
        return getEncryptedSet(serializedMultipliedSet);
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
                // Sale 1... result.put(entry.getKey(), paillierSender.homomorphicMultiply(entry.getValue(), BigInteger.ZERO, paillierSender.nsquare));
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

    public BigInteger getN() {
        return n;
    }
}