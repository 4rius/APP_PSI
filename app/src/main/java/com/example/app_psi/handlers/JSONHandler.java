package com.example.app_psi.handlers;

import com.example.app_psi.helpers.CSHelper;
import com.example.app_psi.collections.CryptoImplementation;
import com.example.app_psi.helpers.DamgardJurikHelper;
import com.example.app_psi.helpers.PaillierHelper;
import com.example.app_psi.implementations.CryptoSystem;
import com.example.app_psi.objects.Device;
import com.example.app_psi.objects.Node;
import com.example.app_psi.proxies.ActivityLogger;
import com.example.app_psi.proxies.LogActivityProxy;
import com.example.app_psi.proxies.RealActivityLogger;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;

import static com.example.app_psi.collections.DbConstants.*;

import android.os.Debug;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Nullable;

public class JSONHandler {

    private final Map<CryptoImplementation, CSHelper> CSHelpers = new HashMap<>();

    private final OPEHandler OPEHandler = new OPEHandler();

    private final DomainPSIHandler domainPSIHandler = new DomainPSIHandler();

    private final OPECAHandler OPECAHandler = new OPECAHandler();

    private final ThreadPoolExecutor executor; // Executor para lanzar hilos

    public JSONHandler() {
        CSHelpers.put(CryptoImplementation.PAILLIER, new PaillierHelper(DFL_BIT_LENGTH));
        CSHelpers.put(CryptoImplementation.DAMGARD_JURIK, new DamgardJurikHelper(DFL_BIT_LENGTH, DFL_EXPANSION_FACTOR));
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
    }

    private void runInBackground(Runnable task) {
        executor.execute(task);
    }

    public String startIntersection(Device device, String peerId, @NonNull String cryptoSystem, String operationType) {
        CSHelper handler = null;
        CryptoImplementation cryptoImpl = CryptoImplementation.fromString(cryptoSystem);
        if (cryptoImpl != null) {
            handler = CSHelpers.get(cryptoImpl);
        } else {
            assert Node.getInstance() != null;
            Node.getInstance().getLogger().log(Level.SEVERE, "Unknown implementation: " + cryptoSystem);
        }

        return intersectionStarter(device, peerId, cryptoSystem, operationType, handler);
    }

    @NonNull
    private String intersectionStarter(Device device, String peerId, @NonNull String cryptoSystem, String operationType, CSHelper handler) {
        if (handler != null) {
            switch (operationType) {
                case "PSI-Domain":
                    runInBackground(() -> domainPSIHandler.intersectionFirstStep(device, peerId, handler));
                    break;
                case "PSI-CA":
                    runInBackground(() -> OPECAHandler.intersectionFirstStep(device, peerId, handler));
                    break;
                case "OPE":
                    runInBackground(() -> OPEHandler.intersectionFirstStep(device, peerId, handler));
                    break;
                default:
                    throw new IllegalArgumentException("Invalid operation type: " + operationType);
            }
        }
        return "Intersection started - " + cryptoSystem + " - " + operationType + " - " + peerId;
    }

    public void launchTest(Device device, String peerId, @Nullable Integer tr, @Nullable String impl, @Nullable String type) {
        if (impl != null) {
            assert tr != null;
            assert type != null;
            CryptoImplementation cryptoImpl = CryptoImplementation.fromString(impl);
            CSHelper handler = CSHelpers.get(cryptoImpl);
            for (int i = 0; i < tr; i++) {
                intersectionStarter(device, peerId, impl, type, handler);
            }
        } else {
            for (int i = 0; i < TEST_ROUNDS; i++) {
                runInBackground(() -> {
                    runInBackground(() -> domainPSIHandler.intersectionFirstStep(device, peerId, Objects.requireNonNull(CSHelpers.get(CryptoImplementation.PAILLIER))));
                    runInBackground(() -> domainPSIHandler.intersectionFirstStep(device, peerId, Objects.requireNonNull(CSHelpers.get(CryptoImplementation.DAMGARD_JURIK))));
                    runInBackground(() -> OPECAHandler.intersectionFirstStep(device, peerId, Objects.requireNonNull(CSHelpers.get(CryptoImplementation.PAILLIER))));
                    runInBackground(() -> OPECAHandler.intersectionFirstStep(device, peerId, Objects.requireNonNull(CSHelpers.get(CryptoImplementation.DAMGARD_JURIK))));
                    runInBackground(() -> OPEHandler.intersectionFirstStep(device, peerId, Objects.requireNonNull(CSHelpers.get(CryptoImplementation.PAILLIER))));
                    runInBackground(() -> OPEHandler.intersectionFirstStep(device, peerId, Objects.requireNonNull(CSHelpers.get(CryptoImplementation.DAMGARD_JURIK))));
                });
            }
        }
    }

