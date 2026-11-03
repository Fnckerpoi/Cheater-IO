package com.turboio.addon;

/** Exact pending-attempt identity and explicit failed-result policy. */
final class OfficialOtaAttempt {
 static OtaProfile pendingProfile(String candidate,String token){
  for(OtaProfile profile:new OtaProfile[]{OtaProfile.TMG3,OtaProfile.STOCK127,OtaProfile.TMG3_REPAIRED})if(profile.matchesJournal(candidate,token))return profile;
  return null;
 }
 static boolean commitFailure(java.util.function.BooleanSupplier commit,Runnable restore){
  try{if(commit.getAsBoolean())return true;}catch(RuntimeException error){restore.run();throw error;}
  restore.run();return false;
 }
 static boolean failureAllowed(boolean explicitFailure,boolean pending,boolean knownIdentity,boolean samePeer,boolean foreground,boolean recoveryAllowed,boolean freshBoot,boolean armed,boolean checking){
  return explicitFailure&&pending&&knownIdentity&&samePeer&&foreground&&recoveryAllowed&&freshBoot&&!armed&&!checking;
 }
}