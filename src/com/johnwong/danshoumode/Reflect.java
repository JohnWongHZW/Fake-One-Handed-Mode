package com.johnwong.danshoumode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Small reflection utility independent of either Xposed API generation. */
final class Reflect {
    private Reflect() {}
    static Class<?> findClass(String name, ClassLoader loader) {
        try { return Class.forName(name, false, loader); }
        catch (ClassNotFoundException e) { throw new IllegalStateException(name, e); }
    }
    static Field findField(Class<?> type, String name) {
        for (Class<?> c=type; c!=null; c=c.getSuperclass()) {
            try { Field f=c.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new IllegalStateException("Missing field " + type.getName()+"."+name);
    }
    static Object getObjectField(Object object,String name) {
        try { return findField(object.getClass(),name).get(object); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
    static int getIntField(Object object,String name) { return (Integer)getObjectField(object,name); }
    static Method findMethodExact(Class<?> type,String name,Class<?>... args) {
        for(Class<?> c=type;c!=null;c=c.getSuperclass()) {
            try { Method m=c.getDeclaredMethod(name,args);m.setAccessible(true);return m; }
            catch(NoSuchMethodException ignored) {}
        }
        throw new IllegalStateException("Missing method " + type.getName()+"."+name);
    }
    static List<Method> named(Class<?> type,String name) {
        List<Method> out=new ArrayList<>();
        for(Class<?> c=type;c!=null;c=c.getSuperclass())
            for(Method m:c.getDeclaredMethods()) if(m.getName().equals(name))out.add(m);
        return out;
    }
    static Object callMethod(Object object,String name,Object... args) {
        return call(object.getClass(),object,name,args);
    }
    static Object callStaticMethod(Class<?> type,String name,Object... args) { return call(type,null,name,args); }
    private static Object call(Class<?> type,Object object,String name,Object[] args) {
        // Search the most-derived declaration first. Reject ambiguous overloads.
        for(Class<?> c=type;c!=null;c=c.getSuperclass()) {
            List<Method> matches=new ArrayList<>();
            for(Method m:c.getDeclaredMethods()) {
                if(!m.getName().equals(name)||m.getParameterCount()!=args.length||
                        (object==null&&!Modifier.isStatic(m.getModifiers())))continue;
                Class<?>[] p=m.getParameterTypes();boolean compatible=true;
                for(int i=0;i<p.length;i++)if(args[i]==null?p[i].isPrimitive():!boxed(p[i]).isInstance(args[i]))compatible=false;
                if(compatible)matches.add(m);
            }
            Method candidate=null;
            for(Method m:matches) {
                // Covariant returns produce getParent():WindowContainer plus a bridge
                // getParent():ConfigurationContainer. They are one operation, not overloads.
                if(isReturnBridge(m,matches))continue;
                if(candidate!=null)throw new IllegalStateException("Ambiguous method "+name+": "+matches);
                candidate=m;
            }
            if(candidate!=null) {
                try { candidate.setAccessible(true);return candidate.invoke(object,args); }
                catch(InvocationTargetException e) { throw new IllegalStateException(name+": "+e.getCause(),e.getCause()); }
                catch(ReflectiveOperationException e) { throw new IllegalStateException(name,e); }
            }
        }
        throw new IllegalStateException("Missing method "+type.getName()+"."+name);
    }
    private static boolean isReturnBridge(Method method,List<Method> matches) {
        if(!method.isBridge())return false;
        for(Method real:matches) {
            if(!real.isBridge()&&Arrays.equals(method.getParameterTypes(),real.getParameterTypes())&&
                    Modifier.isStatic(method.getModifiers())==Modifier.isStatic(real.getModifiers())&&
                    method.getReturnType().isAssignableFrom(real.getReturnType()))return true;
        }
        return false;
    }
    private static Class<?> boxed(Class<?> c) {
        if(c==int.class)return Integer.class;if(c==boolean.class)return Boolean.class;
        if(c==long.class)return Long.class;if(c==float.class)return Float.class;if(c==double.class)return Double.class;
        if(c==byte.class)return Byte.class;if(c==short.class)return Short.class;if(c==char.class)return Character.class;
        return c;
    }
}
