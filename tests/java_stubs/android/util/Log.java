package android.util;

public final class Log {
    public static final java.util.List<String> lines=new java.util.ArrayList<String>();
    public static int i(String tag,String message){lines.add(tag+":"+message);return 0;}
}
