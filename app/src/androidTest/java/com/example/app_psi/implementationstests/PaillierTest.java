package com.example.app_psi.implementationstests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.app_psi.implementations.Paillier;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class PaillierTest {

    private Paillier paillier;

    @Before
    public void setup() {
        paillier = new Paillier(128);
    }

    @Test
    public void encryptionAndDecryptionReturnsOriginalNumber() {
        BigInteger originalNumber = BigInteger.valueOf(123456789);
        BigInteger encryptedNumber = paillier.Encrypt(originalNumber);
        BigInteger decryptedNumber = paillier.Decrypt(encryptedNumber);

        assertEquals(originalNumber, decryptedNumber);
    }

    @Test
    public void addEncryptedNumbersReturnsCorrectSum() {
        BigInteger number1 = BigInteger.valueOf(123);
        BigInteger number2 = BigInteger.valueOf(456);

        BigInteger encryptedNumber1 = paillier.Encrypt(number1);
        BigInteger encryptedNumber2 = paillier.Encrypt(number2);

        BigInteger encryptedSum = paillier.addEncryptedNumbers(encryptedNumber1, encryptedNumber2);
        BigInteger decryptedSum = paillier.Decrypt(encryptedSum);

        assertEquals(number1.add(number2), decryptedSum);
    }

    @Test
    public void multiplyEncryptedByScalarReturnsCorrectProduct() {
        BigInteger number = BigInteger.valueOf(123);
        BigInteger scalar = BigInteger.valueOf(456);

        BigInteger encryptedNumber = paillier.Encrypt(number);
        BigInteger encryptedProduct = paillier.multiplyEncryptedByScalar(encryptedNumber, scalar);
        BigInteger decryptedProduct = paillier.Decrypt(encryptedProduct);

        assertEquals(number.multiply(scalar), decryptedProduct);
    }

    @Test
    public void addEncryptedAndScalarReturnsCorrectSum() {
        BigInteger number = BigInteger.valueOf(123);
        BigInteger scalar = BigInteger.valueOf(456);

        BigInteger encryptedNumber = paillier.Encrypt(number);
        BigInteger encryptedSum = paillier.addEncryptedAndScalar(encryptedNumber, scalar);
        BigInteger decryptedSum = paillier.Decrypt(encryptedSum);

        assertEquals(number.add(scalar), decryptedSum);
    }

    @Test
    public void encryptionAndDecryptionWithDifferentNumbers() {
        BigInteger originalNumber1 = BigInteger.valueOf(123456789);
        BigInteger originalNumber2 = BigInteger.valueOf(987654321);

        BigInteger encryptedNumber1 = paillier.Encrypt(originalNumber1);
        BigInteger encryptedNumber2 = paillier.Encrypt(originalNumber2);

        BigInteger decryptedNumber1 = paillier.Decrypt(encryptedNumber1);
        BigInteger decryptedNumber2 = paillier.Decrypt(encryptedNumber2);

        assertNotEquals(decryptedNumber1, decryptedNumber2);
    }

    @Test
    public void encryptRootsReturnsCorrectlyEncryptedRoots() {
        List<BigInteger> roots = Arrays.asList(BigInteger.valueOf(1), BigInteger.valueOf(2), BigInteger.valueOf(3));
        List<BigInteger> encryptedRoots = paillier.encryptRoots(roots);

        for (int i = 0; i < roots.size(); i++) {
            BigInteger decryptedRoot = paillier.Decrypt(encryptedRoots.get(i));
            assertEquals(roots.get(i), decryptedRoot);
        }
    }
}
