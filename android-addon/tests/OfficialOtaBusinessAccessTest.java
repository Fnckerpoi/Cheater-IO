package com.turboio.addon;

public final class OfficialOtaBusinessAccessTest {
 private static int checks;
 private static void yes(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
 private static boolean observe(OfficialOtaBusinessAccess a,String peer,long now,OfficialOtaGate.Phase phase,boolean recovered,boolean fresh){return a.observe(peer,now,true,true,true,true,false,false,phase,recovered,fresh,true);}
 private static boolean observe(OfficialOtaBusinessAccess a,String peer,long now,OfficialOtaGate.Phase phase,boolean recovered,boolean fresh,boolean bootEvidenceGood){return a.observe(peer,now,true,true,true,true,false,false,phase,recovered,fresh,bootEvidenceGood);}
 private static OfficialOtaBusinessAccess opened(){OfficialOtaBusinessAccess a=new OfficialOtaBusinessAccess();a.request("peer",100);yes(observe(a,"peer",103,OfficialOtaGate.Phase.UNKNOWN,true,true),"explicit confirmed readback permits business");return a;}
 private static byte[] f(int type,Object json){return OtaFrame.encode(type,json,null);}
 private static void reply(OfficialOtaGate g,int type,Object json,long at){yes(g.outbound("peer",f(type,null),at,true,false),"read-only request");g.inbound("peer",f(type,json),at+1);}
 private static Object version(){return OtaFrame.object("OsVersion",OtaProfile.TMG3_REPAIRED.version,"OtaValidationState","idle","OtaValidationConfirmed",true,"PackageTypes",java.util.Arrays.asList("files"));}
 private static void policy(){
  OfficialOtaBusinessAccess a=new OfficialOtaBusinessAccess();
  yes(!observe(a,"peer",100,OfficialOtaGate.Phase.UNKNOWN,true,true),"pending alone never grants");
  a.request("peer",100);yes(!observe(a,"peer",101,OfficialOtaGate.Phase.UNKNOWN,false,true),"recovery required");yes(!observe(a,"peer",102,OfficialOtaGate.Phase.UNKNOWN,true,false),"fresh response required");yes(observe(a,"peer",103,OfficialOtaGate.Phase.UNKNOWN,true,true),"all evidence grants");
  yes(observe(a,"peer",300000,OfficialOtaGate.Phase.UNKNOWN,false,false,true),"granted business outlives readback and manual TTL when boot evidence good");
  yes(observe(a,"peer",400000,OfficialOtaGate.Phase.INSTALLING,false,false,true),"install result remains protected while business works");
  yes(!observe(a,"peer",400001,OfficialOtaGate.Phase.INSTALLING,false,false,false),"negative boot evidence revokes granted lease");
  yes(!observe(a,"peer",400002,OfficialOtaGate.Phase.INSTALLING,false,false,true),"revoked lease cannot revive even if evidence restores");
  a=opened();
  yes(!observe(a,"",400001,OfficialOtaGate.Phase.UNKNOWN,true,true),"disconnect revokes immediately");yes(!observe(a,"peer",400002,OfficialOtaGate.Phase.UNKNOWN,true,true),"same peer reconnect never revives lease");
  a=opened();yes(!observe(a,"other",104,OfficialOtaGate.Phase.UNKNOWN,true,true),"wrong peer revokes");yes(!observe(a,"peer",105,OfficialOtaGate.Phase.UNKNOWN,true,true),"peer return needs confirmation");
  a=opened();yes(!observe(a,null,104,OfficialOtaGate.Phase.UNKNOWN,true,true),"unknown connection revokes");
  a=opened();yes(!observe(a,"peer",102,OfficialOtaGate.Phase.UNKNOWN,true,true),"clock reversal revokes");yes(!observe(a,"peer",104,OfficialOtaGate.Phase.UNKNOWN,true,true),"clock recovery cannot revive");
  a=opened();a.cancel();yes(!observe(a,"peer",104,OfficialOtaGate.Phase.UNKNOWN,true,true),"cancel revokes");
  a=opened();a.request("peer",104);yes(!observe(a,"peer",105,OfficialOtaGate.Phase.UNKNOWN,true,false),"new readback replaces old lease");
  yes(!observe(new OfficialOtaBusinessAccess(),"peer",106,OfficialOtaGate.Phase.UNKNOWN,true,true),"process restart grants nothing");
  for(OfficialOtaGate.Phase p:OfficialOtaGate.Phase.values())if(p!=OfficialOtaGate.Phase.UNKNOWN&&p!=OfficialOtaGate.Phase.INSTALLING){a=opened();yes(!observe(a,"peer",104,p,true,true),"active/new phase revokes "+p);yes(!observe(a,"peer",105,OfficialOtaGate.Phase.UNKNOWN,true,true),"new transfer lease cannot revive");}
  for(int bad=0;bad<6;bad++){a=opened();yes(!a.observe("peer",104,bad!=0,bad!=1,bad!=2,bad!=3,bad==4,bad==5,OfficialOtaGate.Phase.UNKNOWN,true,true,true),"unsafe state revokes condition "+bad);}
 }
 private static void freshReadback(){
  OfficialOtaGate g=new OfficialOtaGate(p->true,OtaProfile.TMG3_REPAIRED);g.restore("peer",0);
  reply(g,1,version(),10);reply(g,11,OtaFrame.object("Result",true),20);yes(g.freshBoot(25),"prior boot readback fresh");yes(!g.freshBootAfter(25,25),"click cannot reuse pre-click responses");
  reply(g,1,version(),30);yes(!g.freshBootAfter(32,25),"one new reply insufficient");reply(g,11,OtaFrame.object("Result",true),40);yes(g.freshBootAfter(42,25),"both new replies qualify");
  yes(!g.freshBootAfter(60042,25),"expired replies cannot grant");yes(g.bootEvidenceGood(),"expiry alone is not negative boot evidence");
  reply(g,11,OtaFrame.object("Result",false),60043);yes(!g.bootEvidenceGood(),"new negative idle reply invalidates business evidence");
  g=new OfficialOtaGate(p->true,OtaProfile.TMG3_REPAIRED);g.restore("peer",0);reply(g,1,version(),10);reply(g,11,OtaFrame.object("Result",true),20);
  yes(g.outbound("peer",f(1,null),30,true,false),"new query");g.inbound("peer",f(1,version()),15031);yes(!g.freshBootAfter(15032,25),"late ignored response cannot reuse old good reply");
  yes(g.pending(),"business readback does not clear OTA pending");yes(!g.outbound("peer",f(4,OtaFrame.object("Mode",2)),15033,true,true),"OTA start still blocked");yes(g.pending(),"blocked OTA start retains pending");
 }
 private static void malformedReadback(){
  for(int kind=0;kind<3;kind++){
   OfficialOtaGate g=new OfficialOtaGate(p->true,OtaProfile.TMG3_REPAIRED);g.restore("peer",0);
   OfficialOtaBusinessAccess a=new OfficialOtaBusinessAccess();a.request("peer",5);
   reply(g,1,version(),10);reply(g,11,OtaFrame.object("Result",true),20);
   yes(a.observe("peer",25,true,true,true,true,false,false,g.phase(),true,g.freshBootAfter(25,a.requestedAt()),g.bootEvidenceGood()),"boot replies grant before malformed case "+kind);
   int type=kind==2?11:1;
   yes(g.outbound("peer",f(type,null),30,true,false),"new query before malformed case "+kind);
   byte[] bad=kind==1?OtaFrame.encode(type,version(),new byte[]{1}):f(type,java.util.Arrays.asList("invalid"));
   g.inbound("peer",bad,31);
   yes(!a.observe("peer",32,true,true,true,true,false,false,g.phase(),true,g.freshBootAfter(32,a.requestedAt()),g.bootEvidenceGood()),"malformed reply revokes granted business case "+kind);
   yes(g.pending(),"malformed reply retains OTA pending case "+kind);
   reply(g,1,version(),40);reply(g,11,OtaFrame.object("Result",true),50);
   yes(!a.observe("peer",52,true,true,true,true,false,false,g.phase(),true,g.freshBootAfter(52,a.requestedAt()),g.bootEvidenceGood()),"restored replies cannot revive canceled business case "+kind);
  }
 }
 public static void main(String[] args){policy();freshReadback();malformedReadback();System.out.println("Official OTA business access: "+checks+" checks; offline only");}
}
