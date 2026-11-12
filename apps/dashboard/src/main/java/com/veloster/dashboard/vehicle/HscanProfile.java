package com.veloster.dashboard.vehicle;

/** Exact head-unit product/build selection for the read-only HSCAN subscriber. */
public final class HscanProfile {
    public enum Kind { UNSUPPORTED, JSN, PD, JS }

    public static Kind identify(String product, String incremental) {
        if ("pd_kr".equals(product) && "eng.vmadmin.20260114.084130".equals(incremental)) return Kind.PD;
        if ("js_kr".equals(product) && "eng.vmadmin.20260114.090656".equals(incremental)) return Kind.JS;
        if ("jsn_kr".equals(product) && "eng.vmadmin.20250902.081552".equals(incremental)) return Kind.JSN;
        return Kind.UNSUPPORTED;
    }
}
