package com.sandesh.watch;

import java.nio.charset.StandardCharsets;

/** Small, versioned, memory-only request/response protocol. Never carries a send command. */
public final class WatchProtocol {
    private WatchProtocol() {}
    public static final int VERSION = 1;
    public static final int MAX_REQUEST = 1024;
    public static final int MAX_RESPONSE = 48000;
    public static final int CONVERSATIONS = 10;
    public static final int HISTORY = 5;
    public static final int TEXT = 480;
    public static final String REQUEST = "/sandesh/widget/v1/request";
    public static final String RESPONSE = "/sandesh/widget/v1/response";
    public static final String CLEAR = "/sandesh/widget/v1/clear";
    public static boolean validToken(String value) {
        return value != null && value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
    public static boolean validThread(String value) {
        try { return value != null && value.matches("[0-9]{1,19}") && Long.parseLong(value) > 0; }
        catch (NumberFormatException ignored) { return false; }
    }
    public static boolean validCommand(String value) { return "inbox".equals(value) || "thread".equals(value) || "open".equals(value); }
    public static String clip(String value, int points) {
        if (value == null) return "";
        int count = value.codePointCount(0, value.length());
        return count <= points ? value : value.substring(0, value.offsetByCodePoints(0, points)) + "…";
    }
    public static byte[] bytes(String value, int limit) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > limit) throw new IllegalArgumentException("Watch payload too large");
        return bytes;
    }
}
