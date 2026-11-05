package com.johnwong.danshoumode;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import java.util.List;

final class Controller {
    private static final int MUSIC = AudioManager.STREAM_MUSIC;
    private static final long MAX_SESSION_MS = 2 * 60 * 60 * 1000;
    private final Context context;
    private final Object dms, sync;
    private final ClassLoader loader;
    final Handler handler;
    private AudioManager audio;
    private Object windowManager;
    private final AtomicFile journal = new AtomicFile(new File("/data/system/danshoumode-lubenwei-audio.properties"));
    private Properties saved;
    private boolean ready, failed, initialized;
    private String message = "等待系统启动";
    private long expires, keyStart,lastSettingsOpen;
    private int keys;
    volatile boolean active;
    volatile int mode; // 0 off, 1 one-handed, 2 legacy
    final HandSurface handSurface = new HandSurface();
    private final OutsideTap outsideTap = new OutsideTap();
    private final HandSettings handSettings = new HandSettings();
    private boolean handReady;
    volatile Object device;
    volatile int nativeWidth, nativeHeight;
    volatile long projections, viewports;

    Controller(Context c, Object service, ClassLoader cl) {
        context = c; dms = service; loader = cl;
        sync = Reflect.getObjectField(dms, "mSyncRoot");
        HandlerThread thread = new HandlerThread("LubenweiModeWatchdog");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    void start() { handler.post(this::waitForBoot); }

    Bundle command(String op) { return command(new Intent(Protocol.ACTION).putExtra("op", op)); }

    Bundle command(Intent intent) {
        String op=intent.getStringExtra("op");
        boolean configSaved=false;
        try {
            if ("toggle".equals(op)) { if (isLegacy()) stop("已恢复"); else { if(active)stop("正在切换"); enable(); } }
            else if ("hand_toggle".equals(op)) { if(isHand())stop("已退出单手模式");else {if(active)stop("正在切换");enableHand();} }
            else if ("save_hand".equals(op)) { saveHand(intent);configSaved=true; }
            else if ("stop".equals(op)) stop("已恢复");
        } catch (Throwable t) {
            if("save_hand".equals(op))message="配置更新失败："+Diagnostics.concise(t);
            else stop("操作失败：" + Diagnostics.concise(t));
            Entry.log(t);
        }
        Bundle state = new Bundle();
        state.putBoolean("configSaved",configSaved);
        state.putBoolean("active", isLegacy());
        state.putBoolean("handActive", isHand());
        state.putBoolean("handReady", handReady && ready && !failed);
        state.putString("side", handSettings.left ? "left" : "right");
        state.putInt("percent", handSettings.percent);
        state.putBoolean("ready", (ready && !failed && saved == null) || active);
        state.putBoolean("pendingAudio", !active && saved != null);
        state.putString("message", !Entry.hooksReady ? "显示适配失败：" + Entry.hookFailure + "。请复制诊断。" : message);
        state.putString("runtime", "active=" + active + "; projections=" + projections + "; viewports=" + viewports +
                "; observed=" + Entry.projectionSeen + "/" + Entry.viewportSeen);
        return state;
    }
    private void saveHand(Intent intent) throws Exception {
        HandSettings next=handSettings.changed(intent),before=handSettings.copy();boolean updating=isHand();
        if(updating)try{handSurface.update(next);}catch(Throwable t){stop("比例调整失败，已退出单手模式");throw new IllegalStateException(t);}
        try{handSettings.save(next);}catch(Exception t){
            if(updating)try{handSurface.update(before);}catch(Throwable rollback){stop("比例恢复失败，已退出单手模式");Entry.log(rollback);}
            throw t;
        }
        message=updating?"比例已更新，当前小窗即时生效":"配置已保存，快捷开关将使用此配置";
        Diagnostics.add("单手配置已保存："+(next.left?"左手":"右手")+" "+next.percent+"%");
    }

    private void waitForBoot() {
        try {
            String boot = (String) Reflect.callStaticMethod(
                    Reflect.findClass("android.os.SystemProperties", loader), "get", "sys.boot_completed", "0");
            if (!"1".equals(boot)) { handler.postDelayed(this::waitForBoot, 2000); return; }
            initialize();
        } catch (Throwable t) { Entry.log(t); }
    }

    private void initialize() {
        if (initialized) return;
        initialized = true;
        try {
            audio = context.getSystemService(AudioManager.class);
            windowManager = Reflect.callStaticMethod(
                    Reflect.findClass("android.view.WindowManagerGlobal", loader), "getWindowManagerService");
            Reflect.findMethodExact(windowManager.getClass(), "updateRotation", boolean.class, boolean.class);
            Reflect.findMethodExact(audio.getClass(), "getDevicesForStream", int.class);
            Reflect.findMethodExact(audio.getClass(), "getLastAudibleStreamVolume", int.class);
            Reflect.findMethodExact(audio.getClass(), "getDevicesForAttributes", AudioAttributes.class);
            ready = Entry.hooksReady;
            failed = !ready;
            message = ready ? "已连接 · 选择左右手与比例后开启" : "显示适配失败：" + Entry.hookFailure;
            handSettings.load();
            handReady=Entry.handHooksReady;
        } catch (Throwable t) { failed = true; message = "系统接口不兼容：" + Diagnostics.concise(t); Entry.log(t); }
        try { loadJournal(); restoreAudio(); }
        catch (Throwable t) { ready = false; message = "音量备份读取失败，请勿开启：" + t.getClass().getSimpleName(); Entry.log(t); }
        try {
            IntentFilter safety = new IntentFilter();
            safety.addAction(Intent.ACTION_SCREEN_OFF);
            safety.addAction("android.intent.action.USER_SWITCHED");
            safety.addAction(Intent.ACTION_SHUTDOWN);
            context.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    String action=i.getAction();
                    if(Intent.ACTION_SCREEN_OFF.equals(action)) {
                        if(isHand())stop("已熄屏，退出单手模式");
                        // The legacy mode intentionally stays active, including media mute.
                    } else if("android.intent.action.USER_SWITCHED".equals(action)) {
                        stop("已切换用户，退出当前模式");
                    } else if(Intent.ACTION_SHUTDOWN.equals(action)) {
                        stop("正在关机，退出当前模式");
                    }
                }
            }, safety, null, handler, Context.RECEIVER_EXPORTED);
            handler.post(tick);
        } catch (Throwable t) { ready = false; Entry.log(t); }
    }

    private void enable() throws Exception {
        if (!ready || failed) throw new IllegalStateException("模块未就绪或本次开机已发生兼容错误");
        if (saved != null) { restoreAudio(); if (saved != null) throw new IllegalStateException("请恢复到之前的音频输出设备"); }
        PowerManager pm = context.getSystemService(PowerManager.class);
        KeyguardManager km = context.getSystemService(KeyguardManager.class);
        if (!pm.isInteractive() || km.isKeyguardLocked()) throw new IllegalStateException("请先解锁手机");
        if (!Entry.projectionSeen || !Entry.viewportSeen) throw new IllegalStateException("尚未观察到显示和触控接口");
        synchronized (sync) {
            Object mapper = Reflect.getObjectField(dms, "mLogicalDisplayMapper");
            Object logical = Reflect.callMethod(mapper, "getDisplayLocked", 0);
            device = Reflect.callMethod(logical, "getPrimaryDisplayDeviceLocked");
            Object info = Reflect.callMethod(device, "getDisplayDeviceInfoLocked");
            nativeWidth = Reflect.getIntField(info, "width");
            nativeHeight = Reflect.getIntField(info, "height");
            int type = Reflect.getIntField(info, "type");
            int rotation = Reflect.getIntField(info, "rotation");
            if (type != 1 || rotation != 0) throw new IllegalStateException("仅支持自然竖屏、无额外面板旋转的内置屏");
            Geometry.fitLeftThird(nativeWidth, nativeHeight, nativeWidth, nativeHeight);
        }
        if (audio.isVolumeFixed()) throw new IllegalStateException("当前输出设备不允许调整音量");
        int route = route();
        // A single output route can be restored unambiguously. Do not alter multi-device playback.
        if (route == 0 || Integer.bitCount(route) != 1) throw new IllegalStateException("当前音频输出为多设备或无法识别");
        Properties snapshot = new Properties();
        snapshot.setProperty("version", "1");
        snapshot.setProperty("route", Integer.toString(route));
        snapshot.setProperty("routeIdentity", routeIdentity());
        snapshot.setProperty("index", Integer.toString((Integer) Reflect.callMethod(audio, "getLastAudibleStreamVolume", MUSIC)));
        snapshot.setProperty("muted", Boolean.toString(audio.isStreamMute(MUSIC)));
        saved = snapshot;
        writeJournal(); // Must be durable before changing audio or display.
        try {
            audio.setStreamVolume(MUSIC, 0, 0);
            if (audio.getStreamVolume(MUSIC) != 0) throw new IllegalStateException("媒体音量未能归零");
            long now = SystemClock.elapsedRealtime();
            expires = now + MAX_SESSION_MS;
            projections = 0; viewports = 0; keys = 0;
            synchronized (sync) { mode = 2; active = true; }
            refreshDisplay();
            message = "已开启 · 再点切换可恢复";
            handler.postDelayed(() -> {
                if (isLegacy() && (projections == 0 || viewports == 0)) fail(new IllegalStateException("未收到显示或触控更新"));
            }, 2500);
        } catch (Throwable t) { stop("开启失败，已尝试回退"); throw new IllegalStateException(t); }
    }

    boolean isHand(){return active && mode==1;}
    boolean isLegacy(){return active && mode==2;}
    void exitFromOutside(){handler.post(()->{if(isHand())stop("双击空白区域，已退出单手模式");});}
    void openSettings(){handler.post(()->{
        if(!isHand()||!context.getSystemService(PowerManager.class).isInteractive()||context.getSystemService(KeyguardManager.class).isKeyguardLocked())return;
        long now=SystemClock.elapsedRealtime();if(now-lastSettingsOpen<700)return;lastSettingsOpen=now;
        try{context.startActivity(new Intent(Intent.ACTION_MAIN).setClassName(Protocol.PACKAGE,Protocol.PACKAGE+".MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));}
        catch(Throwable t){message="打开模块设置失败："+Diagnostics.concise(t);Entry.log(t);}
    });}
    private void enableHand() {
        if(!ready||failed||!handReady)throw new IllegalStateException("单手窗口接口尚未就绪，请查看日志");
        if(!context.getSystemService(PowerManager.class).isInteractive()||context.getSystemService(KeyguardManager.class).isKeyguardLocked())
            throw new IllegalStateException("请先解锁手机");
        if(saved!=null)throw new IllegalStateException("请先切回原音频设备，完成旧模式音量恢复");
        // A true portrait window keeps status/navigation/keyboard under the same scale.
        try {
            outsideTap.register(windowManager,loader,this,context);
            handSurface.start(windowManager,handSettings,context.getResources().getDisplayMetrics().density);
            mode=1;active=true;keys=0;expires=SystemClock.elapsedRealtime()+MAX_SESSION_MS;
            message="单手模式已开启 · 双击空白处任意位置退出";
            Diagnostics.add("单手模式开启；"+(handSettings.left?"左手":"右手")+"；"+handSettings.percent+"%");
        } catch(Throwable t){stop("单手模式开启失败");throw new IllegalStateException(t);}
    }

    private void refreshDisplay() {
        // Neither wm size/density nor persisted rotation settings are written.
        Reflect.callMethod(windowManager, "updateRotation", false, true);
        synchronized (sync) { Reflect.callMethod(dms, "scheduleTraversalLocked", false); }
    }

    private void stop(String reason) {
        boolean changed;
        synchronized (sync) { changed = active; active = false; mode = 0; }
        keys = 0; message = reason;
        try { outsideTap.unregister(); } catch(Throwable t){Entry.log(t);}
        try { handSurface.stop(); } catch(Throwable t){failed=true;message="显示恢复待重试，请熄屏；若仍异常请重启";Entry.log(t);}
        Diagnostics.add(reason);
        if (changed) {
            try { refreshDisplay(); }
            catch (Throwable t) { failed = true; message = "显示回退异常，请熄屏重亮或重启；本次开机禁止再开启"; Entry.log(t); }
        }
        try { restoreAudio(); }
        catch (Throwable t) { message += "；音量待恢复，请点紧急恢复重试"; Entry.log(t); }
    }

    void fail(Throwable t) {
        Entry.log(t);
        handler.post(() -> { failed = true; stop("兼容检查失败，已退出：" + t.getMessage()); });
    }

    void emergencyKey() {
        handler.post(() -> {
            if (!active) return;
            long now = SystemClock.elapsedRealtime();
            if (now - keyStart > 2000) { keys = 0; keyStart = now; }
            if (++keys >= 3) stop("已通过连按三次音量减恢复");
        });
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            try {
                if (active) {
                    long now = SystemClock.elapsedRealtime();
                    if (now >= expires) stop("已达到两小时上限，自动恢复");
                    else if (isLegacy() && !sameRoute()) stop("音频输出改变，已退出");
                    else if (isLegacy() && audio.getStreamVolume(MUSIC) != 0) audio.setStreamVolume(MUSIC, 0, 0);
                } else {
                    if(handSurface.needsRestore()) {
                        handSurface.stop();refreshDisplay();message="显示已恢复，请重启后再次使用单手模式";
                    }
                    if (saved != null) restoreAudio();
                }
            } catch (Throwable t) { if (active) stop("状态监测失败，已退出"); Entry.log(t); }
            finally { handler.postDelayed(this, 1000); }
        }
    };

    private int route() { return (Integer) Reflect.callMethod(audio, "getDevicesForStream", MUSIC); }

    private String routeIdentity() {
        AudioAttributes a = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        List<?> devices = (List<?>) Reflect.callMethod(audio, "getDevicesForAttributes", a);
        if (devices == null || devices.size() != 1) throw new IllegalStateException("媒体输出设备无法唯一识别");
        Object d = devices.get(0);
        return Reflect.callMethod(d, "getType") + ":" + Reflect.callMethod(d, "getAddress");
    }

    private boolean sameRoute() {
        return route() == Integer.parseInt(saved.getProperty("route")) &&
                routeIdentity().equals(saved.getProperty("routeIdentity"));
    }

    private void writeJournal() throws Exception {
        FileOutputStream out = null;
        try {
            out = journal.startWrite();
            saved.store(out, "Lubenwei mode: original media volume. No display settings are persisted.");
            journal.finishWrite(out);
            File f = journal.getBaseFile();
            f.setReadable(false, false); f.setWritable(false, false);
            f.setReadable(true, true); f.setWritable(true, true);
        } catch (Exception e) {
            if (out != null) journal.failWrite(out);
            saved = null;
            throw e;
        }
    }

    private void loadJournal() throws Exception {
        if (!journal.getBaseFile().exists() && !new File(journal.getBaseFile() + ".bak").exists()) return;
        Properties p = new Properties();
        try (FileInputStream in = journal.openRead()) { p.load(in); }
        if (!"1".equals(p.getProperty("version")) || p.getProperty("routeIdentity") == null)
            throw new IllegalStateException("未知音量备份格式");
        int index = Integer.parseInt(p.getProperty("index"));
        int deviceMask = Integer.parseInt(p.getProperty("route"));
        if (index < 0 || index > audio.getStreamMaxVolume(MUSIC) || Integer.bitCount(deviceMask) != 1)
            throw new IllegalStateException("音量备份校验失败");
        if (!"true".equals(p.getProperty("muted")) && !"false".equals(p.getProperty("muted")))
            throw new IllegalStateException("音量备份校验失败");
        saved = p;
    }

    private void restoreAudio() {
        if (saved == null || audio == null) return;
        if (!sameRoute()) {
            message = "画面已恢复；请切回开启前的音频输出，随后自动恢复原音量";
            return;
        }
        int index = Integer.parseInt(saved.getProperty("index"));
        boolean muted = Boolean.parseBoolean(saved.getProperty("muted"));
        audio.setStreamVolume(MUSIC, index, 0);
        audio.adjustStreamVolume(MUSIC, muted ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE, 0);
        if (audio.isStreamMute(MUSIC) != muted || (!muted && audio.getStreamVolume(MUSIC) != index))
            throw new IllegalStateException("系统拒绝恢复原音量，保留备份以便重试");
        journal.delete(); saved = null;
        if (message.startsWith("画面已恢复；请切回")) message = "画面和原音量均已恢复";
    }
}
