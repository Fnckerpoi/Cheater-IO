package com.turboio.addon;
public final class OfficialOtaAuthorizationTest {
 public static void main(String[] args){
  int checks=0;
  for(int mask=0;mask<64;mask++){
   boolean token=(mask&1)!=0,pending=(mask&2)!=0,armed=(mask&4)!=0,checking=(mask&8)!=0,foreground=(mask&16)!=0,legacy=(mask&32)!=0;
   boolean oldCondition=!token||pending||armed||checking||!foreground||legacy;
   if(OfficialOtaAuthorization.allowed(token,pending,armed,checking,foreground,legacy)==oldCondition)throw new AssertionError("changed policy mask="+mask);checks++;
   String diagnostic=OfficialOtaAuthorization.diagnostic(token,pending,armed,checking,foreground,legacy);
   if(!diagnostic.matches("AUTH_ENTRY tokenMatches=(true|false) pending=(true|false) armed=(true|false) checking=(true|false) foreground=(true|false) legacyBusy=(true|false)"))throw new AssertionError("unbounded diagnostic");checks++;
  }
  String[] rejected={null,"","tmig1","TMIG1 ","private-device-secret","ＴＭＩＧ１"};
  for(String input:rejected){if(OfficialOtaAuthorization.tokenMatches(input))throw new AssertionError("token weakened");String diag=OfficialOtaAuthorization.diagnostic(OfficialOtaAuthorization.tokenMatches(input),false,false,false,true,false);if(input!=null&&!input.isEmpty()&&diag.contains(input))throw new AssertionError("token leaked");checks++;}
  if(!OfficialOtaAuthorization.tokenMatches("TMIG1"))throw new AssertionError("valid token refused");checks++;
  System.out.println("Official OTA authorization: "+checks+" policy/boolean privacy checks passed");
 }
}
