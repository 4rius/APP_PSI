package com.example.app_psi.proxies;

import android.util.Log;

import java.util.List;

public class LogActivityProxy implements ActivityLogger {
    private final ActivityLogger target;

    public LogActivityProxy(ActivityLogger target) {
        this.target = target;
    }

    @Override
    public void logStart() {
        target.logStart();
    }

    @Override
    public void logStop() {
        target.logStop();
    }

    @Override
    public void logActivity(String tag, double duration, String peerId, long cpuTime) {
        target.logActivity(tag, duration, peerId, cpuTime);
        Log.d("LogActivityProxy",  tag + " - " + duration + " s");
    }

    @Override
    public void logResult(List<Integer> result, int size, String peerId, String cryptoScheme) {
        target.logResult(result, size, peerId, cryptoScheme);
        Log.d("LogActivityProxy", "logResult: " + result + " - " + size + " - " + peerId + " - " + cryptoScheme);
    }

}
