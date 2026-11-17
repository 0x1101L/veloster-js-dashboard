package com.veloster.carplayhub;

public class EngineerWifiControlTest {
    public static class Manager {
        boolean called, enabled, second;
        public boolean setWifiEnabledForEngineerMode(boolean a, boolean b) {
            called=true; enabled=a; second=b; return true;
        }
    }
    public static class Denied {
        public boolean setWifiEnabledForEngineerMode(boolean a, boolean b) { throw new SecurityException(); }
    }
    public static void main(String[] args) throws Exception {
        Manager m=new Manager();
        assert EngineerWifiControl.request(m,true,11,1) && m.called && m.enabled && !m.second;
        m.called=false;
        try { EngineerWifiControl.request(m,true,13,1); throw new AssertionError("active AP accepted"); }
        catch(EngineerWifiControl.UnsafeStateException expected) { assert !m.called; }
        try { EngineerWifiControl.request(m,true,11,2); throw new AssertionError("Wi-Fi enabling accepted"); }
        catch(EngineerWifiControl.UnsafeStateException expected) { assert !m.called; }
        assert EngineerWifiControl.request(m,true,11,4) && m.called && m.enabled;
        assert EngineerWifiControl.request(m,false,-1,4) && m.called && !m.enabled && !m.second;
        try { EngineerWifiControl.request(new Denied(),true,11,1); throw new AssertionError("denial hidden"); }
        catch(SecurityException expected) { }
        System.out.println("PASS OEM Wi-Fi request arguments, state gate and permission denial");
    }
}
