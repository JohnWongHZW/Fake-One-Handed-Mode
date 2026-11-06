"""Run the real Controller against deterministic Android boundary fakes.
These are lifecycle/recovery tests, not a claim of ROM, touch-driver or UI compatibility.
"""
from pathlib import Path
import subprocess
import os
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'build/host-tests'
SRC=BASE/'src'
OUT=BASE/'classes'
OUT.mkdir(parents=True,exist_ok=True)
files={
'android/os/SystemClock.java':'''package android.os; public class SystemClock {public static long now=10000; public static long elapsedRealtime(){return now;}}''',
'android/os/Looper.java':'''package android.os; public class Looper {}''',
'android/os/HandlerThread.java':'''package android.os; public class HandlerThread {public HandlerThread(String s){} public void start(){} public Looper getLooper(){return new Looper();}}''',
'android/os/Handler.java':'''package android.os; import java.util.*; public class Handler {
static class Job {long t;Runnable r;Job(long a,Runnable b){t=a;r=b;}}
static PriorityQueue<Job> q=new PriorityQueue<>((a,b)->Long.compare(a.t,b.t));
public Handler(Looper l){} public void post(Runnable r){postDelayed(r,0);} public void postDelayed(Runnable r,long ms){q.add(new Job(SystemClock.now+ms,r));}
public static void advance(long ms){long end=SystemClock.now+ms;int max=10000;while(!q.isEmpty()&&q.peek().t<=end){if(--max==0)throw new Error("loop");Job j=q.poll();SystemClock.now=j.t;j.r.run();}SystemClock.now=end;}
public static void clear(){q.clear();}}''',
'android/os/Bundle.java':'''package android.os; import java.util.*; public class Bundle extends HashMap<String,Object>{public void putFloat(String k,float v){put(k,v);}public void putBoolean(String k,boolean v){put(k,v);}public void putLong(String k,long v){put(k,v);}public void putString(String k,String v){put(k,v);}}''',
'android/os/PowerManager.java':'''package android.os; public class PowerManager {public boolean isInteractive(){return true;}}''',
'android/os/SystemProperties.java':'''package android.os; public class SystemProperties {public static String get(String a,String b){return "1";}}''',
'android/app/ActivityThread.java':'''package android.app;import android.content.Context;public class ActivityThread{public static Context context;public static ActivityThread currentActivityThread(){return new ActivityThread();}public Context getSystemContext(){return context;}}''',
'android/app/KeyguardManager.java':'''package android.app; public class KeyguardManager {public boolean locked; public boolean isKeyguardLocked(){return locked;}}''',
'android/content/Intent.java':'''package android.content; public class Intent {public static final String ACTION_SCREEN_OFF="screenoff",ACTION_SHUTDOWN="shutdown";public String action,op;public Intent(String a){action=a;} public Intent setOp(String x){op=x;return this;}public String getStringExtra(String s){return op;}}''',
'android/content/IntentFilter.java':'''package android.content;import java.util.*;public class IntentFilter{public List<String> actions=new ArrayList<>();public IntentFilter(){}public IntentFilter(String s){addAction(s);}public void addAction(String s){actions.add(s);}}''',
'android/content/BroadcastReceiver.java':'''package android.content;import android.os.Bundle;public abstract class BroadcastReceiver {public int code;public Bundle result;public abstract void onReceive(Context c,Intent i); public void setResultCode(int n){code=n;}public void setResultExtras(Bundle b){result=b;}}''',
'android/content/Context.java':'''package android.content;import java.util.*;import android.os.*;import android.app.*;import android.media.*;public class Context {
public static final int RECEIVER_EXPORTED=2;public AudioManager audio=new AudioManager();public KeyguardManager keyguard=new KeyguardManager();public Map<String,BroadcastReceiver> receivers=new HashMap<>();
public <T>T getSystemService(Class<T> c){return c.cast(c==AudioManager.class?audio:c==KeyguardManager.class?keyguard:new PowerManager());}
public Intent registerReceiver(BroadcastReceiver r,IntentFilter f,String p,Handler h,int flags){for(String a:f.actions)receivers.put(a,r);return null;}
public void send(String a,String op){receivers.get(a).onReceive(this,new Intent(a).setOp(op));}}''',
'android/graphics/Rect.java':'''package android.graphics;public class Rect {}''',
'android/media/AudioAttributes.java':'''package android.media;public class AudioAttributes{public static final int USAGE_MEDIA=1,CONTENT_TYPE_MUSIC=2;public static class Builder{public Builder setUsage(int n){return this;}public Builder setContentType(int n){return this;}public AudioAttributes build(){return new AudioAttributes();}}}''',
'android/media/AudioManager.java':'''package android.media;import java.util.*;public class AudioManager{
public static final int STREAM_MUSIC=3,ADJUST_MUTE=-100,ADJUST_UNMUTE=100;public int volume=7,mask=2;public String address="speaker";public boolean muted,denyRestore;
public boolean isVolumeFixed(){return false;}public int getDevicesForStream(int s){return mask;}public int getLastAudibleStreamVolume(int s){return volume;}public int getStreamMaxVolume(int s){return 15;}
public int getStreamVolume(int s){return muted?0:volume;}public boolean isStreamMute(int s){return muted;}
public void setStreamVolume(int s,int n,int f){if(n>0&&denyRestore)throw new IllegalStateException("restore denied");volume=n;muted=n==0;}
public void adjustStreamVolume(int s,int d,int f){muted=d==ADJUST_MUTE;}
public List<Device> getDevicesForAttributes(AudioAttributes a){return Collections.singletonList(new Device(mask,address));}
public static class Device {int t;String a;Device(int t,String a){this.t=t;this.a=a;}public int getType(){return t;}public String getAddress(){return a;}}}''',
'android/util/AtomicFile.java':'''package android.util;import java.io.*;public class AtomicFile {
private File file;public AtomicFile(File f){file=new File(System.getProperty("test.root"),f.getName());}
public File getBaseFile(){return file;}public FileOutputStream startWrite()throws Exception{return new FileOutputStream(file);}public void finishWrite(FileOutputStream s)throws Exception{s.close();}
public void failWrite(FileOutputStream s){try{s.close();}catch(Exception e){}} public FileInputStream openRead()throws Exception{return new FileInputStream(file);}public void delete(){file.delete();}}''',
'android/view/WindowManagerGlobal.java':'''package android.view;public class WindowManagerGlobal {public static final WM wm=new WM();public static Object getWindowManagerService(){return wm;}public static class WM {public boolean fail;public int refreshes; public void updateRotation(boolean a,boolean b){refreshes++;if(fail)throw new IllegalStateException("injected display failure");}}}''',
'com/johnwong/danshoumode/Entry.java':'''package com.johnwong.danshoumode;public class Entry{static boolean projectionSeen=true,viewportSeen=true,hooksReady=true,handHooksReady=true;static String hookFailure="synthetic unsupported interface";static Controller controller;static Object displayManager;static void log(Throwable t){}}''',
'android/os/Build.java':'''package android.os;public class Build {public static final String MANUFACTURER="TestMaker",MODEL="TestModel",DISPLAY="TestBuild";public static class VERSION {public static final String RELEASE="17";public static final int SDK_INT=37;}}''',
'com/johnwong/danshoumode/ControllerTest.java':'''package com.johnwong.danshoumode;
import android.content.*;import android.os.*;import android.view.WindowManagerGlobal;import java.io.*;
public class ControllerTest {
static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
public static class Info{public int width=1440,height=3200,type=1,rotation=0;}
public static class Device{public Object getDisplayDeviceInfoLocked(){return new Info();}}
public static class Logical{public Object getPrimaryDisplayDeviceLocked(){return new Device();}}
public static class Mapper{public Object getDisplayLocked(int id){return new Logical();}}
public static class Dms{public Object mSyncRoot=new Object(),mLogicalDisplayMapper=new Mapper();public void scheduleTraversalLocked(boolean b){}}
static class Fixture {Context ctx=new Context();Controller c;
Fixture(boolean clean){Handler.clear();WindowManagerGlobal.wm.fail=false;if(clean){new File(System.getProperty("test.root"),"danshoumode-lubenwei-audio.properties").delete();new File(System.getProperty("test.root"),"johnwong-danshou-config.properties").delete();} WindowManagerGlobal.wm.reset();
c=new Controller(ctx,new Dms(),getClass().getClassLoader());c.start();Handler.advance(0);}
void op(String op){c.command(op);}void start(){op("toggle");c.projections=1;c.viewports=1;}}
public static void main(String[] args)throws Exception{
Fixture f=new Fixture(true);f.start();check(f.c.active&&f.ctx.audio.volume==0,"enter mutes");
f.op("toggle");check(!f.c.active&&f.ctx.audio.volume==7&&!f.ctx.audio.muted,"toggle restores exact volume");
f=new Fixture(true);f.start();Handler.advance(21000);check(f.c.isLegacy()&&f.ctx.audio.volume==0,"first activation stays active and muted after 20 seconds without confirmation");
f.op("toggle");check(!f.c.active&&f.ctx.audio.volume==7,"toggle after former preview deadline restores audio");
f.start();Handler.advance(25000);check(f.c.active,"subsequent activation needs no confirmation");
Handler.advance(7200000);check(!f.c.active&&f.ctx.audio.volume==7,"session limit restores");
f=new Fixture(true);f.start();for(int i=0;i<3;i++){f.c.emergencyKey();Handler.advance(100);}check(!f.c.active&&f.ctx.audio.volume==7,"emergency key restores");
f=new Fixture(true);f.start();f.ctx.send(Intent.ACTION_SCREEN_OFF,null);Handler.advance(25000);
check(f.c.isLegacy()&&f.ctx.audio.volume==0,"legacy stays active and muted through screen off without confirmation");
f.op("stop");check(!f.c.active&&f.ctx.audio.volume==7,"manual exit after screen off restores original audio");
f=new Fixture(true);f.start();f.ctx.send("android.intent.action.USER_SWITCHED",null);check(!f.c.active&&f.ctx.audio.volume==7,"user switch still restores legacy mode");
f=new Fixture(true);f.start();f.ctx.send(Intent.ACTION_SHUTDOWN,null);check(!f.c.active&&f.ctx.audio.volume==7,"shutdown still restores legacy mode");
f=new Fixture(true);f.ctx.audio.muted=true;f.start();f.op("stop");check(f.ctx.audio.volume==7&&f.ctx.audio.muted,"original mute preserved");
f=new Fixture(true);f.start();f.ctx.audio.address="different same-type device";f.ctx.audio.volume=4;Handler.advance(1000);
check(!f.c.active&&f.ctx.audio.volume==4,"route identity change does not overwrite new device");
f.ctx.audio.address="speaker";Handler.advance(1000);check(f.ctx.audio.volume==7,"pending original route restored");
f=new Fixture(true);f.start();f.ctx.audio.denyRestore=true;f.op("stop");check(!f.c.active,"audio error never prevents display exit");
f.ctx.audio.denyRestore=false;Handler.advance(1000);check(f.ctx.audio.volume==7,"failed restore retries");
f=new Fixture(true);WindowManagerGlobal.wm.fail=true;f.op("toggle");check(!f.c.active&&f.ctx.audio.volume==7,"display update exception rolls back audio");
f=new Fixture(true);f.start();f=new Fixture(false);check(!f.c.active&&f.ctx.audio.volume==7,"restart remains off and restores journal");
f=new Fixture(true);f.ctx.keyguard.locked=true;f.op("toggle");check(!f.c.active&&f.ctx.audio.volume==7,"locked device rejects before changes");
f=new Fixture(true);f.op("toggle");Handler.advance(3000);check(!f.c.active&&f.ctx.audio.volume==7,"missing projection callbacks auto-exit");
f=new Fixture(true);Entry.hooksReady=false;f=new Fixture(true);f.op("toggle");check(!f.c.active&&f.ctx.audio.volume==7,"unresolved hook prevents all mutations");
check(f.c.command("status").get("message").toString().contains("synthetic unsupported interface"),"diagnostic remains available after hook failure");Entry.hooksReady=true;
Handler.clear();Entry.controller=null;Entry.displayManager=new Dms();Entry.hooksReady=false;
Context diagContext=new Context();android.app.ActivityThread.context=diagContext;Bootstrap.start(ControllerTest.class.getClassLoader());Handler.advance(0);
diagContext.send(Protocol.ACTION,"status");BroadcastReceiver receiver=diagContext.receivers.get(Protocol.ACTION);
check(receiver.code==1&&Boolean.FALSE.equals(receiver.result.get("ready")),"modern bootstrap replies even when display hook resolution fails");
check(receiver.result.get("diagnostics").toString().contains("TestMaker TestModel"),"modern bootstrap exposes device report independently of display hook");
Diagnostics.add("sentinel diagnostic without display hooks");diagContext.send(Protocol.ACTION,"clear_logs");
check(Boolean.TRUE.equals(receiver.result.get("logsCleared"))&&!receiver.result.get("diagnostics").toString().contains("sentinel diagnostic"),"logs clear even when display hooks are unavailable");
Handler.clear();Entry.controller=null;Entry.displayManager=null;Entry.hooksReady=true;diagContext=new Context();android.app.ActivityThread.context=diagContext;
Bootstrap.start(ControllerTest.class.getClassLoader());Handler.advance(0);check(!diagContext.receivers.containsKey(Protocol.ACTION),"bootstrap waits for late service capture");
Entry.displayManager=new Dms();Handler.advance(500);check(diagContext.receivers.containsKey(Protocol.ACTION)&&Entry.controller!=null,"late DMS capture connects without another reboot");
Handler.clear();Entry.controller=null;Entry.displayManager=new Object();diagContext=new Context();android.app.ActivityThread.context=diagContext;
Bootstrap.start(ControllerTest.class.getClassLoader());Handler.advance(0);diagContext.send(Protocol.ACTION,"status");receiver=diagContext.receivers.get(Protocol.ACTION);
check(receiver.code==1&&Boolean.FALSE.equals(receiver.result.get("ready")),"invalid DMS internals leave diagnostic-only channel available");
System.out.println("Controller/bootstrap: "+checks+" lifecycle/recovery checks passed against deterministic boundary fakes");
}}
'''
}
# These fakes implement only Android boundaries; production controller, settings,
# surface management and outside-tap detector are compiled unmodified.
exec((ROOT/'tests/hand_fakes.py').read_text(encoding='utf-8'))
exec((ROOT/'tests/tile_fakes.py').read_text(encoding='utf-8'))
files['com/johnwong/danshoumode/ControllerTest.java']=files['com/johnwong/danshoumode/ControllerTest.java'].replace(
 'System.out.println("Controller/bootstrap: "+checks', (ROOT/'tests/hand_scenarios.java.txt').read_text(encoding='utf-8')+'\nSystem.out.println("Controller/bootstrap: "+checks')
