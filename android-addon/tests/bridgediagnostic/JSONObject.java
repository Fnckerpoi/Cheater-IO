package org.json;
import java.util.*;import com.turboio.addon.DshClient;
public class JSONObject {
 final Map<String,Object> v;public JSONObject(){v=new LinkedHashMap<>();}public JSONObject(String s){v=DshClient.parseJsonObject(s);}public JSONObject(Map<String,Object> m){v=new LinkedHashMap<>(m);}
 static Object wrap(Object o){if(o instanceof Map)return new JSONObject((Map)o);if(o instanceof List)return new JSONArray((List)o);return o;}
 static Object raw(Object o){return o instanceof JSONObject?((JSONObject)o).v:o instanceof JSONArray?((JSONArray)o).v:o;}
 public JSONObject put(String k,Object o)throws JSONException{v.put(k,raw(o));return this;}public Object get(String k){if(!v.containsKey(k))throw new IllegalArgumentException(k);return wrap(v.get(k));}
 public JSONObject getJSONObject(String k){return (JSONObject)get(k);}public JSONArray getJSONArray(String k){return (JSONArray)get(k);}public String getString(String k){return (String)get(k);}
 public JSONObject optJSONObject(String k){Object o=wrap(v.get(k));return o instanceof JSONObject?(JSONObject)o:null;}
 public String optString(String k,String d){Object o=v.get(k);return o instanceof String?(String)o:d;}public String optString(String k){return optString(k,"");}
 public Object opt(String k){return wrap(v.get(k));}
 public boolean has(String k){return v.containsKey(k);}public int length(){return v.size();}public Iterator<String> keys(){return v.keySet().iterator();}public String toString(){return DshClient.stringifyJson(v);}
}
