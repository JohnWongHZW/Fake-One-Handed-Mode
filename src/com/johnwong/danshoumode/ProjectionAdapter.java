package com.johnwong.danshoumode;

import android.graphics.Rect;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Runs AFTER normal logical-display configuration, before DMS builds input viewports. */
final class ProjectionAdapter {
    private final Field orientation,logicalFrame,physicalFrame;
    private final Method project,token;
    ProjectionAdapter(Class<?> deviceClass,Class<?> transactionClass,ClassLoader loader) {
        orientation=Reflect.findField(deviceClass,"mCurrentOrientation");
        logicalFrame=Reflect.findField(deviceClass,"mCurrentLayerStackRect");
        physicalFrame=Reflect.findField(deviceClass,"mCurrentDisplayRect");
        if(orientation.getType()!=int.class||logicalFrame.getType()!=Rect.class||physicalFrame.getType()!=Rect.class)
            throw new IllegalStateException("显示缓存字段类型不兼容");
        token=Reflect.findMethodExact(deviceClass,"getDisplayTokenLocked");
        project=Reflect.findMethodExact(transactionClass,"setDisplayProjection",
                Reflect.findClass("android.os.IBinder",loader),int.class,Rect.class,Rect.class);
    }
    void apply(Object device,Object transaction,int nativeWidth,int nativeHeight) throws Exception {
        Rect source=(Rect)logicalFrame.get(device);
        if(source==null||source.isEmpty())throw new IllegalStateException("系统尚未提供有效逻辑画面");
        int[] r=Geometry.fitLeftThird(nativeWidth,nativeHeight,source.width(),source.height());
        Rect destination=new Rect(r[0],r[1],r[2],r[3]);
        Object displayToken=token.invoke(device);
        if(displayToken==null)throw new IllegalStateException("主屏显示 token 为空");
        // No new transaction, native numeric transaction IDs or hidden compositor settings.
        // Reuse the system's existing transaction. Keep source rectangle untouched.
        project.invoke(transaction,displayToken,1,new Rect(source),destination);
        orientation.setInt(device,1);
        physicalFrame.set(device,new Rect(destination));
    }
    void verify(Object device,Object viewport,int nativeWidth,int nativeHeight) throws Exception {
        Rect physical=(Rect)Reflect.getObjectField(viewport,"physicalFrame");
        Rect logical=(Rect)Reflect.getObjectField(viewport,"logicalFrame");
        if(Reflect.getIntField(viewport,"orientation")!=1||!physical.equals(physicalFrame.get(device))||
                !logical.equals(logicalFrame.get(device))||physical.left!=0||physical.top<0||physical.isEmpty()||
                physical.right>nativeHeight/3||physical.bottom>nativeWidth)
            throw new IllegalStateException("显示与触控映射不一致，自动退出");
    }
}
