package com.turboio.addon;
import java.util.*;
final class SecretStore {
 static Exception modelFailure,searchFailure;static Runnable onModel,onSearch;
 static String get(android.content.Context c)throws Exception {if(onModel!=null)onModel.run();if(modelFailure!=null)throw modelFailure;return "model-test-key";}
 static String get(android.content.Context c,String key)throws Exception {if("search_key".equals(key)){if(onSearch!=null)onSearch.run();if(searchFailure!=null)throw searchFailure;return "search-test-key";}return "dsh_key".equals(key)?"test-token":"";}
 static void reset(){modelFailure=searchFailure=null;onModel=onSearch=null;}
}
final class DshSettings {
 static final String ORIGIN="https://example.com";static long auth=1;static Runnable onClosed;
 static long authGeneration(){return auth;}static boolean storageLocked(){return false;}static boolean locked(android.content.Context a){return false;}static void lockStorage(){}static void show(android.app.Activity a){}
 static String encode(String s)throws Exception{return java.net.URLEncoder.encode(s,"UTF-8");}
 static final class Policy {
  static DshDirectory.Option find(List<DshDirectory.Option> rows,String id){for(DshDirectory.Option row:rows)if(id.equals(row.id))return row;return null;}
  static String savedMessage(boolean c,String s,String before,String f,String old,String message){return message;}
 }
}
class AaEmbeddedActivity extends android.app.Activity {void settingsChanged(){}}
// The adoption host can also stop the monitor; this suite must never see either call. Arming the lens is
// only reachable from a submitted dispatch, which these bridge cases deliberately never perform.
final class DshProactiveDisplay {static DshProactiveDisplay get(android.content.Context a){return new DshProactiveDisplay();}void setHudEnabled(boolean value){}void start(DshProactiveReport.RoundIdentity r){throw new AssertionError("status must not start monitor");}void stop(){throw new AssertionError("status must not stop the monitor");}}
/**
 * Recording stand-in for the launcher card publisher. This suite owns the AA call boundary; the card's
 * layout and single-flight policy have their own suite in tests/workbenchcard.
 */
final class DshWorkbenchCardSync {
 static int aaCalls;
 static void setEnabled(boolean value){}
 static void asr(String text,boolean finished){}
 static void aa(String state,String sessionId,String answer){aaCalls++;}
}
