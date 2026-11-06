package com.johnwong.danshoumode;

import android.os.Build;
import java.lang.reflect.Method;

final class Diagnostics {
    private static final StringBuilder events=new StringBuilder();
    static synchronized void add(String text) {
        if(events.length()>16000)events.delete(0,4000);
        events.append(text).append('\n');
    }
    static synchronized void clear() { events.setLength(0); }
    static void methods(Class<?> type,String... names) {
        for(String name:names) {
            boolean found=false;
            for(Method m:Reflect.named(type,name)){add(m.toString());found=true;}
            if(!found)add(type.getName()+"#"+name+": absent");
        }
    }
    static synchronized String report() {
        return "伪单手模式 1.2.0-preview / libxposed API 102\n"+
                "Device: "+Build.MANUFACTURER+" "+Build.MODEL+"\nAndroid: "+Build.VERSION.RELEASE+
                " SDK "+Build.VERSION.SDK_INT+"\nBuild: "+Build.DISPLAY+"\n"+
                "Hooks ready: "+Entry.hooksReady+"; hand hooks: "+Entry.handHooksReady+"\n"+events;
    }
    static String concise(Throwable t) {
        Throwable cause=t;
        while(cause.getCause()!=null&&cause.getCause()!=cause)cause=cause.getCause();
        return cause.getClass().getSimpleName()+": "+String.valueOf(cause.getMessage());
    }
}
