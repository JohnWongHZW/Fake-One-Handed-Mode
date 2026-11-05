"""Regression tests for the real adapter against ROM-shaped classes with NO setProjectionLocked."""
from pathlib import Path
import os,subprocess
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'build/projection-tests';SRC=BASE/'src';OUT=BASE/'classes'
OUT.mkdir(parents=True,exist_ok=True)
files={
'android/os/IBinder.java':'package android.os; public interface IBinder {}',
'android/graphics/Rect.java':'''package android.graphics;public class Rect {
public int left,top,right,bottom;public Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}public Rect(Rect r){this(r.left,r.top,r.right,r.bottom);}
public int width(){return right-left;}public int height(){return bottom-top;}public boolean isEmpty(){return width()<=0||height()<=0;}
public boolean equals(Object o){if(!(o instanceof Rect))return false;Rect r=(Rect)o;return left==r.left&&top==r.top&&right==r.right&&bottom==r.bottom;}}''',
'android/view/SurfaceControl.java':'''package android.view;import android.os.IBinder;import android.graphics.Rect;public class SurfaceControl {
public static class Transaction {public int rotation,calls;public Rect source,dest;public boolean fail;
public Transaction setDisplayProjection(IBinder token,int rotation,Rect src,Rect dst){if(fail)throw new IllegalStateException("transaction denied");calls++;this.rotation=rotation;source=new Rect(src);dest=new Rect(dst);return this;}}}''',
'com/android/server/display/DisplayDevice.java':'''package com.android.server.display;import android.graphics.Rect;import android.os.IBinder;
public class DisplayDevice {public int mCurrentOrientation;public Rect mCurrentLayerStackRect=new Rect(0,0,1440,3200),mCurrentDisplayRect=new Rect(0,0,1440,3200);
private final IBinder token=new IBinder(){};public IBinder getDisplayTokenLocked(){return token;}
// Deliberately no setProjectionLocked method: this reproduces the user's missing-method condition.
}''',
'com/johnwong/danshoumode/ProjectionTest.java':'''package com.johnwong.danshoumode;
import android.graphics.Rect;import android.view.SurfaceControl.Transaction;import com.android.server.display.DisplayDevice;
public class ProjectionTest {
static int checks;static void check(boolean ok,String msg){checks++;if(!ok)throw new AssertionError(msg);}
public static class Base {private int flag=5;private int getFlag(){return flag;}}
public static class Child extends Base {}
public static class Aosp {public void configureDisplayLocked(Transaction t,DisplayDevice d,boolean blank){normal(t,d);}}
public static class Oem {public int calls,flag;public boolean originalBool;
public void configureDisplayLocked(DisplayDevice d,boolean blank,Transaction t,int vendorFlag){calls++;flag=vendorFlag;originalBool=blank;normal(t,d);}}
public static class Ambiguous {public void configureDisplayLocked(Transaction t,DisplayDevice d,boolean blank){}public void configureDisplayLocked(Transaction t,DisplayDevice d,int flags){}}
public static class Unknown {public void configureDisplayLocked(Transaction t,Object d,boolean blank){}}
public static class DoubleTransaction {public void configureDisplayLocked(Transaction a,Transaction b,DisplayDevice d){}}
public static class Viewport {public int orientation;public Rect physicalFrame,logicalFrame;}
static void normal(Transaction t,DisplayDevice d){d.mCurrentOrientation=0;d.mCurrentLayerStackRect=new Rect(0,0,1440,3200);d.mCurrentDisplayRect=new Rect(0,0,1440,3200);t.setDisplayProjection(d.getDisplayTokenLocked(),0,d.mCurrentLayerStackRect,d.mCurrentDisplayRect);}
static void rejected(Class<?> c){boolean rejected=false;try{HookSignatures.configuration(c);}catch(IllegalStateException e){rejected=true;}check(rejected,"reject ambiguous or unknown signature "+c);}
public static void main(String[] args)throws Exception {
check(Reflect.getIntField(new Child(),"flag")==5,"inherited private field");check(((Integer)Reflect.callMethod(new Child(),"getFlag"))==5,"inherited private method");
HookSignatures.Configuration a=HookSignatures.configuration(Aosp.class);check(a.transactionIndex==0&&a.deviceIndex==1,"AOSP signature");
HookSignatures.Configuration o=HookSignatures.configuration(Oem.class);check(o.transactionIndex==2&&o.deviceIndex==0,"OEM typed arguments instead of exact descriptor");
rejected(Ambiguous.class);rejected(Unknown.class);rejected(DoubleTransaction.class);
ProjectionAdapter adapter=new ProjectionAdapter(DisplayDevice.class,Transaction.class,ProjectionTest.class.getClassLoader());
DisplayDevice d=new DisplayDevice();Transaction t=new Transaction();Oem logical=new Oem();
o.method.invoke(logical,d,false,t,42);Rect original=d.mCurrentLayerStackRect;adapter.apply(d,t,1440,3200);
check(logical.calls==1&&logical.flag==42&&!logical.originalBool,"original executes once and OEM arguments unchanged");
check(t.calls==2&&t.rotation==1,"normal configure then our projection");
check(t.source.equals(new Rect(0,0,1440,3200))&&original==d.mCurrentLayerStackRect,"full original frame preserved");
check(d.mCurrentOrientation==1&&d.mCurrentDisplayRect.equals(t.dest),"projection and viewport cache synchronized");
check(t.dest.left==0&&t.dest.right<=3200/3&&t.dest.bottom<=1440,"left-third geometry");
Viewport v=new Viewport();v.orientation=1;v.physicalFrame=new Rect(t.dest);v.logicalFrame=new Rect(t.source);adapter.verify(d,v,1440,3200);check(true,"matching input viewport accepted");
v.orientation=0;boolean rejected=false;try{adapter.verify(d,v,1440,3200);}catch(IllegalStateException e){rejected=true;}check(rejected,"mismatched input rotation rejected");
normal(t,d);check(t.rotation==0&&d.mCurrentDisplayRect.equals(new Rect(0,0,1440,3200)),"next normal configure restores full projection");
t.fail=true;boolean failed=false;try{adapter.apply(d,t,1440,3200);}catch(Exception e){failed=true;}check(failed&&d.mCurrentOrientation==0&&d.mCurrentDisplayRect.equals(new Rect(0,0,1440,3200)),"transaction failure does not corrupt viewport cache");
System.out.println("Projection/ROM regression: "+checks+" checks passed; fixture contains no setProjectionLocked method");
}}
'''
}
for name,content in files.items():
    p=SRC/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content,encoding='utf-8')
java=Path(os.environ.get('JAVA_HOME',r'D:\Java\17'))/'bin'
def tool(n):
    p=java/(n+('.exe' if os.name=='nt' else ''));return str(p) if p.exists() else n
sources=[SRC/name for name in files]+[ROOT/'src/com/johnwong/danshoumode'/n for n in ['Reflect.java','Geometry.java','HookSignatures.java','ProjectionAdapter.java']]
subprocess.run([tool('javac'),'-encoding','UTF-8','--release','8','-d',str(OUT),*map(str,sources)],check=True)
subprocess.run([tool('java'),'-cp',str(OUT),'com.johnwong.danshoumode.ProjectionTest'],check=True)
