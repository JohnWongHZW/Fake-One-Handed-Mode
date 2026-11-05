package com.johnwong.danshoumode;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;

/** Always exposes diagnostics after boot, even if display hook resolution failed. */
final class Bootstrap {
    static void start(ClassLoader loader) {
        HandlerThread thread=new HandlerThread("LubenweiBootstrap");thread.start();
        Handler handler=new Handler(thread.getLooper());
        handler.post(new Runnable() {
            int errors,serviceWait;
            @Override public void run() {
                try {
                    String boot=(String)Reflect.callStaticMethod(Reflect.findClass("android.os.SystemProperties",loader),"get","sys.boot_completed","0");
                    if(!"1".equals(boot)){handler.postDelayed(this,1500);return;}
                    Object activityThread=Reflect.callStaticMethod(Reflect.findClass("android.app.ActivityThread",loader),"currentActivityThread");
                    Context context=(Context)Reflect.callMethod(activityThread,"getSystemContext");
                    if(Entry.displayManager==null && serviceWait++<20){handler.postDelayed(this,500);return;}
                    Controller c=Entry.controller;
                    if(c==null && Entry.displayManager!=null) {
                        try {c=new Controller(context,Entry.displayManager,loader);Entry.controller=c;c.start();}
                        catch(Throwable t){Entry.hookFailure=Diagnostics.concise(t);Entry.log(t);}
                    }else if(c==null)Diagnostics.add("DisplayManagerService was not captured");
                    Handler receiverHandler=c!=null?c.handler:handler;
                    context.registerReceiver(new BroadcastReceiver(){
                        @Override public void onReceive(Context ctx,Intent intent) {
                            boolean clearLogs="clear_logs".equals(intent.getStringExtra("op"));
                            if(clearLogs)Diagnostics.clear();
                            Bundle state;
                            Controller current=Entry.controller;
                            if(current!=null)state=current.command(intent);
                            else {
                                state=new Bundle();state.putBoolean("active",false);state.putBoolean("ready",false);
                                state.putString("message","模块已加载，显示适配失败："+Entry.hookFailure+"。请复制诊断。");
                            }
                            state.putBoolean("logsCleared",clearLogs);
                            state.putString("diagnostics",Diagnostics.report());
                            setResultCode(1);setResultExtras(state);
                        }
                    },new IntentFilter(Protocol.ACTION),Protocol.PERMISSION,receiverHandler,Context.RECEIVER_EXPORTED);
                    Diagnostics.add("Control/diagnostics receiver registered");
                }catch(Throwable t) {
                    if(errors++<3)Entry.log(t);
                    if(errors<40)handler.postDelayed(this,1500);
                }
            }
        });
    }
}
