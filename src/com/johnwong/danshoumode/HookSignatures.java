package com.johnwong.danshoumode;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class HookSignatures {
    static final String TRANSACTION="android.view.SurfaceControl$Transaction";
    static final String DEVICE="com.android.server.display.DisplayDevice";
    static final class Configuration {
        final Method method;
        final int transactionIndex,deviceIndex;
        Configuration(Method m,int t,int d){method=m;transactionIndex=t;deviceIndex=d;}
    }
    /** Identify typed arguments, retaining all OEM-added arguments unchanged. */
    static Configuration configuration(Class<?> type) {
        Configuration found=null;
        for(Method m:Reflect.named(type,"configureDisplayLocked")) {
            if(m.getReturnType()!=void.class||Modifier.isStatic(m.getModifiers()))continue;
            int t=-1,d=-1;boolean duplicate=false;Class<?>[] p=m.getParameterTypes();
            for(int i=0;i<p.length;i++) {
                if(p[i].getName().equals(TRANSACTION)){if(t>=0)duplicate=true;t=i;}
                if(p[i].getName().equals(DEVICE)){if(d>=0)duplicate=true;d=i;}
            }
            if(t<0||d<0||duplicate)continue;
            if(found!=null)throw new IllegalStateException("多个 configureDisplayLocked 候选，拒绝猜测");
            m.setAccessible(true);found=new Configuration(m,t,d);
        }
        if(found==null)throw new IllegalStateException("找不到可识别的 LogicalDisplay.configureDisplayLocked");
        return found;
    }
}
