package com.turboio.addon;
final class SecretStore {static String get(android.content.Context app,String name){return name.equals("dsh_key")?"token":"";}}
final class DshSettings {static long auth=1;static boolean storageLocked(){return false;}static long authGeneration(){return auth;}}
/** Recording stand-in for the launcher card publisher; this suite owns the real display/lease path. */
final class DshWorkbenchCardSync {
 static int asrCalls,aaCalls;
 static void setEnabled(boolean value){}
 static void asr(String text,boolean finished){asrCalls++;}
 static void aa(String state,String sessionId,String answer){aaCalls++;}
}
