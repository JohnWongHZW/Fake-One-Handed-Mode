package com.johnwong.danshoumode;

import android.app.KeyguardManager;
import android.app.WallpaperManager;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.util.*;
import java.util.concurrent.*;
import io.github.libxposed.api.XposedInterface.HookHandle;
import io.github.libxposed.api.XposedInterface.ExceptionMode;

/** Paint a wallpaper-only backdrop in the local coordinates of the unlocked shade / overview. */
final class Backdrop {
    private final Entry entry;
    private final Context app;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Executor worker=Executors.newSingleThreadExecutor();
    private final Map<Class<?>,Boolean> types=new WeakHashMap<>();
    private final Set<View> drawn=Collections.newSetFromMap(new WeakHashMap<>());
    private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
    private HookHandle hook;
    private Bitmap wallpaper;
    private int generation;
    private long lastAttempt;
    private boolean loading, reportedDraw;

    Backdrop(Entry entry,Context app){ this.entry=entry; this.app=app; }
    void start() {
        IntentFilter f=new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED);
        f.addAction(Intent.ACTION_SCREEN_OFF); f.addAction(Intent.ACTION_USER_PRESENT);
        app.registerReceiver(new BroadcastReceiver(){
            @Override public void onReceive(Context c,Intent i){ resetImage(); changed(); }
        },f,null,main,Context.RECEIVER_NOT_EXPORTED);
        FrameChannel.listen(app,this::changed);
    }
    private void resetImage() {
        generation++; loading=false; wallpaper=null; lastAttempt=0;
        invalidateTargets();
    }
    private void invalidateTargets(){ for(View view:drawn) view.invalidate(); }
    private void changed() {
        if(FrameChannel.current.mode==0) {
            if(hook!=null){ hook.unhook(); hook=null; }
            resetImage(); drawn.clear(); return;
        }
        try {
            if(hook==null) hook=entry.hook(ViewGroup.class.getDeclaredMethod("dispatchDraw",Canvas.class))
                    .setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain->{
                try { paintBehindChildren((ViewGroup)chain.getThisObject(),(Canvas)chain.getArg(0)); }
                catch(RuntimeException err){ if (wallpaper!=null) {wallpaper=null;Entry.log(err);} }
                return chain.proceed();
            });
        } catch(ReflectiveOperationException e){ Entry.log(e); }
        invalidateTargets();
    }
    private boolean target(Class<?> type) {
        Boolean cached=types.get(type);
        if(cached!=null) return cached;
        boolean match=false;
        for(Class<?> c=type;c!=null && c!=ViewGroup.class;c=c.getSuperclass()) {
            String name=c.getName();
            if(FrameChannel.HOME.equals(app.getPackageName())) {
                match=name.equals("com.miui.home.recents.views.RecentsView");
            } else if(name.startsWith("com.android.systemui.") || name.startsWith("miui.systemui.")) {
                String simple=c.getSimpleName();
                match=simple.equals("MiuiNotificationPanelView") || simple.equals("NotificationPanelView")
                        || simple.equals("ControlCenterContentView") || simple.equals("ControlCenterPanelView") || simple.equals("ControlCenterWindowView");
            }
            if(match) break;
        }
        types.put(type,match); return match;
    }
    private void paintBehindChildren(ViewGroup view, Canvas canvas) {
        if(FrameChannel.current.mode==0 || !target(view.getClass()) || !view.isShown() ||
                view.getDisplay()==null || view.getDisplay().getDisplayId()!=0 ||
                view.getHeight()<app.getResources().getDisplayMetrics().heightPixels*.6f) return;
        if(app.getSystemService(KeyguardManager.class).isKeyguardLocked()) return;
        drawn.add(view);
        if(wallpaper==null) { requestImage(); return; }
        if(!reportedDraw){ reportedDraw=true; FrameChannel.signal(app,FrameChannel.UI.equals(app.getPackageName())?4:5); entry.log(android.util.Log.INFO,"伪单手模式","已绘制小窗壁纸："+view.getClass().getName()); }
        int w=view.getWidth(), h=view.getHeight();
        float[] r=Geometry.wallpaperBounds(wallpaper.getWidth(),wallpaper.getHeight(),w,h);
        canvas.drawBitmap(wallpaper,null,new RectF(r[0],r[1],r[2],r[3]),paint);
    }
    private void requestImage() {
        long now=SystemClock.elapsedRealtime();
        if(loading || (lastAttempt!=0 && now-lastAttempt<3000)) return;
        loading=true; lastAttempt=now;
        final int revision=generation;
        worker.execute(()->{
            Bitmap result=null;
            try { result=readWallpaper(); } catch(Exception|OutOfMemoryError e){ Entry.log(e); }
            final Bitmap image=result;
            main.post(()->{
                if(generation!=revision) return;
                wallpaper=image; loading=false;
                if(image==null) FrameChannel.signal(app,6);
                invalidateTargets();
            });
        });
    }
    private Bitmap readWallpaper() throws Exception {
        WallpaperManager manager=app.getSystemService(WallpaperManager.class);
        Bitmap source=null;
        try { if(manager.getWallpaperInfo()==null) {
            try(ParcelFileDescriptor fd=manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)) {
                if(fd!=null) {
                    BitmapFactory.Options o=new BitmapFactory.Options();
                    o.inJustDecodeBounds=true; BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(),null,o);
                    o.inJustDecodeBounds=false; o.inSampleSize=1;
                    while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>512) o.inSampleSize*=2;
                    source=BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(),null,o);
                }
            }
        } } catch(SecurityException ignored){ /* Try the window manager's wallpaper-only API. */ }
        if(source==null) {
            Class<?> global=Reflect.findClass("android.view.WindowManagerGlobal",app.getClassLoader());
            Object wm=Reflect.callStaticMethod(global,"getWindowManagerService");
            source=(Bitmap)Reflect.callMethod(wm,"screenshotWallpaper");
        }
        if(source==null) return null;
        float s=64f/Math.max(source.getWidth(),source.getHeight());
        Bitmap small=Bitmap.createScaledBitmap(source,Math.max(1,Math.round(source.getWidth()*s)),
                Math.max(1,Math.round(source.getHeight()*s)),true);
        Bitmap soft=small.copy(Bitmap.Config.ARGB_8888,true);
        int w=soft.getWidth(),h=soft.getHeight();
        int[] src=new int[w*h], out=new int[w*h];
        soft.getPixels(src,0,w,0,0,w,h);
        for(int y=0;y<h;y++) for(int x=0;x<w;x++){
            int r=0,g=0,b=0,n=0;
            for(int j=Math.max(0,y-3);j<=Math.min(h-1,y+3);j++)
                for(int i=Math.max(0,x-3);i<=Math.min(w-1,x+3);i++){
                    int p=src[j*w+i]; r+=Color.red(p);g+=Color.green(p);b+=Color.blue(p);n++;
                }
            out[y*w+x]=Color.rgb(r/n,g/n,b/n);
        }
        soft.setPixels(out,0,w,0,0,w,h);
        if(small!=source) small.recycle();
        source.recycle();
        return soft;
    }
}
