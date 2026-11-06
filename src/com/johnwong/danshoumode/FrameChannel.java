package com.johnwong.danshoumode;

import android.content.*;
import android.os.*;

/** Read-only, in-memory geometry updates. Commands still need the module signature. */
final class FrameChannel {
    static final String UI = "com.android.systemui", HOME = "com.miui.home";
    private static final String UPDATE = Protocol.PACKAGE+".FRAME", QUERY = UPDATE+".QUERY";
    private static final String[] SIGNALS={"","系统界面已连接","小米桌面已连接","自由小窗触控已换算",
            "通知面板已绘制壁纸","最近任务已绘制壁纸","壁纸暂不可读取"};
    private static final java.util.BitSet sent=new java.util.BitSet();
    static synchronized void signal(Context app,int code) {
        if(code<=0 || code>=SIGNALS.length || sent.get(code)) return;
        try {
            app.sendBroadcast(new Intent(QUERY).setPackage("android").putExtra("signal",code));
            sent.set(code);
        } catch(RuntimeException e){Entry.log(e);}
    }
    static volatile Frame current = new Frame(new Bundle());
    static final class Frame {
        final long epoch;
        final int mode, x, y, right, bottom;
        final float scale;
        Frame(Bundle b) {
            epoch=b.getLong("epoch"); mode=b.getInt("mode"); x=b.getInt("x"); y=b.getInt("y");
            right=b.getInt("right"); bottom=b.getInt("bottom"); scale=b.getFloat("scale",1f);
        }
        boolean contains(float px,float py) {
            return mode==1 && scale>=.4f && scale<=.85f && px>=x && px<right && py>=y && py<bottom;
        }
    }
    private static Bundle snapshot(Controller c) {
        Bundle b=new Bundle();
        b.putLong("epoch",SystemClock.elapsedRealtimeNanos());
        b.putInt("mode",c.isHand()?1:c.isLegacy()?2:0);
        if(c.isHand()) {
            int[] r=c.handSurface.frame();
            if(r==null){ b.putInt("mode",0);return b; }
            b.putInt("x",r[0]); b.putInt("y",r[1]); b.putInt("right",r[2]); b.putInt("bottom",r[3]);
            b.putFloat("scale",c.activeHandScale());
        }
        return b;
    }
    static void serve(Context context, Handler handler, Controller c) {
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent i) {
                int code=i.getIntExtra("signal",0);
                if(code>0 && code<SIGNALS.length) Diagnostics.add(SIGNALS[code]);
                if(isOrderedBroadcast()) setResultExtras(snapshot(c));
            }
        },new IntentFilter(QUERY),"android.permission.MANAGE_ACTIVITY_TASKS",handler,Context.RECEIVER_EXPORTED);
    }
    static void publish(Context context,Controller c) {
        Bundle b=snapshot(c);
        for(String pkg:new String[]{UI,HOME})
            context.sendBroadcast(new Intent(UPDATE).setPackage(pkg).putExtras(b).addFlags(Intent.FLAG_RECEIVER_FOREGROUND));
    }
    static void listen(Context app, Runnable changed) {
        app.registerReceiver(new BroadcastReceiver(){
            @Override public void onReceive(Context ignored,Intent i){ accept(i.getExtras(),changed); signal(app,UI.equals(app.getPackageName())?1:2); }
        },new IntentFilter(UPDATE),"android.permission.MANAGE_USERS",null,Context.RECEIVER_EXPORTED);
        app.sendOrderedBroadcast(new Intent(QUERY).setPackage("android"),null,new BroadcastReceiver(){
            @Override public void onReceive(Context ignored,Intent i){ accept(getResultExtras(false),changed); }
        },null,0,null,null);
    }
    private static void accept(Bundle b,Runnable changed) {
        if(b==null) return;
        Frame frame=new Frame(b);
        if(frame.epoch<=current.epoch) return;
        current=frame;
        changed.run();
    }
}
