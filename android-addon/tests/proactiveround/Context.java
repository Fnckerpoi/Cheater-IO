package android.content;
import java.util.*;import java.lang.reflect.*;
public class Context {
 public final Map<String,Map<String,Object>> values=new HashMap<>();public boolean fail;
 public Context getApplicationContext(){return this;}
 public SharedPreferences getSharedPreferences(String name,int mode){final Map<String,Object> v=values.computeIfAbsent(name,k->new HashMap<>());return (SharedPreferences)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{SharedPreferences.class},(p,m,a)->{
  if(m.getName().equals("getString")||m.getName().equals("getBoolean"))return v.containsKey(a[0])?v.get(a[0]):a[1];
  if(m.getName().equals("contains"))return v.containsKey(a[0]);
  if(m.getName().equals("edit")){final Map<String,Object> changes=new HashMap<>();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{SharedPreferences.Editor.class},(e,n,b)->{if(n.getName().startsWith("put")){changes.put((String)b[0],b[1]);return e;}if(n.getName().equals("commit")){if(fail)return false;v.putAll(changes);return true;}return null;});}return null;});}
}
