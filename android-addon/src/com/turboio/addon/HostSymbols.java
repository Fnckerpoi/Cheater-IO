package com.turboio.addon;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

public final class HostSymbols {
    public static final String MANAGER_CLASS, PACKET_CLASS, BIZ_ENUM_CLASS, PRIO_ENUM_CLASS;
    public static final String ERROR_ENUM_CLASS, SMALL_FILE_CLASS, METHOD_SEND_PACKET, METHOD_SEND_FILE;
    public static final String METHOD_GET_DEVICES, FIELD_ERROR_SEND_FAILED, FIELD_SMALL_CORE, METHOD_OTA_QUEUE;
    static {
        String[] s=resolve(HostSymbols.class.getClassLoader());
        MANAGER_CLASS=s[0]; PACKET_CLASS=s[1]; BIZ_ENUM_CLASS=s[2]; PRIO_ENUM_CLASS=s[3];
        ERROR_ENUM_CLASS=s[4]; SMALL_FILE_CLASS=s[5]; METHOD_SEND_PACKET=s[6]; METHOD_SEND_FILE=s[7];
        METHOD_GET_DEVICES=s[12]; FIELD_ERROR_SEND_FAILED=s[13]; FIELD_SMALL_CORE=s[14]; METHOD_OTA_QUEUE=s[15];
    }
    private static String[] names(int version) {
        switch(version) {
            case 110:
                return new String[]{"O4.v","O4.V","a5.h","O4.c","a5.A","b5.q","t","u","a5.Q","b5.p","O4.G","O4.Y","i","MESSAGE_SEND_FAILED","SmallCore","s"};
            case 107:
                return new String[]{"Y3.u","Y3.S","j4.h","Y3.b","j4.A","k4.q","t","u","j4.Q","k4.p","Y3.C","Y3.V","g","r","b","o"};
            default:
                return new String[]{"E3.u","E3.Q","P3.h","E3.b","P3.A","Q3.q","u","v","P3.Q","Q3.p","E3.C","E3.V","g","r","b","o"};
        }
    }
    private static Class<?> cls(String n,ClassLoader l)throws ClassNotFoundException{return Class.forName(n,false,l);}
    private static boolean present(String[] n,ClassLoader l)throws ClassNotFoundException {
        try{cls(n[0],l);return true;}catch(ClassNotFoundException absent){return false;}
    }
    private static void field(Class<?> c,String n,Class<?> t,boolean stat)throws ReflectiveOperationException{
        Field f=c.getField(n);if(f.getType()!=t||Modifier.isStatic(f.getModifiers())!=stat)throw new NoSuchFieldException(c.getName()+"."+n);
    }
    private static void method(Class<?> c,String n,Class<?> r,boolean stat,Class<?>...p)throws ReflectiveOperationException{
        Method m=c.getMethod(n,p);if(m.getReturnType()!=r||Modifier.isStatic(m.getModifiers())!=stat)throw new NoSuchMethodException(c.getName()+"."+n);
    }
    private static void contract(String[] n,ClassLoader l)throws ReflectiveOperationException{
        Class<?> manager=cls(n[0],l),packet=cls(n[1],l),biz=cls(n[2],l),prio=cls(n[3],l),error=cls(n[4],l),small=cls(n[5],l),opt=cls(n[8],l),listener=cls(n[9],l),device=cls(n[10],l),cb=cls("kotlin.jvm.functions.Function2",l);
        if(!biz.isEnum()||!prio.isEnum()||!error.isEnum()||!small.isEnum()||!listener.isAssignableFrom(manager))throw new NoSuchFieldException("host enum/listener contract");
        method(biz,"valueOf",biz,true,String.class);method(prio,"valueOf",prio,true,String.class);
        field(manager,"h",manager,true);method(manager,n[12],List.class,true);method(manager,n[6],void.class,false,packet,cb);
        method(manager,n[7],String.class,false,File.class,String.class,small,String.class);
        packet.getConstructor(byte[].class,String.class,String.class,biz,opt,cls(n[3],l),boolean.class,boolean.class,listener,cb);
        field(packet,"b",String.class,false);field(packet,"c",biz,false);field(packet,"e",byte[].class,false);field(packet,"k",cb,false);
        field(error,n[13],error,true);field(small,n[14],small,true);field(device,"a",String.class,false);method(device,"b",boolean.class,false);
        method(cls(n[11],l),n[15],void.class,true,packet);
    }
    static String[] resolve(ClassLoader loader){
        try{
            String[] v110=names(110),modern=names(107),legacy=names(105);
            boolean is110=present(v110,loader),m=present(modern,loader),o=present(legacy,loader);
            int count=(is110?1:0)+(m?1:0)+(o?1:0);
            if(count!=1)throw new IllegalStateException("Unknown or ambiguous host ABI");
            String[] selected=is110?v110:(m?modern:legacy);
            contract(selected,loader);
            return selected;
        }
        catch(ReflectiveOperationException|LinkageError e){throw new IllegalStateException("Unsupported or incomplete host ABI",e);}
    }
    private HostSymbols(){}
}
