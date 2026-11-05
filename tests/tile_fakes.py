"""Quick Settings service boundary fakes, added to the shared host suite."""
files.update({
'android/service/quicksettings/Tile.java':'''package android.service.quicksettings;public class Tile {
public static final int STATE_UNAVAILABLE=0,STATE_INACTIVE=1,STATE_ACTIVE=2;public int state;public String label,subtitle;
public void setLabel(String s){label=s;}public void setState(int s){state=s;}public void setSubtitle(String s){subtitle=s;}
public void setContentDescription(String s){}public void updateTile(){}}''',
'android/service/quicksettings/TileService.java':'''package android.service.quicksettings;public class TileService extends android.content.Context {
public Tile tile=new Tile();public boolean locked,unlockRequested;public Tile getQsTile(){return tile;}
public void onStartListening(){}public void onStopListening(){}public void onClick(){}public boolean isLocked(){return locked;}
public void unlockAndRun(Runnable r){unlockRequested=true;locked=false;r.run();}}''',
'com/johnwong/danshoumode/AppBridge.java':'''package com.johnwong.danshoumode;import android.os.Bundle;public class AppBridge {
interface Reply {void accept(Bundle b);}static Reply next;static String lastOp;static int calls;
static void request(android.content.Context c,String op,Reply reply){if(next!=null)throw new AssertionError("overlapping broadcast");next=reply;lastOp=op;calls++;}
static void reply(Bundle b){Reply r=next;next=null;r.accept(b);}}''',
'com/johnwong/danshoumode/TileTest.java':'''package com.johnwong.danshoumode;import android.os.*;import android.service.quicksettings.Tile;
public class TileTest {static int count;static void check(boolean b,String s){count++;if(!b)throw new AssertionError(s);}
public static void main(String[] args){Handler.clear();HandTileService t=new HandTileService();Bundle b=new Bundle();
b.putBoolean("connected",true);b.putBoolean("handReady",true);b.putString("side","left");b.putInt("percent",55);
t.onStartListening();Handler.advance(0);check("status".equals(AppBridge.lastOp),"listening requests server status");
t.onClick();check(AppBridge.calls==1,"tap waits for in-flight status without overlap");AppBridge.reply(b);
check("hand_toggle".equals(AppBridge.lastOp)&&AppBridge.calls==2,"queued tap is not dropped and uses saved server preferences");
b.putBoolean("handActive",true);AppBridge.reply(b);check(t.tile.state==Tile.STATE_ACTIVE&&t.tile.subtitle.contains("55%")&&t.tile.label.equals("伪单手模式"),"active tile reflects server state and settings");
Handler.advance(1000);b.putBoolean("handActive",false);AppBridge.reply(b);check(t.tile.state==Tile.STATE_INACTIVE,"outside exit reflected during next listening poll");
t.locked=true;t.onClick();check(t.unlockRequested&&"hand_toggle".equals(AppBridge.lastOp),"locked tile waits for unlock");AppBridge.reply(b);
t.onStopListening();int calls=AppBridge.calls;Handler.advance(5000);check(calls==AppBridge.calls,"hidden tile stops polling");
t.onStartListening();Handler.advance(0);AppBridge.reply(new Bundle());check(t.tile.state==Tile.STATE_UNAVAILABLE,"disconnected framework is unavailable");
System.out.println("Quick Settings: "+count+" state/queue checks passed against boundary fakes");}}
'''
})
files['android/os/Looper.java']=files['android/os/Looper.java'].replace('public class Looper {}','public class Looper {public static Looper getMainLooper(){return new Looper();}}')
files['android/os/Handler.java']=files['android/os/Handler.java'].replace('public static void clear()', 'public void removeCallbacks(Runnable r){q.removeIf(j->j.r==r);}public static void clear()')
files['android/os/Bundle.java']=files['android/os/Bundle.java'].replace('public void putFloat', 'public boolean getBoolean(String k){return Boolean.TRUE.equals(get(k));}public String getString(String k){return (String)get(k);}public float getFloat(String k,float f){return containsKey(k)?(Float)get(k):f;}public void putFloat')
