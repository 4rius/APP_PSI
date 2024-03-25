package com.example.app_psi.helpers;

import com.example.app_psi.implementations.CryptoSystem;

import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Polynomials {

    // Método para generar un polinomio a partir de sus raíces
    public static List<BigInteger> polyFromRoots(@NotNull List<Integer> roots, BigInteger negOne, @NotNull BigInteger one) {
        BigInteger zero = one.add(negOne);
        List<BigInteger> coefs = Arrays.asList(negOne.multiply(BigInteger.valueOf(roots.get(0))), one);
        for (int i = 1; i < roots.size(); i++) {
            coefs = polyMul(coefs, Arrays.asList(negOne.multiply(BigInteger.valueOf(roots.get(i))), one), zero);
        }
        return coefs;
    }

    // Método para multiplicar dos polinomios
    public static @NotNull List<BigInteger> polyMul(@NotNull List<BigInteger> coefs1, @NotNull List<BigInteger> coefs2, BigInteger zero) {
        // Inicializar coefs3 con ceros, nCopies inicializa la lista más eficientemente que add
        List<BigInteger> coefs3 = new ArrayList<>(Collections.nCopies(coefs1.size() + coefs2.size() - 1, zero));
        for (int i = 0; i < coefs1.size(); i++) {
            for (int j = 0; j < coefs2.size(); j++) {
                coefs3.set(i + j, coefs3.get(i + j).add(coefs1.get(i).multiply(coefs2.get(j))));
            }
        }
        return coefs3;
    }

    // Método para evaluar un polinomio cifrado
    public static BigInteger hornerEvalCrypt(@NotNull List<BigInteger> coefs, BigInteger x, CryptoSystem cs) {
        BigInteger result = coefs.get(coefs.size() - 1);
        for (int i = coefs.size() - 2; i >= 0; i--) {
            result = cs.multiplyEncryptedByScalar(result, x);
            result = cs.addEncryptedNumbers(result, coefs.get(i));
        }
        return result;
    }

    private Polynomials() {}
}