    /** @noinspection unchecked*/
    private void handleIntersectionSecondStep(Device device, String peer, String implementation, @NonNull LinkedTreeMap<String, Object> peerData) {
        LinkedTreeMap<String, String> peerPubKey = (LinkedTreeMap<String, String>) peerData.remove("pubkey");
        CSHelper handler;
        CryptoImplementation cryptoImpl = CryptoImplementation.fromString(implementation);
        handler = CSHelpers.get(cryptoImpl);

        assert handler != null;
        if (implementation.contains("PSI-CA")) {
            runInBackground(() -> OPECAHandler.intersectionSecondStep(device, peer, peerPubKey, (ArrayList<String>) peerData.remove("data"), handler));
        } else if (implementation.contains("OPE")) {
            runInBackground(() -> OPEHandler.intersectionSecondStep(device, peer, peerPubKey, (ArrayList<String>) peerData.remove("data"), handler));
        } else {
            runInBackground(() -> domainPSIHandler.intersectionSecondStep(device, peer, peerPubKey, (LinkedTreeMap<String, String>) peerData.remove("data"), handler));
        }
    }

    public void handleFinalStep(@NonNull LinkedTreeMap<String, Object> peerData) {
        String cryptoScheme = (String) peerData.remove("implementation");
        if (cryptoScheme == null) {
            assert Node.getInstance() != null;
            Node.getInstance().getLogger().log(Level.SEVERE, "Missing cryptpscheme field in the final step message");
            return;
        }
        CSHelper handler;
        CryptoImplementation cryptoImpl = CryptoImplementation.fromString(cryptoScheme);
        handler = CSHelpers.get(cryptoImpl);
        assert handler != null;

        if (cryptoScheme.contains("PSI-CA")) {
            runInBackground(() -> OPECAHandler.intersectionFinalStep(peerData, handler));
        } else if (cryptoScheme.contains("OPE")) {
            runInBackground(() -> OPEHandler.intersectionFinalStep(peerData, handler));
        } else {
            runInBackground(() -> domainPSIHandler.intersectionFinalStep(peerData, handler));
        }
    }

    /** @noinspection unchecked*/
    public void handleMessage(String message) {
        Gson gson = new Gson();
        LinkedTreeMap<String, Object> peerData = gson.fromJson(message, LinkedTreeMap.class);

        if (peerData.get("step").equals("2")) {
            handleSecondStep(peerData);
        } else if (peerData.get("step").equals("F")) {
            handleFinalStep(peerData);
        } else {
            assert Node.getInstance() != null;
            Node.getInstance().getLogger().log(Level.SEVERE, "Invalid message format: " + message);
        }
    }

    private void handleSecondStep(@NonNull LinkedTreeMap<String, Object> peerData) {
        String peer = (String) peerData.remove("peer");
        assert Node.getInstance() != null;
        Device device = Node.getInstance().getDevicesMap().get(peer);
        if (device == null) {
            Node.getInstance().getLogger().log(Level.SEVERE, "Device not found for peer: " + peer);
            return;
        }
        String implementation = (String) peerData.remove("implementation");

        assert implementation != null;
        CryptoImplementation cryptoImpl = CryptoImplementation.fromString(implementation);
        if (cryptoImpl != null) {
            handleIntersectionSecondStep(device, peer, implementation, peerData);
        } else {
            Node.getInstance().getLogger().log(Level.SEVERE, "Unknown implementation: " + implementation);
        }
    }

    // Para aprovehcar el logging que tiene IntersectioHandler
    public void keygen(@NonNull String s, int bitLength) {
        new Thread(() -> {
            ActivityLogger logger = new LogActivityProxy(new RealActivityLogger());
            CryptoSystem cs;
            try {
                cs = Objects.requireNonNull(CSHelpers.get(CryptoImplementation.fromString(s))).getCryptoSystem();
            } catch (NullPointerException e) {
                assert Node.getInstance() != null;
                Node.getInstance().getLogger().log(Level.SEVERE, "Unknown implementation: " + s);
                return;
            }
            logger.logStart();
            long startTime = System.currentTimeMillis();
            long startCpuTime = Debug.threadCpuTimeNanos();
            Debug.startMethodTracing();
            cs.keyGeneration(bitLength);
            Debug.stopMethodTracing();
            long cpuTime = Debug.threadCpuTimeNanos() - startCpuTime;
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            logger.logStop();
            logger.logActivity("KEYGEN_" + cs.getClass().getSimpleName() + "-" + bitLength, duration / 1000.0, null, cpuTime);
        }).start();
    }

    public String getPublicKey(String cs) {
        CSHelper handler = CSHelpers.get(CryptoImplementation.fromString(cs));
        assert handler != null;
        return handler.serializePublicKey().toString();
    }
}
