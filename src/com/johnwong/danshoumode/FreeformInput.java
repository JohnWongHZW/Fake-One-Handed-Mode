package com.johnwong.danshoumode;

import android.graphics.Matrix;
import android.view.InputEvent;
import android.view.InputDevice;
import android.view.MotionEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import io.github.libxposed.api.XposedInterface.HookHandle;
import io.github.libxposed.api.XposedInterface.ExceptionMode;

/** Xiaomi's Shell decoration receiver owns its own physical input stream. */
final class FreeformInput {
    private static final String PREFIX="com.android.wm.shell.multitasking.";
    private static final ThreadLocal<FrameChannel.Frame> dispatchFrame=new ThreadLocal<>();
    static volatile android.app.Application clientApp;
    private FrameChannel.Frame stroke;
    private static final String RECEIVER = PREFIX+"miuimultiwinswitch.miuiwindowdecor.MulWinSwitchEventController$EventReceiver";
    void attach(Entry entry,ClassLoader loader) throws Exception {
        try {
            Class.forName(RECEIVER,false,loader);
        } catch(ClassNotFoundException deferred) {
            // Some builds load WM Shell from a plugin class loader after Application.onCreate.
            Class<?> base=Class.forName("android.view.InputEventReceiver",false,loader);
            Method delivery=base.getDeclaredMethod("dispatchInputEvent",int.class,InputEvent.class);
            final HookHandle[] probe={null};
            java.util.concurrent.atomic.AtomicBoolean tried=new java.util.concurrent.atomic.AtomicBoolean();
            probe[0]=entry.hook(delivery).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain->{
                Class<?> actual=chain.getThisObject().getClass();
                if(RECEIVER.equals(actual.getName()) && tried.compareAndSet(false,true)) {
                    try { install(entry,actual.getClassLoader()); }
                    catch(Exception|LinkageError error){ Entry.log(error); }
                    if(probe[0]!=null) probe[0].unhook();
                }
                return chain.proceed();
            });
            return;
        }
        install(entry,loader);
    }
    private void install(Entry entry,ClassLoader loader) throws Exception {
        Class<?> receiver=Class.forName(RECEIVER,false,loader);
        Method displayId=InputEvent.class.getMethod("getDisplayId");
        Method input=receiver.getDeclaredMethod("onInputEvent",InputEvent.class);
        List<HookHandle> hooks=new ArrayList<>();
        try {
            for(String path:new String[]{"miuifreeform.MiuiFreeformModeGestureHandler","miuimultiwinswitch.miuiwindowdecor.MulWinSwitchAnimStatus"}) {
                Class<?> type=Class.forName(PREFIX+path,false,loader);
                Method exclusion=type.getDeclaredMethod("isInExcludeRegion",float.class,float.class);
                hooks.add(entry.hook(exclusion).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain->{
                    FrameChannel.Frame f=dispatchFrame.get();
                    if(f==null) return chain.proceed();
                    return chain.proceed(new Object[]{(Float)chain.getArg(0)*f.scale+f.x,(Float)chain.getArg(1)*f.scale+f.y});
                }));
                for(Method m:type.getDeclaredMethods()) if(m.getName().equals("handleMotionEvents")) entry.deoptimize(m);
            }
            entry.deoptimize(input);
            hooks.add(entry.hook(input).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain->{
                InputEvent inputEvent=(InputEvent)chain.getArg(0);
                if(!(inputEvent instanceof MotionEvent) || (Integer)displayId.invoke(inputEvent)!=0 ||
                        !inputEvent.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) return chain.proceed();
                MotionEvent e=(MotionEvent)inputEvent;
                int action=e.getActionMasked();
                if(action==MotionEvent.ACTION_DOWN) {
                    FrameChannel.Frame f=FrameChannel.current;
                    stroke=f.contains(e.getRawX(),e.getRawY())?f:null;
                }
                FrameChannel.Frame f=stroke;
                if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL) stroke=null;
                if(f==null) return chain.proceed();
                Matrix inverse=new Matrix();
                inverse.setValues(Geometry.inverseHandMatrix(f.scale,f.x,f.y));
                // This receiver finishes the original input event by sequence number.
                e.transform(inverse);
                if(clientApp!=null) FrameChannel.signal(clientApp,3);
                FrameChannel.Frame previous=dispatchFrame.get();
                dispatchFrame.set(f);
                try { return chain.proceed(); }
                finally { if(previous==null) dispatchFrame.remove(); else dispatchFrame.set(previous); }
            }));
        } catch(Exception|LinkageError error) {
            for(HookHandle h:hooks) h.unhook();
            throw error;
        }
    }
}
