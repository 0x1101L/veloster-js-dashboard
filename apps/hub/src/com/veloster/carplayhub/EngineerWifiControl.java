package com.veloster.carplayhub;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Requests the OEM EngineerMode Wi-Fi operation; a return value is not radio state. */
final class EngineerWifiControl {
    static final class UnsafeStateException extends Exception { }
    static boolean request(Object manager, boolean enabled, int apState, int wifiState) throws Exception {
        if (manager == null) throw new IllegalStateException("Wi-Fi service unavailable");
        if (enabled && (apState != 11 || (wifiState != 1 && wifiState != 3 && wifiState != 4)))
            throw new UnsafeStateException();
        Method method = manager.getClass().getMethod("setWifiEnabledForEngineerMode", Boolean.TYPE, Boolean.TYPE);
        try {
            return Boolean.TRUE.equals(method.invoke(manager, Boolean.valueOf(enabled), Boolean.FALSE));
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw e;
        }
    }
}
