package org.json;

import com.turboio.addon.BoundedJson;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JSONObject {
    private final Map<String,Object> values;
    public JSONObject() { values = new LinkedHashMap<>(); }
    public JSONObject(String json) { values = BoundedJson.map(BoundedJson.read(json.getBytes(StandardCharsets.UTF_8))); }
    public JSONObject put(String key, Object value) { values.put(key, value instanceof JSONObject ? ((JSONObject)value).values : value); return this; }
    public Object opt(String key) { return values.get(key); }
    public String optString(String key) { Object value = opt(key); return value == null ? "" : String.valueOf(value); }
    @Override public String toString() { return new String(BoundedJson.canonical(values), StandardCharsets.UTF_8); }
}
