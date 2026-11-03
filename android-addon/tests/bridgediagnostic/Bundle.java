package android.os;
import java.util.*;
public class Bundle {
 private final Map<String,Object> data=new LinkedHashMap<>();
 public Bundle(){}public Bundle(Bundle other){data.putAll(other.data);}
 public void putString(String k,String v){data.put(k,v);}public String getString(String k){return getString(k,null);}public String getString(String k,String d){Object v=data.get(k);return v instanceof String?(String)v:d;}
 public void putLong(String k,long v){data.put(k,v);}public long getLong(String k,long d){Object v=data.get(k);return v instanceof Number?((Number)v).longValue():d;}
 public void putBoolean(String k,boolean v){data.put(k,v);}public boolean getBoolean(String k){return getBoolean(k,false);}public boolean getBoolean(String k,boolean d){Object v=data.get(k);return v instanceof Boolean?(Boolean)v:d;}
 public void putBundle(String k,Bundle v){data.put(k,v);}public <T> void putParcelableArrayList(String k,ArrayList<T> v){data.put(k,v);}
 public boolean containsKey(String k){return data.containsKey(k);}public Set<String> keySet(){return data.keySet();}public Object get(String k){return data.get(k);}public String toString(){return data.toString();}
}
