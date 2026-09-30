package com.invoicespot.auth.session;

// A short name for the browser and system a session signed in from, read
// from its user agent. Some browsers name others too, so the order matters.
public final class DeviceName {

    private DeviceName() {}

    public static String of(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown device";
        }
        String browser = browser(userAgent);
        String system = system(userAgent);
        if (browser == null) {
            return system == null ? "Unknown device" : system;
        }
        return system == null ? browser : browser + " on " + system;
    }

    private static String browser(String agent) {
        if (agent.contains("Edg/") || agent.contains("EdgiOS/")) {
            return "Edge";
        }
        if (agent.contains("OPR/")) {
            return "Opera";
        }
        if (agent.contains("Firefox/") || agent.contains("FxiOS/")) {
            return "Firefox";
        }
        if (agent.contains("Chrome/") || agent.contains("CriOS/")) {
            return "Chrome";
        }
        return agent.contains("Safari/") ? "Safari" : null;
    }

    private static String system(String agent) {
        if (agent.contains("iPhone")) {
            return "iPhone";
        }
        if (agent.contains("iPad")) {
            return "iPad";
        }
        if (agent.contains("Android")) {
            return "Android";
        }
        if (agent.contains("Mac OS X") || agent.contains("Macintosh")) {
            return "macOS";
        }
        if (agent.contains("Windows")) {
            return "Windows";
        }
        if (agent.contains("CrOS")) {
            return "ChromeOS";
        }
        return agent.contains("Linux") ? "Linux" : null;
    }
}
