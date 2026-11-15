package com.veloster.dashboard.debugger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.EOFException;

/** Private CarPlay capture archive; CPRAW1 files are validated and preserved byte-for-byte. */
public final class CarPlayRawLog {
    public static final int FILE_LIMIT = 1024 * 1024;
    public static final int PAYLOAD_LIMIT = 16384;
    private final File directory;

    /** Imports the original CPRAW1 file byte-for-byte into app-private storage. */
    public synchronized File importCapture(InputStream source, long nowMs) throws IOException {
        if (source == null || nowMs < 0) throw new IOException("invalid capture input");
        File rawDirectory = new File(directory, "carplay-raw");
        if (!rawDirectory.isDirectory() && !rawDirectory.mkdirs()) throw new IOException("cannot create private capture directory");
        File temporary = File.createTempFile(".incoming-", ".cpraw", rawDirectory);
        long total = 0;
        try {
            BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(temporary));
            try {
                byte[] buffer = new byte[8192]; int count;
                while ((count = source.read(buffer)) != -1) {
                    total += count;
                    if (total > 16L * 1024 * 1024) throw new IOException("capture exceeds 16 MiB");
                    out.write(buffer, 0, count);
                }
            } finally { out.close(); }
            validateCapture(temporary);
            File target = new File(rawDirectory, "carplay-iap2-" + nowMs + ".cpraw");
            if (target.exists() || !temporary.renameTo(target)) throw new IOException("cannot finalize private capture");
            return target;
        } finally { try { source.close(); } catch (IOException ignored) {} if (temporary.exists()) temporary.delete(); }
    }

    private static void validateCapture(File file) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
        try {
            byte[] magic = new byte[8]; in.readFully(magic);
            if (!new String(magic, "US-ASCII").equals("CPRAW1\r\n")) throw new IOException("not a CPRAW1 capture");
            boolean footer = false; long records = 0; int previousSequence = 0;
            while (!footer) {
                byte[] marker = new byte[4]; in.readFully(marker); String kind = new String(marker, "US-ASCII");
                if ("DROP".equals(kind)) {
                    in.readInt(); int reason = in.readInt();
                    if (reason < 0 || reason > 3 || in.read() != -1) throw new IOException("invalid CPRAW1 footer");
                    footer = true;
                } else if ("I2RX".equals(kind) || "RDRX".equals(kind)) {
                    int sequence = in.readInt(); in.readLong(); int length = in.readInt();
                    if (sequence <= previousSequence) throw new IOException("non-increasing CPRAW1 sequence");
                    previousSequence = sequence;
                    if (length <= 0 || length > 65536) throw new IOException("invalid iAP2 record length");
                    long skipped = 0; while (skipped < length) { int n = in.skipBytes((int)Math.min(length-skipped, 8192)); if (n <= 0) throw new EOFException("truncated iAP2 record"); skipped += n; }
                    records++;
                } else throw new IOException("invalid CPRAW1 record marker");
            }
            if (records == 0) throw new IOException("capture contains no iAP2 receive records");
        } finally { in.close(); }
    }

    public synchronized String latestCaptureSummary() {
        File dir = new File(directory, "carplay-raw"); File[] files = dir.listFiles();
        if (files == null) return "내부 저장 캡처 없음";
        File latest = null;
        for (File file : files) if (file.isFile() && file.getName().endsWith(".cpraw") && (latest == null || file.lastModified() > latest.lastModified())) latest = file;
        return latest == null ? "내부 저장 캡처 없음" : latest.getName() + " · " + latest.length() + " bytes";
    }

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
