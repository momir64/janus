package com.android.tools.utp.gradle;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

/**
 * Drop-in replacement for AGP's DeviceTrackingListener (com.android.tools.utp:gradle-work-action).
 *
 * Identical to the original except for the decode in getDeviceId. JUnit's UniqueIdFormat
 * percent-encodes ':' inside segment values, so a device connected as "adb connect host:port"
 * is stored under the key "192.168.0.7%3A5555" while AndroidTestEngineRunner looks it up by
 * the raw serial "192.168.0.7:5555" in
 *
 *     serials.all { perDeviceAllTestsPassed[it] ?: false }
 *
 * The miss is silently read as a failure, so connectedAndroidTest reports "There were failing
 * tests" for a run in which every test passed. Decoding restores the match.
 */
public final class DeviceTrackingListener implements TestExecutionListener {
    private final ConcurrentHashMap<String, Boolean> perDeviceAllTestsPassed =
            new ConcurrentHashMap<String, Boolean>();

    public final ConcurrentHashMap<String, Boolean> getPerDeviceAllTestsPassed() {
        return perDeviceAllTestsPassed;
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        String deviceId = getDeviceId(testIdentifier);
        if (testIdentifier.isContainer() && deviceId.length() > 0) {
            perDeviceAllTestsPassed.putIfAbsent(deviceId, Boolean.TRUE);
        }
    }

    @Override
    public void executionFinished(
            TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        String deviceId = getDeviceId(testIdentifier);
        if (testIdentifier.isTest() && deviceId.length() > 0) {
            perDeviceAllTestsPassed.put(
                    deviceId,
                    testExecutionResult.getStatus() != TestExecutionResult.Status.FAILED);
        }
    }

    private String getDeviceId(TestIdentifier testIdentifier) {
        String uniqueId = testIdentifier.getUniqueId();
        int start = uniqueId.lastIndexOf("[device:");
        if (start < 0) {
            return "";
        }
        String rest = uniqueId.substring(start + "[device:".length());
        int end = rest.indexOf(']');
        if (end >= 0) {
            rest = rest.substring(0, end);
        }
        try {
            return URLDecoder.decode(rest, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return rest;
        }
    }
}
