package com.johnwong.danshoumode;

import android.view.KeyEvent;
import android.util.Log;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import io.github.libxposed.api.XposedModule;

public final class Entry extends XposedModule {
    static volatile Controller controller;
    static volatile Object displayManager;
    static volatile boolean projectionSeen,viewportSeen,hooksReady,handHooksReady;
    static volatile String hookFailure="系统 Hook 尚未就绪";
    private static volatile Entry instance;
    private boolean installed;

    static void log(Throwable t) {
        Diagnostics.add(Diagnostics.concise(t));
        Entry e=instance;
        if(e!=null)e.log(Log.ERROR,"伪单手模式",Diagnostics.concise(t),t);
        else Log.e("伪单手模式",Diagnostics.concise(t),t);
    }
    @Override public void onModuleLoaded(ModuleLoadedParam p) {
        instance=this;
        Diagnostics.add("Framework: "+getFrameworkName()+" "+getFrameworkVersion()+
                " ("+getFrameworkVersionCode()+") API "+getApiVersion()+"; process="+p.getProcessName());
        log(Log.INFO,"伪单手模式","Modern API module loaded; systemServer="+p.isSystemServer());
    }
    @Override public synchronized void onSystemServerStarting(SystemServerStartingParam p) {
        if(installed)return;installed=true;
        ClassLoader loader=p.getClassLoader();
        Bootstrap.start(loader); // Diagnostics/control lifecycle is independent of display hooks.
        List<HookHandle> behaviorHooks=new ArrayList<>();
        try {
            Class<?> serviceClass=Reflect.findClass("com.android.server.display.DisplayManagerService",loader);
            for(Constructor<?> constructor:serviceClass.getDeclaredConstructors()) {
                hook(constructor).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain->{
                    Object result=chain.proceed();displayManager=chain.getThisObject();return result;
                });
            }
            // A service-ready callback also captures the service if constructor chaining is optimized.
            for(Method ready:Reflect.named(serviceClass,"systemReady")) {
                hook(ready).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain->{
                    Object result=chain.proceed();displayManager=chain.getThisObject();return result;
                });
            }
            Class<?> deviceClass=Reflect.findClass("com.android.server.display.DisplayDevice",loader);
            Class<?> logicalClass=Reflect.findClass("com.android.server.display.LogicalDisplay",loader);
            Class<?> viewportClass=Reflect.findClass("android.hardware.display.DisplayViewport",loader);
            Class<?> rotationClass=Reflect.findClass("com.android.server.wm.DisplayRotation",loader);
            Class<?> policyClass=Reflect.findClass("com.android.server.policy.PhoneWindowManager",loader);
            // Record actual ROM signatures, including the previously failing method, without requiring it.
            Diagnostics.methods(deviceClass,"setProjectionLocked","populateViewportLocked","getDisplayTokenLocked");
            Diagnostics.methods(logicalClass,"configureDisplayLocked");
            Diagnostics.methods(rotationClass,"rotationForOrientation");
            Diagnostics.methods(serviceClass,"systemReady","scheduleTraversalLocked");
            HookSignatures.Configuration config=HookSignatures.configuration(logicalClass);
            ProjectionAdapter adapter=new ProjectionAdapter(deviceClass,config.method.getParameterTypes()[config.transactionIndex],loader);
            Method viewport=Reflect.findMethodExact(deviceClass,"populateViewportLocked",viewportClass);
            Method rotation=Reflect.findMethodExact(rotationClass,"rotationForOrientation",int.class,int.class);
            Method keys=Reflect.findMethodExact(policyClass,"interceptKeyBeforeQueueing",KeyEvent.class,int.class);
            Reflect.findMethodExact(serviceClass,"scheduleTraversalLocked",boolean.class);
            Reflect.findField(serviceClass,"mSyncRoot");Reflect.findField(serviceClass,"mLogicalDisplayMapper");

            behaviorHooks.add(hook(config.method).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain->{
                // The system first configures the display normally, including its original viewport caches.
                Object result=chain.proceed();projectionSeen=true;
                Controller c=controller;Object device=chain.getArg(config.deviceIndex);
                if(c!=null&&c.isLegacy()&&device==c.device) {
                    try {
                        Object info=Reflect.callMethod(device,"getDisplayDeviceInfoLocked");
                        if(Reflect.getIntField(info,"width")!=c.nativeWidth||Reflect.getIntField(info,"height")!=c.nativeHeight)
                            throw new IllegalStateException("屏幕物理尺寸已变化");
                        adapter.apply(device,chain.getArg(config.transactionIndex),c.nativeWidth,c.nativeHeight);
                        c.projections++;
                    }catch(Throwable t){c.fail(t);}
                }
                return result;
            }));
            behaviorHooks.add(hook(viewport).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain->{
                Object result=chain.proceed();viewportSeen=true;Controller c=controller;
                if(c!=null&&c.isLegacy()&&chain.getThisObject()==c.device) {
                    try {adapter.verify(c.device,chain.getArg(0),c.nativeWidth,c.nativeHeight);c.viewports++;}
                    catch(Throwable t){c.fail(t);}
                }
                return result;
            }));
            behaviorHooks.add(hook(rotation).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain->{
                Controller c=controller;
                if(c!=null&&c.active) {
                    try {
                        Object dc=Reflect.getObjectField(chain.getThisObject(),"mDisplayContent");
                        if(((Integer)Reflect.callMethod(dc,"getDisplayId"))==0)return c.isHand()?c.handSurface.rotation():0;
                    }catch(Throwable t){c.fail(t);}
                }
                return chain.proceed();
            }));
            behaviorHooks.add(hook(keys).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain->{
                Controller c=controller;
                if(c!=null&&c.active) {
                    KeyEvent e=(KeyEvent)chain.getArg(0);
                    if(e.getKeyCode()==KeyEvent.KEYCODE_VOLUME_DOWN&&e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0)c.emergencyKey();
                }
                return chain.proceed();
            }));
            hooksReady=true;hookFailure="";
            try {
                Class<?> dcClass=Reflect.findClass("com.android.server.wm.DisplayContent",loader);
                Method changes=Reflect.findMethodExact(dcClass,"applySurfaceChangesTransaction");
                hook(changes).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain->{
                    Object result=chain.proceed();Controller c=controller;
                    if(c!=null&&c.isHand())try {c.handSurface.verify(chain.getThisObject());}catch(Throwable t){c.fail(t);}
                    return result;
                });
                handHooksReady=true;
            } catch(Throwable t){Diagnostics.add("单手窗口接口不可用，旧模式保留："+Diagnostics.concise(t));}

            Diagnostics.add("Installed: "+config.method+"; no dependency on DisplayDevice.setProjectionLocked");
            log(Log.INFO,"伪单手模式","显示 Hook 已就绪，等待系统启动完成");
        }catch(Throwable t) {
            hooksReady=false;hookFailure=Diagnostics.concise(t);
            // Roll back partially installed behavior hooks; keep the independent diagnostic bootstrap.
            for(HookHandle handle:behaviorHooks)try{handle.unhook();}catch(Throwable ignored){}
            log(t);
        }
    }
}
