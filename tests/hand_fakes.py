"""Extra Android boundary fakes loaded by host_harness.py; no production algorithms."""
files.update({
'android/content/Intent.java':'''package android.content;import java.util.*;public class Intent {
public static final String ACTION_SCREEN_OFF="screenoff",ACTION_SHUTDOWN="shutdown",ACTION_MAIN="main";
public static final int FLAG_ACTIVITY_NEW_TASK=0x10000000,FLAG_ACTIVITY_CLEAR_TOP=0x04000000;
public String componentPackage,componentClass;public int flags;
public Intent setClassName(String p,String c){componentPackage=p;componentClass=c;return this;}public Intent addFlags(int f){flags|=f;return this;}
public String action;private Map<String,Object> extras=new HashMap<>();public Intent(){}public Intent(String a){action=a;}public String getAction(){return action;}
public Intent setOp(String x){return putExtra("op",x);}public Intent putExtra(String k,String v){extras.put(k,v);return this;}
public Intent putExtra(String k,float v){extras.put(k,v);return this;}public String getStringExtra(String k){return (String)extras.get(k);}
public Intent putExtra(String k,int v){extras.put(k,v);return this;}public int getIntExtra(String k,int fallback){Object o=extras.get(k);return o==null?fallback:(Integer)o;}
public float getFloatExtra(String k,float fallback){Object o=extras.get(k);return o==null?fallback:(Float)o;}}''',
'android/content/res/Resources.java':'''package android.content.res;public class Resources {public android.util.DisplayMetrics getDisplayMetrics(){return new android.util.DisplayMetrics();}}''',
'android/util/DisplayMetrics.java':'''package android.util;public class DisplayMetrics {public float density=3f;}''',
'android/graphics/Rect.java':'''package android.graphics;public class Rect {public int left,top,right,bottom;public Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}}''',
'android/view/MotionEvent.java':'''package android.view;public class MotionEvent {
public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3,ACTION_POINTER_DOWN=5;
int action,pointers;float x,y;long time;public MotionEvent(int a,float x,float y,long t,int p){action=a;this.x=x;this.y=y;time=t;pointers=p;}
public int getActionMasked(){return action;}public int getPointerCount(){return pointers;}public float getX(){return x;}public float getY(){return y;}public long getEventTime(){return time;}}''',
'android/view/WindowManagerPolicyConstants.java':'''package android.view;public interface WindowManagerPolicyConstants {interface PointerEventListener{void onPointerEvent(MotionEvent e);}}''',
'android/view/SurfaceControl.java':'''package android.view;import android.graphics.Rect;import java.util.*;public class SurfaceControl {
public static boolean failNextApply;public boolean valid=true,shown;public SurfaceControl parent;public String name;public int layer;
public float x,y,scale=1,radius;public int bufferWidth,bufferHeight,format;public Rect crop;public boolean isValid(){return valid;}public void release(){valid=false;}
public static class Builder {SurfaceControl s=new SurfaceControl();public Builder setName(String n){s.name=n;return this;}public Builder setContainerLayer(){return this;}
public Builder setBufferSize(int w,int h){s.bufferWidth=w;s.bufferHeight=h;return this;}public Builder setFormat(int f){s.format=f;return this;}
public Builder setParent(SurfaceControl p){s.parent=p;return this;}public SurfaceControl build(){return s;}}
public static class Transaction implements AutoCloseable {
List<Runnable> work=new ArrayList<>();public Transaction setLayer(SurfaceControl s,int n){work.add(()->s.layer=n);return this;}
public Transaction reparent(SurfaceControl s,SurfaceControl p){work.add(()->s.parent=p);return this;}
public Transaction show(SurfaceControl s){work.add(()->s.shown=true);return this;}
public Transaction setMatrix(SurfaceControl s,float a,float b,float c,float d){if(a!=d||b!=0||c!=0)throw new AssertionError("nonuniform transform");work.add(()->s.scale=a);return this;}
public Transaction setPosition(SurfaceControl s,float x,float y){work.add(()->{s.x=x;s.y=y;});return this;}
public Transaction setCrop(SurfaceControl s,Rect r){work.add(()->s.crop=r);return this;}
public Transaction setCornerRadius(SurfaceControl s,float r){work.add(()->s.radius=r);return this;}
public Transaction remove(SurfaceControl s){work.add(()->{s.valid=false;s.parent=null;});return this;}
public void apply(){if(failNextApply){failNextApply=false;throw new IllegalStateException("injected compositor failure");}for(Runnable r:work)r.run();work.clear();}public void close(){work.clear();}}}''',
'android/view/WindowManagerGlobal.java':'''package android.view;public class WindowManagerGlobal {
public static final WM wm=new WM();public static Object getWindowManagerService(){return wm;}
public static class WM {public boolean fail;public int refreshes;public Object mGlobalLock=new Object();public DC dc;public WindowManagerPolicyConstants.PointerEventListener listener;
public WM(){reset();}public void reset(){dc=new DC();listener=null;SurfaceControl.failNextApply=false;}
public void updateRotation(boolean a,boolean b){refreshes++;if(fail)throw new IllegalStateException("injected display failure");}
public DC getDefaultDisplayContentLocked(){return dc;}public SurfaceControl.Builder makeSurfaceBuilder(){return new SurfaceControl.Builder();}
public void registerPointerEventListener(WindowManagerPolicyConstants.PointerEventListener l,int id){if(id!=0)throw new AssertionError();listener=l;}
public void unregisterPointerEventListener(WindowManagerPolicyConstants.PointerEventListener l,int id){if(listener!=l)throw new AssertionError();listener=null;}
public void event(int a,float x,float y,long time,int n){if(listener!=null)listener.onPointerEvent(new MotionEvent(a,x,y,time,n));}}
public static class Info {public int logicalWidth=1440,logicalHeight=3200,rotation;}
public static class Animator {public boolean animating;public boolean hasLeash(){return animating;}}
public static class Parent {public SurfaceControl root=new SurfaceControl();public SurfaceControl getSurfaceControl(){return root;}}
public static class ConfigurationContainer {protected Object getParent(){throw new AssertionError("base getter selected");}}
public static class Area extends ConfigurationContainer {public Parent parent=new Parent();public SurfaceControl surface=new SurfaceControl();private int mLastLayer=7;
private SurfaceControl mLastRelativeToLayer;public Animator mSurfaceAnimator=new Animator();public Area(){surface.parent=parent.root;surface.layer=7;surface.radius=11;surface.x=3;}
@Override public Parent getParent(){return parent;}}
public static class Policy {public Area area=new Area();public Area getWindowingArea(){return area;}}
public static class DC {public Policy mDisplayAreaPolicy=new Policy();public Info info=new Info();public Info getDisplayInfo(){return info;}
public SurfaceControl getWindowingLayer(){return mDisplayAreaPolicy.area.surface;}}
}'''
})
files['android/content/Context.java']=files['android/content/Context.java'].replace(
 'public <T>T getSystemService', 'public android.content.res.Resources getResources(){return new android.content.res.Resources();}public <T>T getSystemService')
