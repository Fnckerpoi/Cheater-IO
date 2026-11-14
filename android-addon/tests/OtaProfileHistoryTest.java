package com.turboio.addon;

/** Pending journals must resolve their original profile, including after an app upgrade. */
public final class OtaProfileHistoryTest {
 public static void main(String[] args)throws Exception{
  java.lang.reflect.Method lookup;
  try{lookup=OtaProfile.class.getDeclaredMethod("fromJournal",String.class,String.class);}
  catch(NoSuchMethodException e){throw new AssertionError("pending journal has no independent profile resolution",e);}
  for(OtaProfile p:new OtaProfile[]{OtaProfile.TMIG1,OtaProfile.TMG3_10611}){
   if(lookup.invoke(null,p.sha,p.token)!=p)throw new AssertionError("historical identity lost");
   if(lookup.invoke(null,"bad",p.token)!=null)throw new AssertionError("wrong candidate accepted");
   if(lookup.invoke(null,p.sha,"")!=null)throw new AssertionError("missing profile accepted");
  }
  OtaProfile original=(OtaProfile)lookup.invoke(null,OtaProfile.TMIG1.sha,"TMIG1");
  OfficialOtaGate gate=new OfficialOtaGate(peer->{throw new AssertionError("recovery started a new transfer");},original);
  gate.restore("same-glasses",1000);
  read(gate,1,OtaFrame.object("OsVersion","Strix OS 1.0.5.5","OtaValidationState","idle","OtaValidationConfirmed",true,"PackageTypes",java.util.Arrays.asList("files")),2000);
  read(gate,11,OtaFrame.object("Result",true),2100);
  if(!gate.freshBoot(2200))throw new AssertionError("legacy boot recovery unavailable");
  if(gate.outbound("same-glasses",OtaFrame.encode(4,OtaFrame.object("Mode",2),null),2300,true,true))throw new AssertionError("history permitted restart");
  System.out.println("OTA historical profile and recovery checks passed");
 }
 static void read(OfficialOtaGate gate,int type,Object response,long now){
  if(!gate.outbound("same-glasses",OtaFrame.encode(type,null,null),now,true,false))throw new AssertionError("read blocked");
  gate.inbound("same-glasses",OtaFrame.encode(type,response,null),now+1);
 }
}
