package com.johnwong.danshoumode;

import android.content.*;
import android.os.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class AppBridge {
    interface Reply {void accept(Bundle state);}
    static void request(Context context,Intent intent,Reply callback) {
        Handler h=new Handler(Looper.getMainLooper());AtomicBoolean delivered=new AtomicBoolean();
        Runnable timeout=()->{if(delivered.compareAndSet(false,true)){Bundle b=new Bundle();b.putString("message","模块未连接：请启用「系统框架（system）」作用域并重启");callback.accept(b);}};
        if(android.os.Process.myUid()/100000!=0){timeout.run();return;}
        h.postDelayed(timeout,3500);
        try {
            // The Android system context owns this receiver. Its broadcast package is
            // still "android"; that is separate from modern Xposed's "system" scope.
            intent.setAction(Protocol.ACTION).setPackage("android");
            context.sendOrderedBroadcast(intent,null,new BroadcastReceiver(){
                @Override public void onReceive(Context c,Intent i) {
                    if(!delivered.compareAndSet(false,true))return;h.removeCallbacks(timeout);
                    Bundle b=getResultExtras(false);
                    if(getResultCode()!=1||b==null){b=new Bundle();b.putString("message","模块未连接：请启用「系统框架（system）」作用域并重启");}
                    else b.putBoolean("connected",true);
                    callback.accept(b);
                }
            },h,0,null,null);
        }catch(RuntimeException e){timeout.run();}
    }
    static void request(Context context,String op,Reply callback){request(context,new Intent().putExtra("op",op),callback);}
}