# The Android input monitor and broadcasts require a platform process. Exercise the
# production recognizer and controller; the JVM boundary does not dispatch across apps.
files['com/johnwong/danshoumode/FrameChannel.java']='package com.johnwong.danshoumode;class FrameChannel{static void serve(android.content.Context c,android.os.Handler h,Controller v){} static void publish(android.content.Context c,Controller v){}}'
files['com/johnwong/danshoumode/BarMonitor.java']='package com.johnwong.danshoumode;class BarMonitor{static void install(android.content.Context c,Object wm,ClassLoader l,Controller v){}}'
for name,content in files.items():
    p=SRC/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content,encoding='utf-8')
java_home=Path(os.environ.get('JAVA_HOME',r'D:\Java\17'))
def tool(n):
    p=java_home/'bin'/(n+('.exe' if os.name=='nt' else ''))
    return str(p) if p.exists() else n
sources=[SRC/name for name in files]+[ROOT/'src/com/johnwong/danshoumode'/n for n in ['Controller.java','Geometry.java','Protocol.java','Reflect.java','Bootstrap.java','HandSettings.java','HandSurface.java','BlankDecor.java','BarSwipe.java','OutsideTap.java','HandTileService.java','Diagnostics.java']]
subprocess.run([tool('javac'),'-encoding','UTF-8','--release','8','-d',str(OUT),*map(str,sources)],check=True)
subprocess.run([tool('java'),'-Dtest.root='+str(BASE),'-cp',str(OUT),'com.johnwong.danshoumode.ControllerTest'],check=True)
subprocess.run([tool('java'),'-cp',str(OUT),'com.johnwong.danshoumode.TileTest'],check=True)
