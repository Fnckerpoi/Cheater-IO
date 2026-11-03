package com.turboio.addon;
public final class OfficialOtaAuthorizationTest {
 public static void main(String[] args){
  int checks=0;
  OtaProfile[] profiles={OtaProfile.TAP1,OtaProfile.TMIG1,OtaProfile.TMG2,OtaProfile.TMG3};
  String[] tokens={"TAP1-TEST-01","TMIG1","TMG2-CANDIDATE-NOT-APPROVED","TMG3-CANDIDATE-NOT-APPROVED"};
  for(int p=0;p<profiles.length;p++){
   for(int t=0;t<tokens.length;t++){
    boolean matches=OfficialOtaAuthorization.tokenMatches(tokens[t],profiles[p]);
    if(matches!=(p==t))throw new AssertionError("profile token mismatch profile="+p+" token="+t);checks++;
    if(OfficialOtaAuthorization.allowed(matches,false,false,false,true,false)!=(p==t))throw new AssertionError("cross-profile authorization profile="+p+" token="+t);checks++;
   }
   String[] rejected={null,"","tmig1","TMIG1 ","private-device-secret","ＴＭＩＧ１","tmg3","TMG3","TMG3 ","ＴＭＧ３",tokens[p].toLowerCase(java.util.Locale.ROOT)," "+tokens[p],tokens[p]+" "};
   for(String input:rejected){boolean matches=OfficialOtaAuthorization.tokenMatches(input,profiles[p]);if(matches)throw new AssertionError("token weakened profile="+p);String diag=OfficialOtaAuthorization.diagnostic(matches,false,false,false,true,false);if(input!=null&&!input.isEmpty()&&diag.contains(input))throw new AssertionError("token leaked");checks++;}
  }
  for(int mask=0;mask<64;mask++){
   boolean token=(mask&1)!=0,pending=(mask&2)!=0,armed=(mask&4)!=0,checking=(mask&8)!=0,foreground=(mask&16)!=0,legacy=(mask&32)!=0;
   boolean oldCondition=!token||pending||armed||checking||!foreground||legacy;
   if(OfficialOtaAuthorization.allowed(token,pending,armed,checking,foreground,legacy)==oldCondition)throw new AssertionError("changed policy mask="+mask);checks++;
   String diagnostic=OfficialOtaAuthorization.diagnostic(token,pending,armed,checking,foreground,legacy);
   if(!diagnostic.matches("AUTH_ENTRY tokenMatches=(true|false) pending=(true|false) armed=(true|false) checking=(true|false) foreground=(true|false) legacyBusy=(true|false)"))throw new AssertionError("unbounded diagnostic");checks++;
  }
  System.out.println("Official OTA authorization: "+checks+" profile/policy/boolean privacy checks passed");
 }
}
