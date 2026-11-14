package com.veloster.dashboard.debugger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Private, bounded raw record sink. A verified CarPlay producer must call record(). */
public final class CarPlayRawLog {
    public static final int FILE_LIMIT = 1024 * 1024;
    public static final int PAYLOAD_LIMIT = 16384;
    private final File directory;

    public CarPlayRawLog(File directory) { this.directory = directory; }

    /** Returns a status without logging or truncating sensitive payloads. */
    public synchronized String record(long elapsedMs, String source, String messageType, byte[] payload) {
        if (elapsedMs < 0 || !validToken(source) || !validToken(messageType) || payload == null)
            return "invalid";
        if (payload.length > PAYLOAD_LIMIT) return "too_large";
        StringBuilder line = new StringBuilder(128 + payload.length * 2);
        line.append("CARPLAY_RAW v=1 elapsed_ms=").append(elapsedMs)
            .append(" source=").append(source).append(" type=").append(messageType)
            .append(" original_length=").append(payload.length)
            .append(" stored_length=").append(payload.length).append(" state=complete hex=");
        final char[] hex = "0123456789abcdef".toCharArray();
        for (byte value : payload) { int b = value & 255; line.append(hex[b >>> 4]).append(hex[b & 15]); }
        line.append('\n');
        try {
            byte[] bytes = line.toString().getBytes("UTF-8");
            if (!directory.isDirectory() && !directory.mkdirs()) return "storage_error";
            File current = new File(directory, "carplay-raw.log");
            File previous = new File(directory, "carplay-raw.previous.log");
            if (current.length() + bytes.length > FILE_LIMIT) {
                if (previous.exists() && !previous.delete()) return "storage_error";
                if (!current.renameTo(previous)) return "storage_error";
            }
            FileOutputStream out = new FileOutputStream(current, true);
            try { out.write(bytes); } finally { out.close(); }
            return "stored";
        } catch (IOException e) { return "storage_error"; }
        catch (SecurityException e) { return "storage_error"; }
    }

    private static boolean validToken(String value) {
        if (value == null || value.length() == 0 || value.length() > 64) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                    (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.')) return false;
        }
        return true;
    }
}
