package com.turboio.addon;
import java.lang.reflect.*;import java.util.*;
public final class BridgeFixtureMain {
 static int failures,checks; static OfficialOtaGate gate;
 static void check(String n,boolean v){checks++;System.out.println((v?"PASS ":"FAIL ")+n);if(!v)failures++;}
 static void set(Class<?> c,String n,Object v)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(null,v);}
 static void set(String n,Object v)throws Exception{set(OfficialOtaBridge.class,n,v);}
 static Object get(Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(null);}
 static Object get(String n)throws Exception{return get(OfficialOtaBridge.class,n);}
 static void call(String n)throws Exception{Method m=OfficialOtaBridge.class.getDeclaredMethod(n);m.setAccessible(true);m.invoke(null);}
 static void setup()throws Exception{
  android.os.SystemClock.now=1000;FixtureSdk.peer="peer-A";FixtureSdk.sdkFailure=false;FixtureSdk.sent=0;FixtureSdk.failType=0;OtaService.alive=true;OtaService.finishCalls=0;
  OfficialOtaBridge.cancel();set("mode",true);set("initialized",true);set("pending",true);set("armed",false);set("foreground",true);set("checking",false);InMemoryJournal journal=new InMemoryJournal();journal.edit().putBoolean("pending",true).commit();set("journal",journal);
  set("savedCandidate",OtaProfile.TMG3_REPAIRED.sha);set("savedProfile",OtaProfile.TMG3_REPAIRED.token);set("savedPeer",OtaPackage.hash("SHA-256","peer-A".getBytes("UTF-8")));
  gate=new OfficialOtaGate(p->true,OtaProfile.TMG3_REPAIRED);gate.restore("peer-A",1000);set("gate",gate);
  OtaRecoveryGuard r=new OtaRecoveryGuard("peer-A",1000,true);r.observe("peer-A",1000);set("recovery",r);
  set("lease","official-ota-fixture");set(NativeTransfer.class,"messageOwner","official-ota-fixture");set(NativeTransfer.class,"writing",false);
  android.os.SystemClock.now=122000;
 }
 static FixtureSdk.Packet packet(String peer,FixtureSdk.Biz biz,byte[] bytes){return new FixtureSdk.Packet(bytes,"fixture",peer,biz,null,FixtureSdk.Priority.NORMAL,false,false,null,null);}
 static boolean allow(String peer,FixtureSdk.Biz biz){return Boolean.TRUE.equals(OfficialOtaBridge.allowQueue(packet(peer,biz,new byte[]{1})));}
 static boolean voice(){return allow("peer-A",FixtureSdk.Biz.VOICE_ASSISTANT);}
 static boolean query(int type){return Boolean.TRUE.equals(OfficialOtaBridge.allowQueue(packet("peer-A",FixtureSdk.Biz.MARS_FOTA,OtaFrame.encode(type,null,null))));}
 static void rawReply(int type,Object json,byte[] binary){OfficialOtaBridge.observe("messageReceived",OtaFrame.object("message",OtaFrame.object("businessId",9,"deviceId","peer-A","payload",OtaFrame.encode(type,json,binary))));}
 static void reply(String peer,int type,boolean good){Object j=type==1?OtaFrame.object("OsVersion",OtaProfile.TMG3_REPAIRED.version,"OtaValidationState",good?"idle":"busy","OtaValidationConfirmed",good,"PackageTypes",Arrays.asList("files")):OtaFrame.object("Result",good);OfficialOtaBridge.observe("messageReceived",OtaFrame.object("message",OtaFrame.object("businessId",9,"deviceId",peer,"payload",OtaFrame.encode(type,j,null))));}
 static void readback()throws Exception{call("readback");}
 static void finishReplies(){android.os.SystemClock.now+=1;reply("peer-A",1,true);reply("peer-A",11,true);}
 static void grant()throws Exception{setup();readback();finishReplies();}
 public static void main(String[] args)throws Exception{
  setup();check("guard enabled and initialized",Boolean.TRUE.equals(get("mode"))&&Boolean.TRUE.equals(get("initialized")));check("real repaired profile restored",get("savedProfile").equals(OtaProfile.TMG3_REPAIRED.token));check("pending busy before grant",OfficialOtaBridge.busy()&&NativeTransfer.busy());check("no grant denies voice and launcher",!voice()&&!allow("peer-A",FixtureSdk.Biz.LAUNCHER));check("pending denies file before grant",!OfficialOtaBridge.allowFile());
  readback();check("readback submits type1 and type11 through actual queue",FixtureSdk.sent==2&&gate.queryPending(1,android.os.SystemClock.now)&&gate.queryPending(11,android.os.SystemClock.now));check("submission alone denies business",!voice());android.os.SystemClock.now++;reply("peer-A",1,true);check("only type1 denies business",!voice());reply("peer-A",11,true);
  check("fresh replies allow voice",voice());check("fresh replies allow launcher",allow("peer-A",FixtureSdk.Biz.LAUNCHER));check("fresh replies clear bridge and transfer busy",!OfficialOtaBridge.busy()&&!NativeTransfer.busy());check("bridge owned lease released",get("lease").equals("")&&get(NativeTransfer.class,"messageOwner").equals(""));check("service finish invoked",OtaService.finishCalls>0&&!OtaService.alive);check("pending memory and journal retained",Boolean.TRUE.equals(get("pending"))&&((InMemoryJournal)get("journal")).getBoolean("pending",false));check("file spool remains denied",!OfficialOtaBridge.allowFile());check("file queue lanes remain denied",!allow("peer-A",FixtureSdk.Biz.FILE_TRANSFER)&&!allow("peer-A",FixtureSdk.Biz.FILE_TRANSFER_LAUNCHER));
  android.os.SystemClock.now+=61000;check("grant survives evidence TTL",voice()&&!NativeTransfer.busy());check("ordinary message lease usable",NativeTransfer.acquireMessages("ordinary"));NativeTransfer.releaseMessages("not-owner");check("wrong owner cannot release ordinary lease",get(NativeTransfer.class,"messageOwner").equals("ordinary"));NativeTransfer.releaseMessages("ordinary");
  grant();check("wrong destination denied",!allow("peer-B",FixtureSdk.Biz.VOICE_ASSISTANT));
  grant();for(int t:new int[]{4,7,9}){check("MARS type"+t+" denied",!Boolean.TRUE.equals(OfficialOtaBridge.allowQueue(packet("peer-A",FixtureSdk.Biz.MARS_FOTA,OtaFrame.encode(t,t==4?OtaFrame.object("Mode",2):OtaFrame.object(),null)))));}check("MARS attempts preserve pending and no new grant",Boolean.TRUE.equals(get("pending"))&&!Boolean.TRUE.equals(get("armed")));
  grant();query(11);android.os.SystemClock.now++;reply("peer-A",11,false);check("negative reply revokes immediately",OfficialOtaBridge.busy()&&!voice());
  grant();FixtureSdk.peer="";check("disconnect synchronously revokes busy",OfficialOtaBridge.busy());FixtureSdk.peer="peer-A";check("reconnect cannot resurrect",!voice());
  grant();FixtureSdk.sdkFailure=true;check("null SDK connection denies",OfficialOtaBridge.busy()&&!voice());FixtureSdk.sdkFailure=false;check("SDK recovery cannot resurrect",!voice());
  grant();FixtureSdk.peer="peer-B";check("changed peer denies",!allow("peer-B",FixtureSdk.Biz.VOICE_ASSISTANT));FixtureSdk.peer="peer-A";check("peer return cannot resurrect",!voice());
  setup();reply("peer-A",1,true);reply("peer-A",11,true);check("unrequested replies cannot grant",!voice());
  setup();readback();android.os.SystemClock.now+=16000;reply("peer-A",1,true);reply("peer-A",11,true);check("late replies cannot grant",!voice());
  setup();query(1);query(11);android.os.SystemClock.now++;reply("peer-A",1,true);reply("peer-A",11,true);check("fresh evidence without explicit readback cannot grant",!voice());readback();check("old consumed evidence cannot grant",!voice());finishReplies();check("new responses after readback grant",voice());
  setup();set("savedCandidate","unknown");readback();finishReplies();check("unknown candidate denies",!voice());setup();set("savedProfile","unknown");readback();finishReplies();check("unknown profile token denies",!voice());
  setup();readback();android.os.SystemClock.now++;reply("peer-B",1,true);reply("peer-B",11,true);check("wrong inbound peer cannot grant",!voice());
  setup();FixtureSdk.failType=11;readback();finishReplies();check("second query submission failure cannot grant",!voice());
  grant();set("foreground",false);check("background cancels grant",!voice());set("foreground",true);check("foreground return cannot resurrect",!voice());
  grant();Field phase=OfficialOtaGate.class.getDeclaredField("phase");phase.setAccessible(true);phase.set(gate,OfficialOtaGate.Phase.TRANSFER);check("active OTA phase cancels grant",!voice());phase.set(gate,OfficialOtaGate.Phase.UNKNOWN);check("OTA transfer end cannot resurrect",!voice());
  setup();set(NativeTransfer.class,"messageOwner","other-owner");readback();finishReplies();check("grant preserves another owners lease",get(NativeTransfer.class,"messageOwner").equals("other-owner")&&NativeTransfer.busy());
  grant();OfficialOtaBridge.cancel();check("explicit cancel revokes grant",!voice());
  grant();set("checking",true);check("new OTA checking cancels grant",!voice());
  grant();OfficialOtaBridge.serviceLost();check("service loss cancels grant",!voice());
  grant();set(NativeTransfer.class,"writing",true);check("file spool busy preserves transfer exclusion",NativeTransfer.busy()&&!NativeTransfer.acquireMessages("blocked"));check("file spool cannot bypass SDK file gate",!OfficialOtaBridge.allowFile());set(NativeTransfer.class,"writing",false);check("spool completion preserves ordinary grant",voice()&&!NativeTransfer.busy());
  grant();query(1);android.os.SystemClock.now++;rawReply(1,Arrays.asList("invalid"),null);check("malformed type1 array revokes grant",OfficialOtaBridge.busy()&&!voice());
  grant();query(1);android.os.SystemClock.now++;rawReply(1,OtaFrame.object("OsVersion",OtaProfile.TMG3_REPAIRED.version),new byte[]{1});check("type1 unexpected binary revokes grant",OfficialOtaBridge.busy()&&!voice());
  grant();query(11);android.os.SystemClock.now++;rawReply(11,Arrays.asList("invalid"),null);check("malformed type11 array revokes grant",OfficialOtaBridge.busy()&&!voice());
  setup();check("process restart pending denies",!voice()&&Boolean.TRUE.equals(get("pending")));
  System.out.println("RESULT checks="+checks+" failures="+failures);System.exit(failures==0?0:1);
 }
}
