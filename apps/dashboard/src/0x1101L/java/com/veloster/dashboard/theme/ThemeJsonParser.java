package com.veloster.dashboard.theme;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small strict JSON reader for the bundled, data-only theme contract (API 17 / Java 7). */
public final class ThemeJsonParser {
    private final String source;
    private int at;

    private ThemeJsonParser(String source) {
        if (source == null) throw new IllegalArgumentException("JSON source is null");
        this.source = source;
    }

    public static Object parse(String source) {
        ThemeJsonParser parser = new ThemeJsonParser(source);
        Object value = parser.value(0);
        parser.space();
        if (parser.at != parser.source.length()) throw parser.error("trailing content");
        return value;
    }

    private Object value(int depth) {
        if(depth>64)throw error("nesting limit exceeded");
        space();
        if (at >= source.length()) throw error("expected value");
        char c = source.charAt(at);
        if (c == '{') return object(depth+1);
        if (c == '[') return array(depth+1);
        if (c == '"') return string();
        if (c == 't') { literal("true"); return Boolean.TRUE; }
        if (c == 'f') { literal("false"); return Boolean.FALSE; }
        if (c == 'n') { literal("null"); return null; }
        if (c == '-' || (c >= '0' && c <= '9')) return number();
        throw error("unexpected value");
    }

    private Map<String,Object> object(int depth) {
        at++;
        LinkedHashMap<String,Object> result = new LinkedHashMap<String,Object>();
        space();
        if (take('}')) return result;
        while (true) {
            space();
            if (at >= source.length() || source.charAt(at) != '"') throw error("expected object key");
            String key = string();
            space(); require(':');
            if (result.containsKey(key)) throw error("duplicate object key: " + key);
            result.put(key, value(depth));
            space();
            if (take('}')) return result;
            require(',');
        }
    }

    private List<Object> array(int depth) {
        at++;
        ArrayList<Object> result = new ArrayList<Object>();
        space();
        if (take(']')) return result;
        while (true) {
            result.add(value(depth));
            space();
            if (take(']')) return result;
            require(',');
        }
    }

    private String string() {
        require('"');
        StringBuilder result = new StringBuilder();
        while (at < source.length()) {
            char c = source.charAt(at++);
            if (c == '"') return result.toString();
            if (c < 0x20) throw error("control character in string");
            if (c != '\\') { result.append(c); continue; }
            if (at >= source.length()) throw error("unfinished escape");
            char escaped = source.charAt(at++);
            if (escaped == '"' || escaped == '\\' || escaped == '/') result.append(escaped);
            else if (escaped == 'b') result.append('\b');
            else if (escaped == 'f') result.append('\f');
            else if (escaped == 'n') result.append('\n');
            else if (escaped == 'r') result.append('\r');
            else if (escaped == 't') result.append('\t');
            else if (escaped == 'u') {
                if (at + 4 > source.length()) throw error("short unicode escape");
                int code = 0;
                for (int i = 0; i < 4; i++) {
                    int digit = Character.digit(source.charAt(at++), 16);
                    if (digit < 0) throw error("invalid unicode escape");
                    code = (code << 4) | digit;
                }
                result.append((char)code);
            } else throw error("invalid escape");
        }
        throw error("unterminated string");
    }

    private Number number() {
        int start = at;
        if (take('-') && at >= source.length()) throw error("invalid number");
        if (take('0')) {
            if (at < source.length() && source.charAt(at)>='0'&&source.charAt(at)<='9') throw error("leading zero");
        } else {
            digits(true);
        }
        boolean decimal = false;
        if (take('.')) { decimal = true; digits(true); }
        if (take('e') || take('E')) {
            decimal = true;
            if (!take('+')) take('-');
            digits(true);
        }
        String token = source.substring(start, at);
        try { if (decimal) return Double.valueOf(token); return Long.valueOf(token); }
        catch (NumberFormatException e) { throw error("invalid number"); }
    }

    private void digits(boolean required) {
        int start = at;
        while (at < source.length() && source.charAt(at)>='0'&&source.charAt(at)<='9') at++;
        if (required && at == start) throw error("expected digit");
    }

    private void literal(String expected) {
        if (!source.regionMatches(at, expected, 0, expected.length())) throw error("invalid literal");
        at += expected.length();
    }

    private void space() {
        while (at < source.length()) {
            char c = source.charAt(at);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') at++;
            else return;
        }
    }

    private boolean take(char expected) {
        if (at < source.length() && source.charAt(at) == expected) { at++; return true; }
        return false;
    }

    private void require(char expected) {
        if (!take(expected)) throw error("expected '" + expected + "'");
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at character " + at);
    }
}
