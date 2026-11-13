package com.veloster.dashboard.debugger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Bounded private navigation observation log. Accepts only explicitly defined events. */
public final class HudEventLog {
    public static final int LIMIT = 65536;
    private final File directory;
    private final Map<String,String> last = new HashMap<String,String>();
    private final Map<String,Long> counts = new HashMap<String,Long>();

    public HudEventLog(File directory) { this.directory = directory; }

    /** Returns false on storage failure. Unknown events and invalid values are ignored. */
    public synchronized boolean record(long elapsedMs, String name, String detail) {
        if (elapsedMs < 0 || name == null || detail == null) return true;
        String value = value(name, detail);
        if ("binding.media.3".equals(name) && ("received".equals(detail) || "processed".equals(detail) || "invalid".equals(detail))) {
            Long old=counts.get(detail);long count=old==null?1:old.longValue()+1;
            counts.put(detail,Long.valueOf(count));
            if ((count & (count-1))!=0) return true;
            value=detail+"_"+count;
        }
        String key = key(name);
        if (value == null || value.equals(last.get(key))) return true;
        try {
            byte[] line = ("HUD_EVENT v=1 elapsed_ms=" + elapsedMs + " event=" + name
                    + " value=" + value + "\n").getBytes("UTF-8");
            if (!directory.isDirectory() && !directory.mkdirs()) return false;
            File current = new File(directory, "hud-events.log");
            File previous = new File(directory, "hud-events.previous.log");
            if (current.length() + line.length > LIMIT) {
                if (previous.exists() && !previous.delete()) return false;
                if (!current.renameTo(previous)) return false;
            }
            FileOutputStream out = new FileOutputStream(current, true);
            try { out.write(line); } finally { out.close(); }
            last.put(key, value);
            return true;
        } catch (IOException e) { return false; }
        catch (SecurityException e) { return false; }
    }

    private static String key(String name) {
        if (name.startsWith("navigation.route.observer") || name.equals("navigation.route.stopped")) return "route.lifecycle";
        if (name.startsWith("navigation.observer")) return "provider.lifecycle";
        return name;
    }

    public static boolean accepts(String name, String detail) {
        return name != null && detail != null && value(name, detail) != null;
    }

    private static String value(String name, String detail) {
        if (name.equals("carplay.screen.probe"))
            return detail.equals("armed") || detail.equals("captured") || detail.equals("wrong_foreground") || detail.equals("permission_denied") || detail.equals("unavailable") || detail.equals("error") ? detail : null;
        if (name.equals("carplay.media.playback"))
            return detail.equals("playing") || detail.equals("paused") ? detail : null;
        if (name.equals("carplay.media.mode"))
            return detail.equals("active") || detail.equals("inactive") ? detail : null;
        if (name.equals("carplay.media.visibility"))
            return detail.equals("hidden") || detail.equals("visible") ? detail : null;
        if (name.equals("binding.media.3")) {
            if (detail.equals("registered") || detail.equals("stopped") || detail.equals("permission denied") || detail.equals("error"))
                return detail.equals("permission denied") ? "permission_denied" : detail;
            if (detail.equals("received") || detail.equals("processed") || detail.equals("invalid")) return detail;
            return null;
        }
        if (name.equals("navigation.route_state") || name.equals("navigation.state_destination")) {
            try { return Integer.toString(Integer.parseInt(detail)); }
            catch (NumberFormatException e) { return null; }
        }
        if (name.equals("navigation.route.source"))
            return detail.equals("sticky") || detail.equals("broadcast") ? detail : null;
        if (name.equals("navigation.route.observer") || name.equals("navigation.observer")) return "registered";
        if (name.equals("navigation.route.stopped") || name.equals("navigation.observer.stopped")) return "stopped";
        if (name.equals("navigation.route.observer.error") || name.equals("navigation.observer.error")
                || name.equals("navigation.state.error") || name.equals("navigation.route.close")
                || name.equals("navigation.observer.close")) return "error";
        if (name.equals("navigation.route.invalid") || name.equals("navigation.state")) return "unavailable";
        if (name.equals("tbt.returned")) return "returned_unverified";
        return null;
    }
}
