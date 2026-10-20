package com.turboio.addon;

/** First authorization interlock; diagnostics contain booleans only. */
final class OfficialOtaAuthorization {
 static boolean tokenMatches(String token){return "TMIG1".equals(token);}
 static boolean allowed(boolean tokenMatches,boolean pending,boolean armed,boolean checking,boolean foreground,boolean legacyBusy){
  return tokenMatches&&!pending&&!armed&&!checking&&foreground&&!legacyBusy;
 }
 static String diagnostic(boolean tokenMatches,boolean pending,boolean armed,boolean checking,boolean foreground,boolean legacyBusy){
  return "AUTH_ENTRY tokenMatches="+tokenMatches+" pending="+pending+" armed="+armed+" checking="+checking+" foreground="+foreground+" legacyBusy="+legacyBusy;
 }
}
