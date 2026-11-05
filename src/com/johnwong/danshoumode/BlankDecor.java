package com.johnwong.danshoumode;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.view.Surface;
import android.view.SurfaceControl;

/** A small buffer layer outside the scaled window tree; it owns no input window. */
final class BlankDecor {
    private SurfaceControl control;
    private Surface surface;
    private float unit;
    private int displayWidth,width,height;
    void create(Object wm,SurfaceControl parent,int displayWidth,int displayHeight,float density) {
        this.displayWidth=displayWidth;
        // Fit even at the maximum 85% scale, keeping the decoration in the upper blank area.
        unit=Math.min(density,Math.min(displayWidth/360f,displayHeight*.14f/100f));
        width=Math.max(1,Math.round(320*unit));height=Math.max(1,Math.round(100*unit));
        try {
            Object builder=Reflect.callMethod(wm,"makeSurfaceBuilder");
            Reflect.callMethod(builder,"setName","伪单手模式提示与设置");Reflect.callMethod(builder,"setParent",parent);
            Reflect.callMethod(builder,"setBufferSize",width,height);Reflect.callMethod(builder,"setFormat",PixelFormat.TRANSLUCENT);
            control=(SurfaceControl)Reflect.callMethod(builder,"build");surface=new Surface(control);
            Canvas canvas=surface.lockCanvas(null);
            try {
                canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
                Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setTextAlign(Paint.Align.CENTER);
                paint.setColor(0xFFBDC8C0);paint.setTextSize(14*unit);
                canvas.drawText("双击空白处任意位置退出",width/2f,23*unit,paint);
                float left=width/2f-56*unit,top=42*unit;
                paint.setColor(0xFFD4EDDA);canvas.drawRoundRect(new RectF(left,top,left+112*unit,top+44*unit),22*unit,22*unit,paint);
                paint.setColor(0xFF256B47);paint.setTextSize(15*unit);canvas.drawText("设置",left+69*unit,70*unit,paint);
                float cx=left+28*unit,cy=64*unit;paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*unit);
                canvas.drawCircle(cx,cy,6*unit,paint);canvas.drawCircle(cx,cy,2*unit,paint);
                for(int i=0;i<8;i++){double angle=i*Math.PI/4;float dx=(float)Math.cos(angle),dy=(float)Math.sin(angle);
                    canvas.drawLine(cx+8*unit*dx,cy+8*unit*dy,cx+11*unit*dx,cy+11*unit*dy,paint);}
            } finally {surface.unlockCanvasAndPost(canvas);}
        } catch(Throwable t) {
            try(SurfaceControl.Transaction transaction=new SurfaceControl.Transaction()){remove(transaction);transaction.apply();}catch(Throwable ignored){}
            release();throw new IllegalStateException("空白区提示绘制失败",t);
        }
    }
    void place(SurfaceControl.Transaction t,int frameTop,int layer) {
        if(control==null)return;
        t.setLayer(control,layer).setPosition(control,(displayWidth-width)/2f,(frameTop-height)/2f);
        Reflect.callMethod(t,"show",control);
    }
    boolean buttonAt(float x,float y,int frameTop) {
        if(control==null)return false;
        float left=displayWidth/2f-56*unit,top=(frameTop-height)/2f+42*unit;
        return x>=left&&x<left+112*unit&&y>=top&&y<top+44*unit;
    }
    void remove(SurfaceControl.Transaction t){if(control!=null&&control.isValid())Reflect.callMethod(t,"remove",control);}
    void release(){if(surface!=null)surface.release();if(control!=null)control.release();surface=null;control=null;}
}
