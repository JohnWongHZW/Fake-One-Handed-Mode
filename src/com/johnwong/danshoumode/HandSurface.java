package com.johnwong.danshoumode;

import android.graphics.Rect;
import android.view.SurfaceControl;

/** Own temporary container: original windowing-layer matrix/crop/radius stay untouched. */
final class HandSurface {
    private SurfaceControl leash,windowing,parent;
    private Object displayContent,lock,area;
    private int width,height,rotation,layer;
    private float density;
    private final BlankDecor decor=new BlankDecor();
    private volatile Placement placement;
    private volatile int generation;
    private static final class Placement {
        final float scale,radius;final int[] frame;
        Placement(int width,int height,float density,HandSettings settings){
            scale=settings.scale();radius=28f*density*scale;frame=Geometry.handFrame(width,height,scale,settings.left);
        }
    }

    void start(Object wm,HandSettings settings,float density) {
        lock=Reflect.getObjectField(wm,"mGlobalLock");
        synchronized(lock) {
            if(leash!=null)throw new IllegalStateException("上次单手窗口尚未恢复");
            displayContent=Reflect.callMethod(wm,"getDefaultDisplayContentLocked");
            Object info=Reflect.callMethod(displayContent,"getDisplayInfo");
            width=Reflect.getIntField(info,"logicalWidth");height=Reflect.getIntField(info,"logicalHeight");
            rotation=Reflect.getIntField(info,"rotation");
            if(width>=height)throw new IllegalStateException("请将手机竖屏后开启单手模式");
            Object policy=Reflect.getObjectField(displayContent,"mDisplayAreaPolicy");
            area=Reflect.callMethod(policy,"getWindowingArea");
            windowing=(SurfaceControl)Reflect.callMethod(displayContent,"getWindowingLayer");
            parent=(SurfaceControl)Reflect.callMethod(Reflect.callMethod(area,"getParent"),"getSurfaceControl");
            if(windowing==null||windowing==parent||!windowing.isValid()||parent==null||!parent.isValid())throw new IllegalStateException("窗口层父级不兼容");
            if(Reflect.getObjectField(area,"mLastRelativeToLayer")!=null||
                    Boolean.TRUE.equals(Reflect.callMethod(Reflect.getObjectField(area,"mSurfaceAnimator"),"hasLeash")))
                throw new IllegalStateException("窗口层正在进行系统动画，请稍后再试");
            this.density=density;Placement next=new Placement(width,height,density,settings);
            layer=Reflect.getIntField(area,"mLastLayer");
            Diagnostics.add("Hand surface: "+area.getClass().getName()+"; "+width+"x"+height+
                    "; rotation="+rotation+"; scale="+next.scale+"; layer="+layer);
            Object builder=Reflect.callMethod(wm,"makeSurfaceBuilder");
            Reflect.callMethod(builder,"setName","伪单手模式容器");Reflect.callMethod(builder,"setContainerLayer");
            Reflect.callMethod(builder,"setParent",parent);
            leash=(SurfaceControl)Reflect.callMethod(builder,"build");
            try{decor.create(wm,parent,width,height,density);}catch(Throwable t){Diagnostics.add("退出提示暂不可用："+Diagnostics.concise(t));}
            try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()) {
                t.setLayer(leash,layer).reparent(windowing,leash);Reflect.callMethod(t,"show",leash);
                setTransform(t,next);decor.place(t,next.frame[1],layer+1);t.apply();placement=next;generation++;
            }catch(Throwable error){stop();throw new IllegalStateException(error);}
        }
    }
    int[] frame(){Placement p=placement;return p==null?null:p.frame.clone();}
    int generation(){return generation;}
    int rotation(){return rotation;}
    boolean needsRestore(){return leash!=null;}
    boolean inside(float x,float y){Placement p=placement;return p!=null&&Geometry.insideRounded(x,y,p.frame,p.radius);}
    boolean owns(Object dc){return leash!=null&&dc==displayContent;}
    private void setTransform(SurfaceControl.Transaction t,Placement p) {
        Reflect.callMethod(t,"setMatrix",leash,p.scale,0f,0f,p.scale);t.setPosition(leash,p.frame[0],p.frame[1]);
        t.setCrop(leash,new Rect(0,0,width,height));Reflect.callMethod(t,"setCornerRadius",leash,p.radius/p.scale);
    }
    void update(HandSettings settings) {
        if(lock==null)throw new IllegalStateException("单手窗口尚未创建");
        synchronized(lock){
            if(leash==null)throw new IllegalStateException("单手窗口已经关闭");
            verify(displayContent);Placement next=new Placement(width,height,density,settings);
            try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
                setTransform(t,next);decor.place(t,next.frame[1],layer+1);t.apply();placement=next;generation++;
            }
        }
    }
    void verify(Object dc) {
        if(!owns(dc))return;
        Object info=Reflect.callMethod(dc,"getDisplayInfo");
        if(Reflect.getIntField(info,"logicalWidth")!=width||Reflect.getIntField(info,"logicalHeight")!=height||Reflect.getIntField(info,"rotation")!=rotation||
                Reflect.callMethod(dc,"getWindowingLayer")!=windowing||!leash.isValid())
            throw new IllegalStateException("显示布局变化，退出单手模式");
    }
    void stop() {
        if(lock==null)return;
        synchronized(lock){stopLocked();}
    }
    private void stopLocked() {
        if(leash==null)return;
        try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()) {
            if(windowing!=null&&windowing.isValid()) {
                if(parent==null||!parent.isValid())throw new IllegalStateException("窗口父级已失效，暂不移除容器");
                t.reparent(windowing,parent);
            }
            if(leash.isValid())Reflect.callMethod(t,"remove",leash);decor.remove(t);t.apply();
        }
        leash.release();decor.release();leash=null;windowing=null;parent=null;displayContent=null;placement=null;generation++;
    }
}
