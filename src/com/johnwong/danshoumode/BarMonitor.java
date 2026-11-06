package com.johnwong.danshoumode;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.res.Resources;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import java.lang.reflect.Proxy;

final class BarMonitor {
    private static BarMonitor installed;
    private final BarSwipe swipe = new BarSwipe();
    private final Context context;
    private final Controller controller;
    private final Object wm;
    private Object monitor, dispatcher;
    private int generation;
    private boolean reportedError;

    private BarMonitor(Context context, Object wm, Controller controller) {
        this.context = context; this.wm = wm; this.controller = controller;
    }
    static void install(Context context, Object wm, ClassLoader loader, Controller controller) throws Exception {
        if (installed != null) return;
        BarMonitor owner = new BarMonitor(context, wm, controller);
        Class<?> listenerType = Reflect.findClass("android.view.WindowManagerPolicyConstants$PointerEventListener", loader);
        Object listener = Proxy.newProxyInstance(loader, new Class<?>[]{listenerType}, (self, method, args) -> {
            switch (method.getName()) {
                case "equals": return self == args[0];
                case "hashCode": return System.identityHashCode(self);
                case "toString": return "DanShouNavigation";
                case "onPointerEvent":
                    try { owner.onTouch((MotionEvent) args[0]); }
                    catch (RuntimeException error) {
                        owner.swipe.cancel();
                        if (!owner.reportedError) { owner.reportedError = true; Entry.log(error); }
                    }
            }
            return null;
        });
        try {
            Object input = context.getSystemService(Context.INPUT_SERVICE);
            owner.monitor = Reflect.callMethod(input, "monitorGestureInput", "DanShouNavigation", 0);
            Object channel = Reflect.callMethod(owner.monitor, "getInputChannel");
            Class<?> type = Reflect.findClass("com.android.server.wm.PointerEventDispatcher", loader);
            owner.dispatcher = type.getConstructor(Reflect.findClass("android.view.InputChannel", loader)).newInstance(channel);
            Reflect.callMethod(owner.dispatcher, "registerInputEventListener", listener);
        } catch (Exception error) {
            if (owner.dispatcher != null) Reflect.callMethod(owner.dispatcher, "dispose");
            if (owner.monitor != null) Reflect.callMethod(owner.monitor, "dispose");
            owner.dispatcher = owner.monitor = null;
            Reflect.callMethod(wm, "registerPointerEventListener", listener, 0);
            Diagnostics.add("导航手势使用系统观察接口：" + Diagnostics.concise(error));
        }
        installed = owner;
        Diagnostics.add("小白条手势已就绪");
    }
    private void onTouch(MotionEvent e) {
        if (controller.isLegacy() || e.getPointerCount()!=1 || !e.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) {
            swipe.cancel(); return;
        }
        int action=e.getActionMasked();
        float x=e.getRawX(), y=e.getRawY();
        if (action == MotionEvent.ACTION_DOWN) {
            swipe.cancel();
            Resources resources=context.getResources();
            if (y < resources.getDisplayMetrics().heightPixels * .85f) return;
            int mode=resources.getIdentifier("config_navBarInteractionMode","integer","android");
            if ((mode==0 || resources.getInteger(mode)!=2) &&
                    Settings.Global.getInt(context.getContentResolver(),"force_fsg_nav_bar",0)!=1) return;
            if (context.getSystemService(KeyguardManager.class).isKeyguardLocked() ||
                    !context.getSystemService(PowerManager.class).isInteractive() ||
                    (Integer)Reflect.callStaticMethod(ActivityManager.class,"getCurrentUser")!=0) return;
            int[] frame;
            boolean active=controller.isHand();
            generation=controller.handSurface.generation();
            if(active) frame=controller.handSurface.frame();
            else synchronized(Reflect.getObjectField(wm,"mGlobalLock")) {
                Object dc=Reflect.callMethod(wm,"getDefaultDisplayContentLocked");
                Object info=Reflect.callMethod(dc,"getDisplayInfo");
                frame=new int[]{0,0,Reflect.getIntField(info,"logicalWidth"),Reflect.getIntField(info,"logicalHeight")};
                if(frame[2]>=frame[3]) return;
            }
            if(frame==null) return;
            int dim=resources.getIdentifier("navigation_bar_gesture_height","dimen","android");
            int band=dim==0?Math.round(24*resources.getDisplayMetrics().density):resources.getDimensionPixelSize(dim);
            if (active) band=Math.round(band*controller.activeHandScale());
            swipe.begin(x,y,e.getEventTime(),frame,band,ViewConfiguration.get(context).getScaledTouchSlop(),active);
        } else if(generation!=controller.handSurface.generation()) swipe.cancel();
        else if(action==MotionEvent.ACTION_MOVE) {
            if(swipe.move(x,y,e.getEventTime()) && monitor!=null) Reflect.callMethod(monitor,"pilferPointers");
        } else if(action==MotionEvent.ACTION_UP) {
            int result=swipe.finish(x,y,e.getEventTime());
            if(result!=0) controller.barSwipe(result==3,result==1,generation);
        } else swipe.cancel();
    }
}
