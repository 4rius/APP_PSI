package com.example.app_psi.implementationstests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.app_psi.implementations.DamgardJurik;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class DamgardJurikTest {

    private DamgardJurik damgardJurik;

    @Before
    public void setup() {
        damgardJurik = new DamgardJurik(128, 2);
    }

    @Test
    public void encryptionAndDecryptionReturnsOriginalNumber() {
        BigInteger originalNumber = BigInteger.valueOf(123456789);
        BigInteger encryptedNumber = damgardJurik.Encrypt(originalNumber);
        BigInteger decryptedNumber = damgardJurik.Decrypt(encryptedNumber);

        assertEquals(originalNumber, decryptedNumber);
    }

    @Test
    public void addEncryptedNumbersReturnsCorrectSum() {
        BigInteger number1 = BigInteger.valueOf(123);
        BigInteger number2 = BigInteger.valueOf(456);

        BigInteger encryptedNumber1 = damgardJurik.Encrypt(number1);
        BigInteger encryptedNumber2 = damgardJurik.Encrypt(number2);

        BigInteger encryptedSum = damgardJurik.addEncryptedNumbers(encryptedNumber1, encryptedNumber2);
        BigInteger decryptedSum = damgardJurik.Decrypt(encryptedSum);

        assertEquals(number1.add(number2), decryptedSum);
    }

    @Test
    public void multiplyEncryptedByScalarReturnsCorrectProduct() {
        BigInteger number = BigInteger.valueOf(123);
        BigInteger scalar = BigInteger.valueOf(456);

        BigInteger encryptedNumber = damgardJurik.Encrypt(number);
        BigInteger encryptedProduct = damgardJurik.multiplyEncryptedByScalar(encryptedNumber, scalar);
        BigInteger decryptedProduct = damgardJurik.Decrypt(encryptedProduct);

        assertEquals(number.multiply(scalar), decryptedProduct);
    }

    @Test
    public void addEncryptedAndScalarReturnsCorrectSum() {
        BigInteger number = BigInteger.valueOf(123);
        BigInteger scalar = BigInteger.valueOf(456);

        BigInteger encryptedNumber = damgardJurik.Encrypt(number);
        BigInteger encryptedSum = damgardJurik.addEncryptedAndScalar(encryptedNumber, scalar);
        BigInteger decryptedSum = damgardJurik.Decrypt(encryptedSum);

        assertEquals(number.add(scalar), decryptedSum);
    }

    @Test
    public void encryptionAndDecryptionWithDifferentNumbers() {
        BigInteger originalNumber1 = BigInteger.valueOf(123456789);
        BigInteger originalNumber2 = BigInteger.valueOf(987654321);

        BigInteger encryptedNumber1 = damgardJurik.Encrypt(originalNumber1);
        BigInteger encryptedNumber2 = damgardJurik.Encrypt(originalNumber2);

        BigInteger decryptedNumber1 = damgardJurik.Decrypt(encryptedNumber1);
        BigInteger decryptedNumber2 = damgardJurik.Decrypt(encryptedNumber2);

        assertNotEquals(decryptedNumber1, decryptedNumber2);
    }

    @Test
    public void encryptRootsReturnsCorrectlyEncryptedRoots() {
        List<BigInteger> roots = Arrays.asList(BigInteger.valueOf(1), BigInteger.valueOf(2), BigInteger.valueOf(3));
        List<BigInteger> encryptedRoots = damgardJurik.encryptRoots(roots);

        for (int i = 0; i < roots.size(); i++) {
            BigInteger decryptedRoot = damgardJurik.Decrypt(encryptedRoots.get(i));
            assertEquals(roots.get(i), decryptedRoot);
        }
    }
}