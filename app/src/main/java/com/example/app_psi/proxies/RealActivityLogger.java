package com.example.app_psi.proxies;

import static com.example.app_psi.DbConstants.VERSION;

import com.example.app_psi.services.LogService;

import java.util.List;

public class RealActivityLogger implements ActivityLogger {
    @Override
    public void logStart() {
        LogService.Companion.startLogging();
    }

    @Override
    public void logStop() {
        LogService.Companion.stopLogging();
    }

    @Override
    public void logActivity(String tag, double duration, String peerId, long cpuTime) {
        LogService.Companion.logActivity(tag, duration, VERSION, peerId, cpuTime);
    }

    @Override
    public void logResult(List<Integer> result, int size, String peerId, String cryptoScheme) {
        LogService.Companion.logResult(result, size, com.example.app_psi.DbConstants.VERSION, peerId, cryptoScheme);    }
}
