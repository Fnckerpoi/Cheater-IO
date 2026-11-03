package com.turboio.addon;
final class SecretStore {static String token="token";static String get(android.content.Context app,String name){return name.equals("dsh_key")?token:"";}}
final class DshSettings {static long auth=1;static boolean locked;static long authGeneration(){return auth;}static boolean storageLocked(){return locked;}}
/**
 * Recording stand-in for the single display coordinator. The round suite only needs to see which
 * authorized rounds the host would hand over, so the production lease owner stays out of this
 * compilation unit; proactivedisplay/ exercises that one against the real NavGlasses contract.
 */
final class DshProactiveDisplay {
 static final DshProactiveDisplay INSTANCE=new DshProactiveDisplay();
 static int created;
 DshProactiveReport.RoundIdentity started;int starts,stops,hudCommands;Boolean hudEnabled;
 static DshProactiveDisplay get(android.content.Context app){created++;return INSTANCE;}
 static DshProactiveDisplay existing(){return INSTANCE;}
 void start(DshProactiveReport.RoundIdentity r){starts++;started=r;}
 void stop(){stops++;}
 void setHudEnabled(boolean value){hudCommands++;hudEnabled=value;}
 boolean hudEnabled(){return hudEnabled==null||hudEnabled;}
 static void reset(){INSTANCE.started=null;INSTANCE.starts=0;INSTANCE.stops=0;INSTANCE.hudCommands=0;INSTANCE.hudEnabled=null;created=0;}
}
/**
 * Recording stand-in for the launcher workbench card publisher. Its layout and single-flight policy have
 * their own suite in tests/workbenchcard; the round suite only needs to know the hook ran.
 */
final class DshWorkbenchCardSync {
 static int aaCalls;static boolean enabled;
 static void setEnabled(boolean value){enabled=value;}
 static void asr(String text,boolean finished){}
 static void aa(String state,String sessionId,String answer){aaCalls++;}
}
