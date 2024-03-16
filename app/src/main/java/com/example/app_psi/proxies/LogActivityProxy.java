package com.example.app_psi.proxies;

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
        System.out.println("Activity " + tag + " took " + duration + " seconds");
    }

    @Override
    public void logResult(List<Integer> result, int size, String peerId, String cryptoScheme) {
        target.logResult(result, size, peerId, cryptoScheme);
    }

}
