package uk.arias.app_psi.proxies;

import uk.arias.app_psi.services.LogService;

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
    public void logActivity(String tag, double duration, String peerId, long cpuTime, Integer size, Integer encSize) {
        LogService.Companion.logActivity(tag, duration, peerId, cpuTime, size, encSize);
    }

    @Override
    public void logResult(List<Integer> result, int size, String peerId, String cryptoScheme) {
        LogService.Companion.logResult(result, size, peerId, cryptoScheme);    }
}
