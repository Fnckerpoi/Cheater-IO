package com.turboio.addon;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class InMemoryJournal implements SharedPreferences, SharedPreferences.Editor {
    public final Map<String, Object> map = new HashMap<String, Object>();
    private final Map<String, Object> temp = new HashMap<String, Object>();

    public Map<String, ?> getAll() { return new HashMap<String, Object>(map); }
    public String getString(String key, String def) {
        Object v = map.get(key);
        return (v instanceof String) ? (String) v : def;
    }
    public Set<String> getStringSet(String key, Set<String> def) { return def; }
    public int getInt(String key, int def) {
        Object v = map.get(key);
        return (v instanceof Integer) ? (Integer) v : def;
    }
    public long getLong(String key, long def) {
        Object v = map.get(key);
        return (v instanceof Long) ? (Long) v : def;
    }
    public float getFloat(String key, float def) {
        Object v = map.get(key);
        return (v instanceof Float) ? (Float) v : def;
    }
    public boolean getBoolean(String key, boolean def) {
        Object v = map.get(key);
        return (v instanceof Boolean) ? (Boolean) v : def;
    }
    public boolean contains(String key) { return map.containsKey(key); }
    public Editor edit() {
        temp.clear();
        temp.putAll(map);
        return this;
    }
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}

    public Editor putString(String key, String val) { temp.put(key, val); return this; }
    public Editor putStringSet(String key, Set<String> val) { return this; }
    public Editor putInt(String key, int val) { temp.put(key, val); return this; }
    public Editor putLong(String key, long val) { temp.put(key, val); return this; }
    public Editor putFloat(String key, float val) { temp.put(key, val); return this; }
    public Editor putBoolean(String key, boolean val) { temp.put(key, val); return this; }
    public Editor remove(String key) { temp.remove(key); return this; }
    public Editor clear() { temp.clear(); return this; }
    public boolean commit() {
        map.clear();
        map.putAll(temp);
        return true;
    }
    public void apply() { commit(); }
}
