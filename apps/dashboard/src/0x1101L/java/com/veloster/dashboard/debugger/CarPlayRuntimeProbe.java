package com.veloster.dashboard.debugger;

import com.veloster.i18n.UiText;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.security.MessageDigest;

/** Read-only best-effort checks; library presence is not proof of an active CarPlay session. */
public final class CarPlayRuntimeProbe {
    private static final String REFERENCE_SHA256 = "7916f0136adf617e74817cfd0b54fe3e1959d6a8be2b7ea2a0a20c214e2f8224";

    public static final class Snapshot {
        public final String library;
        public final String process;
        Snapshot(String library, String process) { this.library = library; this.process = process; }
    }

    private CarPlayRuntimeProbe() {}

    public static Snapshot inspect() {
        File libraryFile = findLibrary();
        String library = libraryStatus(libraryFile);
        String process = processStatus();
        return new Snapshot(library, process);
    }

    private static File findLibrary() {
        String[] paths = {"/system/lib/libiap2.so", "/vendor/lib/libiap2.so", "/system/lib/arm/libiap2.so"};
        for (String path : paths) {
            File file = new File(path);
            if (file.isFile()) return file;
        }
        return null;
    }

    private static String libraryStatus(File file) {
        if (file == null) return "파일 없음";
        InputStreamHolder input = null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            input = new InputStreamHolder(new FileInputStream(file));
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.stream.read(buffer)) != -1) digest.update(buffer, 0, count);
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest.digest()) hex.append(String.format(java.util.Locale.US, "%02x", value & 255));
            return REFERENCE_SHA256.equals(hex.toString()) ? "파일 확인됨" : "파일 있음 · 기준본과 해시 다름";
        } catch (Exception e) {
            return "파일 읽기 불가";
        } finally {
            if (input != null) input.close();
        }
    }

    private static String processStatus() {
        File proc = new File("/proc");
        File[] entries = proc.listFiles();
        if (entries == null) return "프로세스 목록 접근 제한 · 미확인";
        int readable = 0, unreadable = 0;
        for (File entry : entries) {
            if (!isDigits(entry.getName())) continue;
            File maps = new File(entry, "maps");
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(maps));
                readable++;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("libiap2.so")) return UiText.t("로드 관측 · PID") + " " + entry.getName();
                }
            } catch (Exception ignored) {
                // An unreadable process is unknown, not evidence that the library is absent.
                unreadable++;
            } finally {
                if (reader != null) try { reader.close(); } catch (Exception ignored) {}
            }
        }
        if (unreadable > 0) return "일부 maps 접근 제한 · 미확인";
        return readable > 0 ? "현재 로드 미관측" : "maps 접근 제한 · 미확인";
    }

    private static boolean isDigits(String value) {
        if (value.length() == 0) return false;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) < '0' || value.charAt(i) > '9') return false;
        return true;
    }

    private static final class InputStreamHolder {
        final FileInputStream stream;
        InputStreamHolder(FileInputStream stream) { this.stream = stream; }
        void close() { try { stream.close(); } catch (Exception ignored) {} }
    }
}
