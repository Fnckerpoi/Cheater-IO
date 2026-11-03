package com.turboio.addon;
/** Durable-secret and settings doubles for the adoption suite; every production class stays real. */
final class SecretStore {static String token="token";static String get(android.content.Context app,String name){return name.equals("dsh_key")?token:"";}}
final class DshSettings {static long auth=1;static boolean locked;static long authGeneration(){return auth;}static boolean storageLocked(){return locked;}}
/**
 * Recording stand-in for the launcher workbench card publisher. The adoption suite owns the round and the
 * display coordinator; the card's own layout and single-flight policy live in tests/workbenchcard.
 */
final class DshWorkbenchCardSync {
 static int asrCalls,aaCalls;static boolean enabled;
 static void setEnabled(boolean value){enabled=value;}
 static void asr(String text,boolean finished){asrCalls++;}
 static void aa(String state,String sessionId,String answer){aaCalls++;}
}
