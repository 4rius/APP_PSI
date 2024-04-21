package uk.arias.app_psi.proxies;

import java.util.List;

public interface ActivityLogger {
    void logStart();
    void logStop();
    void logActivity(String tag, double duration, String peerId, long cpuTime);
    void logResult(List<Integer> result, int size, String peerId, String cryptoScheme);
}