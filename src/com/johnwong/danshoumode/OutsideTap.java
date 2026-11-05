package com.johnwong.danshoumode;

import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.content.Context;
import java.lang.reflect.Proxy;

final class OutsideTap {
    private Object listener,wm;
    private float downX,downY,lastX,lastY,touchSlop2,doubleSlop2;
    private long downTime,lastUp=-1;
    private int region,generation,doubleTimeout,tapTimeout;
    private boolean secondTap;
    private void reset(){region=0;lastUp=-1;secondTap=false;}
    private static float distance2(float x,float y,float a,float b){return (x-a)*(x-a)+(y-b)*(y-b);}
    void register(Object windowManager,ClassLoader loader,Controller controller,Context context) {
        ViewConfiguration configuration=ViewConfiguration.get(context);
        touchSlop2=configuration.getScaledTouchSlop()*configuration.getScaledTouchSlop();
        doubleSlop2=configuration.getScaledDoubleTapSlop()*configuration.getScaledDoubleTapSlop();
        doubleTimeout=ViewConfiguration.getDoubleTapTimeout();tapTimeout=Math.min(350,ViewConfiguration.getLongPressTimeout());reset();
        Class<?> type=Reflect.findClass("android.view.WindowManagerPolicyConstants$PointerEventListener",loader);
        wm=windowManager;
        listener=Proxy.newProxyInstance(loader,new Class<?>[]{type},(proxy,method,args)->{
            if(method.getName().equals("equals"))return proxy==args[0];
            if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
            if(method.getName().equals("toString"))return "DanshouOutsideTap";
            if(method.getName().equals("onPointerEvent")&&args!=null&&args.length>0&&args[0] instanceof MotionEvent) {
                // This monitor is registered only on display 0. Keep all callback failures
                // inside the module instead of propagating them to the system input thread.
                try {
                MotionEvent e=(MotionEvent)args[0];
                if(!controller.isHand()){reset();return null;}
                int currentGeneration=controller.handSurface.generation();
                if(generation!=currentGeneration){reset();generation=currentGeneration;}
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN) {
                    downX=e.getX();downY=e.getY();downTime=e.getEventTime();
                    region=controller.handSurface.inside(downX,downY)?0:controller.handSurface.settingsAt(downX,downY)?2:1;
                    secondTap=region==1&&lastUp>=0&&downTime-lastUp>=0&&downTime-lastUp<=doubleTimeout&&distance2(downX,downY,lastX,lastY)<=doubleSlop2;
                    if(!secondTap)lastUp=-1;
                }else if(e.getActionMasked()==MotionEvent.ACTION_UP) {
                    boolean tap=e.getEventTime()-downTime>=0&&e.getEventTime()-downTime<tapTimeout&&distance2(e.getX(),e.getY(),downX,downY)<=touchSlop2;
                    if(tap&&region==2&&controller.handSurface.settingsAt(e.getX(),e.getY())){reset();controller.openSettings();}
                    else if(tap&&region==1&&!controller.handSurface.inside(e.getX(),e.getY())&&!controller.handSurface.settingsAt(e.getX(),e.getY())){
                        if(secondTap){reset();controller.exitFromOutside();}
                        else{lastUp=e.getEventTime();lastX=e.getX();lastY=e.getY();region=0;}
                    }else reset();
                }else if(e.getActionMasked()==MotionEvent.ACTION_CANCEL||e.getPointerCount()>1||
                        (e.getActionMasked()==MotionEvent.ACTION_MOVE&&
                                distance2(e.getX(),e.getY(),downX,downY)>touchSlop2))reset();
                }catch(Throwable t){reset();controller.fail(t);}
            }
            return null;
        });
        Reflect.callMethod(wm,"registerPointerEventListener",listener,0);
    }
    void unregister(){if(listener!=null){Reflect.callMethod(wm,"unregisterPointerEventListener",listener,0);listener=null;}reset();}
}
