package com.example.app_psi.implementationstests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.app_psi.implementations.DamgardJurik;
import com.example.app_psi.implementations.Polynomials;
import com.google.gson.internal.LinkedTreeMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @Test
    public void intersectionDomainReturnsCorrectIntersection() {
        Set<Integer> aliceSet = new HashSet<>(Arrays.asList(1, 2, 3, 4, 5, 6));
        Set<Integer> bobSet = new HashSet<>(Arrays.asList(3, 4, 5, 6, 7, 8));
        LinkedTreeMap<String, BigInteger> aliceEncryptedSet = damgardJurik.encryptMyData(aliceSet, 10);

        // Bob receives and multiplies by 0 or 1
        LinkedTreeMap<String, BigInteger> multEncSet = damgardJurik.getMultipliedSet(aliceEncryptedSet, bobSet, damgardJurik.getN());

        // Alice decrypts the intersection, 1 if the element is in the intersection, 0 otherwise
        for (Map.Entry<String, BigInteger> entry : multEncSet.entrySet()) {
            BigInteger decryptedValue = damgardJurik.Decrypt(entry.getValue());
            entry.setValue(decryptedValue);
        }
        // Cogemos solo los valores que sean 1, que representan la intersección
        List<Integer> intersection = new ArrayList<>();
        for (Map.Entry<String, BigInteger> entry : multEncSet.entrySet()) {
            if (entry.getValue().equals(BigInteger.ONE)) {
                intersection.add(Integer.parseInt(entry.getKey()));
            }
        }

        assertEquals(intersection, Arrays.asList(3, 4, 5, 6));
    }

    @Test
    public void intersectionOPEReturnsCorrectIntersection() {
        List<Integer> aliceSet = Arrays.asList(1, 2, 3, 4, 5, 6);
        List<Integer> bobSet = Arrays.asList(3, 4, 5, 6, 7, 8);
        List<BigInteger> aliceRoots = Polynomials.polyFromRoots(aliceSet, BigInteger.valueOf(-1), BigInteger.ONE);
        ArrayList<BigInteger> aliceEncRoots = damgardJurik.encryptRoots(aliceRoots);

        // Bob receives and evaluates
        ArrayList<BigInteger> bobEval = new ArrayList<>();
        bobEval = damgardJurik.handleOPESecondStep(aliceEncRoots, bobSet, damgardJurik.getN());

        // Alice decrypts and figures out the intersection
        ArrayList<BigInteger> decEval = new ArrayList<>();
        for (BigInteger eval : bobEval) {
            decEval.add(damgardJurik.Decrypt(eval));
        }
        List<Integer> intersection = new ArrayList<>();
        for (BigInteger element : decEval) {
            if (aliceSet.contains(element.intValue())) {
                intersection.add(element.intValue());
            }
        }

        assertEquals(intersection, Arrays.asList(3, 4, 5, 6));
    }
}