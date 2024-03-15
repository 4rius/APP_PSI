package com.example.app_psi.implementationstests;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.app_psi.handlers.SchemeHandler;
import com.example.app_psi.implementations.CryptoSystem;
import com.example.app_psi.implementations.Paillier;
import com.example.app_psi.objects.Device;
import com.google.gson.internal.LinkedTreeMap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class SchemeHandlerTest {

    @Mock
    private Device device;

    private final CryptoSystem cryptoSystem = new Paillier(128);


    private final SchemeHandler schemeHandler = new SchemeHandler();

    @Test
    public void intersectionFirstStepReturnsExpectedMessage() {
        String id = "testId";
        Set<Integer> myData = new HashSet<>();
        String peerId = "peerId";
        int domain = 1;

        String expectedMessage = "Intersection with " + peerId + " - " + cryptoSystem.getClass().getSimpleName() + " - Waiting for response...";

        String actualMessage = schemeHandler.intersectionFirstStep(device, cryptoSystem, id, myData, peerId, domain);

        assertEquals(expectedMessage, actualMessage);
    }

    @Test
    public void OPEIntersectionFirstStepReturnsExpectedMessage() {
        String id = "testId";
        Set<Integer> myData = new HashSet<>();
        String peerId = "peerId";
        String type = "PSI";

        String expectedMessage = "Intersection with " + device + " - " + cryptoSystem.getClass().getSimpleName() + " " + type + " OPE - Waiting for response...";

        String actualMessage = schemeHandler.OPEIntersectionFirstStep(device, cryptoSystem, id, myData, peerId, type);

        assertEquals(expectedMessage, actualMessage);
    }

    @Test
    public void intersectionSecondStepHandlesDataCorrectly() {
        String peer = "peer";
        LinkedTreeMap<String, String> peerPubKey = cryptoSystem.serializePublicKey();
        LinkedTreeMap<String, String> data = new LinkedTreeMap<>();
        String id = "testId";
        Set<Integer> myData = new HashSet<>();

        LinkedTreeMap<String, BigInteger> rec_pk = cryptoSystem.reconstructPublicKey(peerPubKey);
        assertEquals(rec_pk.get("n"), ((Paillier) cryptoSystem).getN());

        schemeHandler.intersectionSecondStep(device, peer, peerPubKey, data, cryptoSystem, id, myData);

        // No assertion here, we're just verifying that no exceptions are thrown
    }

    @Test
    public void OPEIntersectionSecondStepHandlesDataCorrectly() {
        String peer = "peer";
        LinkedTreeMap<String, String> peerPubKey = cryptoSystem.serializePublicKey();
        ArrayList<String> data = new ArrayList<>();
        String id = "testId";
        Set<Integer> myData = new HashSet<>();

        LinkedTreeMap<String, BigInteger> rec_pk = cryptoSystem.reconstructPublicKey(peerPubKey);
        assertEquals(rec_pk.get("n"), ((Paillier) cryptoSystem).getN());

        schemeHandler.OPEIntersectionSecondStep(device, peer, peerPubKey, data, cryptoSystem, id, myData);

        // No assertion here, we're just verifying that no exceptions are thrown
    }

    @Test
    public void intersectionFirstStepHandlesIntersectionCorrectly() {
        String id = "testId";
        Set<Integer> myData = new HashSet<>();
        myData.add(1);
        myData.add(2);
        myData.add(3);
        String peerId = "peerId";
        int domain = 1;

        String expectedMessage = "Intersection with " + peerId + " - " + cryptoSystem.getClass().getSimpleName() + " - Waiting for response...";

        String actualMessage = schemeHandler.intersectionFirstStep(device, cryptoSystem, id, myData, peerId, domain);

        assertEquals(expectedMessage, actualMessage);
    }
}