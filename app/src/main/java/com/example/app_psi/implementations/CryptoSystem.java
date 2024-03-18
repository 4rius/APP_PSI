package com.example.app_psi.implementations;

import androidx.annotation.NonNull;

import com.google.gson.internal.LinkedTreeMap;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public interface CryptoSystem {
    BigInteger multiplyEncryptedByScalar(BigInteger encryptedNumber, BigInteger scalar);
    BigInteger addEncryptedNumbers(BigInteger encryptedNumber1, BigInteger encryptedNumber2);
    BigInteger addEncryptedAndScalar(@NonNull BigInteger a, BigInteger b);

    BigInteger Decrypt(BigInteger encryptedNumber);
    BigInteger Encrypt(BigInteger number);

    void keyGeneration(int bitLength);
}
