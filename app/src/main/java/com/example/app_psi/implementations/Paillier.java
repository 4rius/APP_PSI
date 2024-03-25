package com.example.app_psi.implementations;

import android.util.Log;

import androidx.annotation.NonNull;

import java.math.BigInteger;
import java.security.SecureRandom;

public class Paillier implements CryptoSystem {
    private BigInteger lambda; // Variables para almacenar los números primos y lambda
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
        SecureRandom random = new SecureRandom();
        BigInteger p, q;
        do {
            p = new BigInteger(bitLengthVal / 2, certainty, random);  // Genera un número primo p
            q = new BigInteger(bitLengthVal / 2, certainty, random);  // Genera un número primo q
        } while (p.equals(q));  // Asegura que p y q son diferentes, sino, n sería primo y se rompería la seguridad del esquema


        n = p.multiply(q); // Calcula n = p * q, que se usa como la clave pública
        nsquare = n.multiply(n); // Calcula n al cuadrado
        // Cogemos g como n + 1 para asegurar que g es un número aleatorio en [1, n^2] y para optimizar el cálculo del cifrado y descifrado
        g = n.add(BigInteger.ONE);
        lambda = p.subtract(BigInteger.ONE).multiply(q.subtract(BigInteger.ONE)).divide(
                p.subtract(BigInteger.ONE).gcd(q.subtract(BigInteger.ONE))); // Calcula lambda

        // n tiene que poder dividir el orden de g
        while (isValidGenerator(g)) {
            Log.d("Paillier", "g is not good enough. Trying again...");
            g = g.add(BigInteger.ONE);
        }
    }

    private boolean isValidGenerator(BigInteger g) {
        return !g.modPow(lambda, nsquare).subtract(BigInteger.ONE).divide(n).gcd(n).equals(BigInteger.ONE);
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

    public BigInteger getN() {
        return n;
    }
}