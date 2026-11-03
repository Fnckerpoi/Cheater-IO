package com.turboio.addon;

/** Independent, default-on persisted preference for the combined lens HUD. */
public final class DshCompositeHudSettings {
 public static final String KEY="dsh_composite_hud_enabled";
 public interface Store {boolean contains(String key);boolean getBoolean(String key,boolean fallback);boolean commit(String key,boolean value);}
 private DshCompositeHudSettings(){}
 public static boolean enabled(Store store){return !store.contains(KEY)||store.getBoolean(KEY,true);}
 public static boolean setEnabled(Store store,boolean enabled){return store.commit(KEY,enabled);}
}
