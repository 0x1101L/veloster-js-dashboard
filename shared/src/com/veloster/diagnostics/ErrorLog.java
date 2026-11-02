package com.veloster.diagnostics;

import java.io.File;
import java.io.FileOutputStream;

/** Small private diagnostic log. No exception messages, vehicle values or media metadata. */
public final class ErrorLog implements Thread.UncaughtExceptionHandler {
    public static final int LIMIT=32768;
    private static volatile ErrorLog installed;
    private final File directory;
    private final Thread.UncaughtExceptionHandler previous;
    public ErrorLog(File directory,Thread.UncaughtExceptionHandler previous){this.directory=directory;this.previous=previous;}
    public static synchronized void install(File directory){
        if(installed!=null)return;
        ErrorLog logger=new ErrorLog(directory,Thread.getDefaultUncaughtExceptionHandler());
        Thread.setDefaultUncaughtExceptionHandler(logger);installed=logger;
    }
    public static void record(String source,Throwable error){ErrorLog logger=installed;if(logger!=null)logger.write(source,error);}
    public void uncaughtException(Thread thread,Throwable error){
        try{write("uncaught",error);}finally{if(previous!=null)previous.uncaughtException(thread,error);else error.printStackTrace(System.err);}
    }
    private static String label(String value){
        if(value==null)return "unknown";
        StringBuilder out=new StringBuilder();
        for(int i=0;i<value.length()&&i<120;i++){
            char c=value.charAt(i);out.append(Character.isLetterOrDigit(c)||c=='.'||c=='_'||c=='$'||c=='-'?c:'_');
        }
        return out.toString();
    }
    public synchronized void write(String source,Throwable error){
        // Logging must never replace the original error or interrupt retry/cleanup.
        try{
            StringBuilder out=new StringBuilder("VN-ERROR 1 ");out.append(System.currentTimeMillis()).append(' ').append(label(source)).append('\n');
            Throwable cause=error;
            for(int depth=0;cause!=null&&depth<3;depth++){
                out.append("type ").append(label(cause.getClass().getName())).append('\n');
                StackTraceElement[] frames=cause.getStackTrace();
                for(int i=0;i<frames.length&&i<12;i++){
                    StackTraceElement f=frames[i];out.append(" at ").append(label(f.getClassName())).append('.').append(label(f.getMethodName())).append(':').append(f.getLineNumber()).append('\n');
                }
                Throwable next=cause.getCause();if(next==cause)break;cause=next;
            }
            byte[] bytes=out.toString().getBytes("UTF-8");
            if(bytes.length>LIMIT)return;
            if(!directory.isDirectory()&&!directory.mkdirs())return;
            File current=new File(directory,"errors.log"),old=new File(directory,"errors.previous.log");
            if(current.length()+bytes.length>LIMIT){
                if(old.exists()&&!old.delete())return;
                if(!current.renameTo(old))return;
            }
            FileOutputStream stream=new FileOutputStream(current,true);
            try{stream.write(bytes);}finally{stream.close();}
        }catch(Throwable ignored){/* Best effort, including a full/unavailable filesystem. */}
    }
}