files['android/content/Context.java']=files['android/content/Context.java'].replace(
 'public static final int RECEIVER_EXPORTED', 'public Intent launched;public int launches;public void startActivity(Intent i){launched=i;launches++;}public static final int RECEIVER_EXPORTED')
files['android/os/Bundle.java']=files['android/os/Bundle.java'].replace(
 'public void putFloat', 'public void putInt(String k,int v){put(k,v);}public int getInt(String k,int d){return containsKey(k)?(Integer)get(k):d;}public void putFloat')
files['android/util/AtomicFile.java']=files['android/util/AtomicFile.java'].replace(
 'private File file;', 'public static boolean failNextWrite;private File file;').replace(
 'return new FileOutputStream(file);', 'if(failNextWrite){failNextWrite=false;throw new IOException("injected config write failure");}return new FileOutputStream(file);')
files.update({
'android/view/ViewConfiguration.java':'''package android.view;public class ViewConfiguration {
public static ViewConfiguration get(android.content.Context c){return new ViewConfiguration();}
public int getScaledTouchSlop(){return 24;}public int getScaledDoubleTapSlop(){return 300;}
public static int getDoubleTapTimeout(){return 300;}public static int getLongPressTimeout(){return 500;}}''',
'android/graphics/Color.java':'''package android.graphics;public class Color {public static final int TRANSPARENT=0;}''',
'android/graphics/PixelFormat.java':'''package android.graphics;public class PixelFormat {public static final int TRANSLUCENT=-3;}''',
'android/graphics/PorterDuff.java':'''package android.graphics;public class PorterDuff {public enum Mode {CLEAR}}''',
'android/graphics/RectF.java':'''package android.graphics;public class RectF {public RectF(float l,float t,float r,float b){}}''',
'android/graphics/Paint.java':'''package android.graphics;public class Paint {public static final int ANTI_ALIAS_FLAG=1;
public enum Align {CENTER}public enum Style {STROKE}public Paint(int flags){}public void setTextAlign(Align a){}public void setColor(int c){}
public void setTextSize(float s){}public void setStyle(Style s){}public void setStrokeWidth(float w){}}''',
'android/graphics/Canvas.java':'''package android.graphics;import java.util.*;public class Canvas {
public List<String> texts=new ArrayList<>();public void drawColor(int c,PorterDuff.Mode m){}
public void drawText(String s,float x,float y,Paint p){texts.add(s);}public void drawRoundRect(RectF r,float x,float y,Paint p){}
public void drawCircle(float x,float y,float r,Paint p){}public void drawLine(float x,float y,float x2,float y2,Paint p){}}''',
'android/view/Surface.java':'''package android.view;import java.util.*;import android.graphics.*;public class Surface {
public static List<Surface> created=new ArrayList<>();public static boolean failNextLock;public SurfaceControl control;public Canvas canvas=new Canvas();public boolean released,posted;
public Surface(SurfaceControl c){control=c;created.add(this);}public Canvas lockCanvas(Rect dirty){if(failNextLock){failNextLock=false;throw new IllegalStateException("injected canvas failure");}return canvas;}
public void unlockCanvasAndPost(Canvas c){posted=true;}public void release(){released=true;}}'''
})
