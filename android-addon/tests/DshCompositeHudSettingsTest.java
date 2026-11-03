package com.turboio.addon;
import java.util.*;
public final class DshCompositeHudSettingsTest {
 static int checks;
 static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message+" (check "+checks+")");}
 static final class Fake implements DshCompositeHudSettings.Store {
  final Map<String,Boolean> values=new HashMap<>();boolean allow=true;
  public boolean contains(String key){return values.containsKey(key);}
  public boolean getBoolean(String key,boolean fallback){return values.containsKey(key)?values.get(key):fallback;}
  public boolean commit(String key,boolean value){if(allow)values.put(key,value);return allow;}
 }
 public static void main(String[] args){
  Fake prefs=new Fake();
  ok(DshCompositeHudSettings.enabled(prefs),"missing preference defaults on");
  ok("dsh_composite_hud_enabled".equals(DshCompositeHudSettings.KEY),"one independent combined preference key");
  ok(DshCompositeHudSettings.setEnabled(prefs,false),"off persists");
  ok(!DshCompositeHudSettings.enabled(prefs),"persisted off reads back");
  ok(DshCompositeHudSettings.setEnabled(prefs,true),"on persists");
  ok(DshCompositeHudSettings.enabled(prefs),"persisted on reads back");
  prefs.allow=false;ok(!DshCompositeHudSettings.setEnabled(prefs,false),"failed commit is reported");
  ok(DshCompositeHudSettings.enabled(prefs),"failed commit leaves prior value");
  System.out.println("DshCompositeHudSettingsTest passed "+checks+" checks");
 }
}
