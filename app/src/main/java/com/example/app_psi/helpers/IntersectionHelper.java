package com.example.app_psi.helpers;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.app_psi.objects.Device;
import com.example.app_psi.objects.Node;
import com.example.app_psi.proxies.ActivityLogger;
import com.example.app_psi.proxies.LogActivityProxy;
import com.example.app_psi.proxies.RealActivityLogger;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;

public abstract class IntersectionHelper {

    private static ActivityLogger logger;

    public IntersectionHelper() {
        logger = new LogActivityProxy(new RealActivityLogger());
    }

    protected void sendJsonMessage(@NonNull Device device, Object resSet, String impName, String step, @Nullable LinkedTreeMap<String, String> publicKeyDict) {
        HashMap<String, Object> message = new HashMap<>();
        message.put("data", resSet);
        if (publicKeyDict != null) message.put("pubkey", publicKeyDict);
        message.put("implementation", impName);
        message.put("peer", Node.getInstance().getId());
        message.put("step", step);
        Gson gson = new Gson();
        device.socket.send(gson.toJson(message));
    }

    protected void logIntersectionStart(String id, String peerId, String schemeName, @Nullable String type) {
        if (type == null) Log.d("Node", id + " (You) - Intersection with " + peerId + " - " + schemeName + " OPE");
        else Log.d("Node", id + " (You) - Intersection with " + peerId + " - " + schemeName + " " + type + " OPE");
    }

    protected ActivityLogger getLogger() {
        return logger;
    }

    public abstract void intersectionFirstStep(Device device, String peerId, @NonNull CSHelper handler);
    public abstract void intersectionSecondStep(Device device, String peer, LinkedTreeMap<String, String> peerPubKey, ArrayList<String> data, CSHelper handler);
    public abstract void intersectionSecondStep(Device device, String peer, LinkedTreeMap<String, String> peerPubKey, LinkedTreeMap<String, String> data, CSHelper handler);
    public abstract void intersectionFinalStep(@NonNull LinkedTreeMap<String, Object> peerData, @NonNull CSHelper handler);
}
