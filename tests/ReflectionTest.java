package com.johnwong.danshoumode;

import java.io.Serializable;
import java.lang.reflect.Method;

/** Real javac-generated bridges reproduce the Android getParent failure. */
public final class ReflectionTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static class Parent {}
    static class Base {protected Object getParent(){throw new AssertionError("base implementation selected");}}
    static class Window extends Base {
        final Parent parent=new Parent();int calls;
        @Override protected Parent getParent(){calls++;return parent;}
    }
    static class Area extends Window {}
    static class Generic<T> {protected T find(int id){return null;}}
    static class Specialized extends Generic<Parent> {
        final Parent parent=new Parent();int calls;
        @Override protected Parent find(int id){calls++;return parent;}
    }
    interface ObjectFactory {Object create();}
    interface NumberFactory {Number create();}
    static class ManyBridges implements ObjectFactory,NumberFactory {
        int calls;public Integer create(){calls++;return 42;}
    }
    static class Overloads {
        int calls;
        Object pick(CharSequence x){calls++;return x;}
        Object pick(Serializable x){calls++;return x;}
    }
    static class PrivateParent {private String hidden(){return "inherited";}}
    static class PrivateChild extends PrivateParent {}
    static class StaticTarget {private static String marker(int n){return "static:"+n;}}
    static class Throwing {private Object getParent(){throw new IllegalArgumentException("original cause");}}
    static int bridges(Class<?> type,String name){int n=0;for(Method m:type.getDeclaredMethods())if(m.getName().equals(name)&&m.isBridge())n++;return n;}
    public static void main(String[] args){
        check(bridges(Window.class,"getParent")==1,"fixture must include a real covariant-return bridge");
        Window window=new Window();check(Reflect.callMethod(window,"getParent")==window.parent,"same-signature bridge resolves to real parent");
        check(window.calls==1,"real parent getter invoked once");
        Area area=new Area();check(Reflect.callMethod(area,"getParent")==area.parent&&area.calls==1,"inherited bridge pair resolves");
        Specialized specialized=new Specialized();check(bridges(Specialized.class,"find")==1,"parameterized fixture has bridge");
        check(Reflect.callMethod(specialized,"find",7)==specialized.parent&&specialized.calls==1,"same parameter types with covariant return");
        ManyBridges many=new ManyBridges();check(bridges(ManyBridges.class,"create")==2,"multiple return bridges generated");
        check(Reflect.callMethod(many,"create").equals(42)&&many.calls==1,"all bridges collapse onto a single implementation");
        Overloads overloaded=new Overloads();boolean rejected=false;
        try{Reflect.callMethod(overloaded,"pick","text");}catch(IllegalStateException e){rejected=e.getMessage().contains("Ambiguous method");}
        check(rejected&&overloaded.calls==0,"genuine overload ambiguity remains rejected before invocation");
        rejected=false;try{Reflect.callMethod(overloaded,"pick",(Object)null);}catch(IllegalStateException e){rejected=true;}
        check(rejected&&overloaded.calls==0,"null cannot silently select between overloads");
        check(Reflect.callMethod(new PrivateChild(),"hidden").equals("inherited"),"private superclass lookup retained");
        check(Reflect.callStaticMethod(StaticTarget.class,"marker",3).equals("static:3"),"static lookup retained");
        boolean causePreserved=false;try{Reflect.callMethod(new Throwing(),"getParent");}catch(IllegalStateException e){causePreserved=e.getCause() instanceof IllegalArgumentException;}
        check(causePreserved,"invocation failure keeps original cause");
        System.out.println("Reflection: "+checks+" bridge/overload checks passed");
    }
}
