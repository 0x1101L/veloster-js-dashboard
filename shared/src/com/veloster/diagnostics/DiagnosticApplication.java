package com.veloster.diagnostics;

import android.app.Application;
import java.io.File;

/** Installed before either app's Activity, Service or receiver is created. */
public final class DiagnosticApplication extends Application {
    public void onCreate(){
        super.onCreate();
        try{ErrorLog.install(new File(getFilesDir(),"diagnostics"));}
        catch(RuntimeException ignored){/* Application startup must not depend on logging. */}
    }
}
