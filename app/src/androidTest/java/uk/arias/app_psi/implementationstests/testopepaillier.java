package uk.arias.app_psi.implementationstests;


/*
  Paillier PSI usando Evaluación polinómica PoC
 */

import uk.arias.app_psi.implementations.Paillier;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class testopepaillier {
    @Test
    public void testope() {
        // === Alice's Setup ===
        Paillier paillier = new Paillier(128);
        List<Integer> aliceSet = Arrays.asList(1, 2, 3, 4, 5, 7, 8);

        // Generamos un polinomio que tenga como raíces los elementos de aliceSet
        List<BigInteger> coefficients = polyFromRoots(aliceSet, BigInteger.valueOf(-1), BigInteger.ONE);

        // Imprimir los coeficientes
        System.out.println("Coeficientes: " + coefficients);

        // Ciframos los coeficientes y se los "mandamos" a Bob
        List<BigInteger> encryptedCoeff = new ArrayList<>();
        for (BigInteger coef : coefficients) {
            encryptedCoeff.add(paillier.Encrypt(coef));
        }

        // === Bob's Setup ===
        List<Integer> bobSet = Arrays.asList(2, 3, 6, 8, 1, 9);
        List<BigInteger> encryptedResult = new ArrayList<>();
        Random rand = new Random();
        for (Integer element : bobSet) {
            BigInteger rb = BigInteger.valueOf(rand.nextInt(1000) + 1);
            BigInteger Epbj = hornerEvalCrypt(encryptedCoeff, BigInteger.valueOf(element), paillier);
            System.out.println("Epbj: " + Epbj);
            System.out.println("Decrypted Epbj: " + paillier.Decrypt(Epbj));
            BigInteger result = paillier.Encrypt(BigInteger.valueOf(element));
            BigInteger mult = paillier.multiplyEncryptedByScalar(Epbj, rb);
            result = paillier.addEncryptedNumbers(result, mult);
            encryptedResult.add(result);
        }

        // Resultados
        List<BigInteger> result = new ArrayList<>();
        for (BigInteger res : encryptedResult) {
            result.add(paillier.Decrypt(res));
        }
        System.out.println("Resultado: " + result);

        List<BigInteger> intersection = new ArrayList<>();
        for (BigInteger element : result) {
            if (aliceSet.contains(element.intValue())) {
                intersection.add(element);
            }
        }
        System.out.println("Intersección: " + intersection);
        // Intersection should equal [2, 3, 8, 1]
        assertEquals(intersection, Arrays.asList(BigInteger.valueOf(2), BigInteger.valueOf(3), BigInteger.valueOf(8), BigInteger.valueOf(1)));

    }

    public static List<BigInteger> polyFromRoots(List<Integer> roots, BigInteger negOne, BigInteger one) {
        BigInteger zero = one.add(negOne);
        List<BigInteger> coefs = Arrays.asList(negOne.multiply(BigInteger.valueOf(roots.get(0))), one);
        for (int i = 1; i < roots.size(); i++) {
            coefs = polyMul(coefs, Arrays.asList(negOne.multiply(BigInteger.valueOf(roots.get(i))), one), zero);
        }
        return coefs;
    }

    public static List<BigInteger> polyMul(List<BigInteger> coefs1, List<BigInteger> coefs2, BigInteger zero) {
        // Inicializar coefs3 con ceros, nCopies inicializa la lista más eficientemente que add
        List<BigInteger> coefs3 = new ArrayList<>(Collections.nCopies(coefs1.size() + coefs2.size() - 1, zero));
        for (int i = 0; i < coefs1.size(); i++) {
            for (int j = 0; j < coefs2.size(); j++) {
                coefs3.set(i + j, coefs3.get(i + j).add(coefs1.get(i).multiply(coefs2.get(j))));
            }
        }
        return coefs3;
    }


    public static BigInteger hornerEvalCrypt(List<BigInteger> coefs, BigInteger x, Paillier paillier) {
        BigInteger result = coefs.get(coefs.size() - 1);
        for (int i = coefs.size() - 2; i >= 0; i--) {
            result = paillier.multiplyEncryptedByScalar(result, x);
            result = paillier.addEncryptedNumbers(result, coefs.get(i));
        }
        return result;
    }
}
