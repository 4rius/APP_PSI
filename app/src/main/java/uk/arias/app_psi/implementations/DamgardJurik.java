package uk.arias.app_psi.implementations;

import android.util.Log;

import androidx.annotation.NonNull;

import java.math.BigInteger;
import java.security.SecureRandom;

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
        int certainty = 64;
        SecureRandom random = new SecureRandom();
        BigInteger p, q;
        do {
            p = new BigInteger(bitLength / 2, certainty, random);
            q = new BigInteger(bitLength / 2, certainty, random);
        } while (p.equals(q));

        n = p.multiply(q);
        ns = n.pow(s);  // n^s
        nPowSPlusOne = n.pow(s + 1);  // n^s+1
        lambda = p.subtract(BigInteger.ONE).multiply(q.subtract(BigInteger.ONE)).divide(p.subtract(BigInteger.ONE).gcd(q.subtract(BigInteger.ONE)));

        g = n.add(BigInteger.ONE);
        while (isValidGenerator(g)) {
            Log.d("DamgardJurik", "g is not good enough. Trying again...");
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

    public int getS() {
        return s;
    }

    // Suma homomórfica de dos textos cifrados
    public BigInteger addEncryptedNumbers(@NonNull BigInteger ciphertext1, BigInteger ciphertext2) {
        return ciphertext1.multiply(ciphertext2).mod(nPowSPlusOne);
    }

    // Suma homomórfica de un texto cifrado y un escalar
    public BigInteger addEncryptedAndScalar(@NonNull BigInteger ciphertext, BigInteger scalar) {
        return ciphertext.multiply(g.modPow(scalar, nPowSPlusOne)).mod(nPowSPlusOne);
    }

    // Multiplicación homomórfica de un texto cifrado por un escalar
    public BigInteger multiplyEncryptedByScalar(@NonNull BigInteger ciphertext, BigInteger scalar) {
        return ciphertext.modPow(scalar, nPowSPlusOne);
    }

}
