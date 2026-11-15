package android.content;
import java.util.*;
public class Context {
 private final Map<String,SharedPreferences> stores=new HashMap<>();
 public SharedPreferences getSharedPreferences(String name,int mode){if(!stores.containsKey(name))stores.put(name,new Prefs());return stores.get(name);}
 public static class Prefs implements SharedPreferences {
  final Map<String,Object> data=new HashMap<>();
  final Map<String,Object> disk=new HashMap<>(); public boolean failCommit; public int commits; public int failAtCommit = -1;
  public void restart(){data.clear();data.putAll(disk);}
  public String getString(String k,String d){return data.containsKey(k)?(String)data.get(k):d;}
  public boolean getBoolean(String k,boolean d){return data.containsKey(k)?(Boolean)data.get(k):d;}
  public long getLong(String k,long d){return data.containsKey(k)?(Long)data.get(k):d;}
  public boolean contains(String k){return data.containsKey(k);}
  public Editor edit(){return new Editor(){final Map<String,Object> changes=new HashMap<>(); boolean clearAll; public Editor clear(){clearAll=true;changes.clear();return this;}
   public Editor putString(String k,String v){changes.put(k,v);return this;} public Editor putBoolean(String k,boolean v){changes.put(k,v);return this;} public Editor putLong(String k,long v){changes.put(k,v);return this;} public Editor remove(String k){changes.put(k,null);return this;}
   public boolean commit(){commits++;if(clearAll)data.clear();for(String k:changes.keySet()){if(changes.get(k)==null)data.remove(k);else data.put(k,changes.get(k));}if(failCommit || commits == failAtCommit)return false;disk.clear();disk.putAll(data);return true;}
  };}
 }
}
