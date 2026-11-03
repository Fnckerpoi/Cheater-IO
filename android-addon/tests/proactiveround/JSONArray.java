package org.json;
import java.util.*;import com.turboio.addon.DshClient;
public class JSONArray {
 final List<Object> v;public JSONArray(){v=new ArrayList<>();}public JSONArray(String s){v=(List)DshClient.parseJsonObject("{\"array\":"+s+"}").get("array");}JSONArray(List l){v=l;}
 public JSONArray put(Object o){v.add(JSONObject.raw(o));return this;}public int length(){return v.size();}public JSONObject getJSONObject(int i){return (JSONObject)JSONObject.wrap(v.get(i));}public Object get(int i){return JSONObject.wrap(v.get(i));}public String toString(){return DshClient.stringifyJson(v);}
}
