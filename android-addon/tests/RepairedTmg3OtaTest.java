package com.turboio.addon;
import java.io.*;import java.net.URI;import java.nio.file.*;import java.util.*;import java.util.zip.*;
public final class RepairedTmg3OtaTest {
 static int checks;
 static void ok(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
 static void reject(byte[] b,OtaProfile p)throws Exception{try{OtaPackage.verify(b,p);throw new AssertionError("wrong archive accepted");}catch(IOException good){checks++;}}
 static String req(int port,String path){return "GET "+path+" HTTP/1.1\r\nHost: 127.0.0.1:"+port+"\r\n\r\n";}
 public static void main(String[] args)throws Exception{
  OtaProfile p=(OtaProfile)OtaProfile.class.getDeclaredField("TMG3_REPAIRED").get(null);
  byte[] zip=Files.readAllBytes(Paths.get(args[0])),stock=Files.readAllBytes(Paths.get(args[1])),old=Files.readAllBytes(Paths.get(args[2]));
  ok(zip.length==8869710&&OtaPackage.hash("SHA-256",zip).equals("adf7a6fa6531c6246e0a067343310ac4c662ddd5b76fbe3aba457a8c4ac68c87"),"candidate identity");
  OtaPackage pack=OtaPackage.verify(zip,p);ok(pack.size("nuttx_ap.bin")==7515400,"AP size");
  try(ZipFile z=new ZipFile(args[0]);ZipFile s=new ZipFile(args[1])){ok(z.size()==16&&s.size()==16,"member count"); for(Object row:BoundedJson.list(pack.manifest())){String name=(String)BoundedJson.map(row).get("Name");byte[] b=read(z.getInputStream(z.getEntry(name)));ok(b.length==pack.size(name),"size "+name);for(int pos=0;pos<b.length;pos+=51200){int n=Math.min(51200,b.length-pos);ok(Arrays.equals(Arrays.copyOfRange(b,pos,pos+n),pack.slice(name,pos,n)),"slice "+name);}if(!name.equals("nuttx_ap.bin"))ok(Arrays.equals(b,read(s.getInputStream(s.getEntry(name)))),"stock companion "+name);}}
  reject(stock,p);reject(old,p);reject(zip,OtaProfile.STOCK127);reject(zip,OtaProfile.TMG3);byte[] changed=zip.clone();changed[400]^=1;reject(changed,p);
  for(OtaProfile q:new OtaProfile[]{OtaProfile.TMG3,OtaProfile.STOCK127,p}){ok(OfficialOtaAttempt.pendingProfile(q.sha,q.token)==q,"pending identity");ok(OfficialOtaAttempt.pendingProfile(q.sha,"wrong")==null,"bad pending token");ok(OfficialOtaAttempt.pendingProfile("wrong",q.token)==null,"bad pending hash");}
  ok(OfficialOtaAttempt.pendingProfile(OtaProfile.TMIG1.sha,OtaProfile.TMIG1.token)==null,"unknown identity");
  ok(OfficialOtaAuthorization.tokenMatches("TMG3-REPAIRED-127-01",p),"token");ok(!OfficialOtaAuthorization.tokenMatches(OtaProfile.TMG3.token,p)&&!OfficialOtaAuthorization.tokenMatches(OtaProfile.STOCK127.token,p),"token isolation");
  for(int mask=0;mask<64;mask++){boolean[] a=new boolean[6];for(int n=0;n<6;n++)a[n]=(mask&(1<<n))!=0;ok(OfficialOtaAuthorization.allowed(a[0],a[1],a[2],a[3],a[4],a[5])==(a[0]&&!a[1]&&!a[2]&&!a[3]&&a[4]&&!a[5]),"authorization");}
  String query="?packageType=firmware&packageName=strix+OS&glassesType=S3&deviceType=android&versionCode=01.00.05.0012";Map<String,String> q=OfficialOtaFeed.query("/g/test"+query);ok(OfficialOtaFeed.firmware(q,p),"query");for(String k:new String[]{"packageType","packageName","glassesType","deviceType","versionCode"}){Map<String,String> x=new HashMap<>(q);x.put(k,"wrong");ok(!OfficialOtaFeed.firmware(x,p),"query "+k);}q.put("versionCode","01.00.04.0012");ok(!OfficialOtaFeed.firmware(q,p),"old query rejected");
  try(OfficialOtaFeed f=new OfficialOtaFeed(()->1000,()->true,true,p)){int port=f.start(0);f.arm(zip);Map<String,Object> meta=BoundedJson.map(BoundedJson.map(BoundedJson.read(f.reply(req(port,OfficialOtaFeed.PATHS[0]+query)).body)).get("data"));ok("0100050012".equals(meta.get("versionName"))&&((Number)meta.get("versionCode")).intValue()==100050012,"feed version");ok(((String)meta.get("updateDesc")).contains("repaired offline candidate"),"description");ok(((String)meta.get("taskName")).startsWith(p.token),"feed identity");String path=new URI((String)meta.get("downloadUrl")).getRawPath();ok(Arrays.equals(zip,f.reply(req(port,path)).body),"download bytes");f.disarm();ok(f.reply(req(port,path)).code==404,"disarm");}
  RepairedGateTest.main(args);System.out.println("Repaired OTA: "+checks+" checks passed; offline only");
 }
 static byte[] read(InputStream in)throws IOException{try(InputStream x=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=x.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
}

final class RepairedGateTest {
 static OtaPackage pkg;static int checks,commits;static long now=1000;
 static void yes(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static byte[] f(int t,Object j){return OtaFrame.encode(t,j,null);}
 static Object version(){return OtaFrame.object("OsVersion",OtaProfile.TMG3_REPAIRED.version,"OtaValidationState","idle","OtaValidationConfirmed",true,"PackageTypes",Arrays.asList("files"));}
 static OfficialOtaGate gate(boolean durable){OfficialOtaGate g=new OfficialOtaGate(p->{commits++;return durable;},OtaProfile.TMG3_REPAIRED);g.bind("peer");return g;}
 static void read(OfficialOtaGate g,int type,Object req,Object reply){yes(g.outbound("peer",f(type,req),now++,true,true));g.inbound("peer",f(type,reply),now++);}
 static void checks(OfficialOtaGate g){read(g,1,null,version());read(g,2,null,OtaFrame.object("BatteryLevel",80));read(g,3,OtaFrame.object("OtaSize",OtaProfile.TMG3_REPAIRED.zipSize),OtaFrame.object("Result",true));read(g,11,null,OtaFrame.object("Result",true));}
 static void arm(OfficialOtaGate g){g.arm(pkg,"peer","TMG3-REPAIRED-127-01",now++);}
 static void start(OfficialOtaGate g){yes(g.outbound("peer",f(4,OtaFrame.object("Mode",2)),now++,true,true));g.inbound("peer",f(4,OtaFrame.object("Result",true)),now++);}
 static void manifest(OfficialOtaGate g){g.inbound("peer",f(5,null),now++);yes(g.outbound("peer",f(5,OtaFrame.object("OtaVersion",OtaProfile.TMG3_REPAIRED.version)),now++,true,true));g.inbound("peer",f(6,OtaFrame.object("OtaVersion",OtaProfile.TMG3_REPAIRED.version)),now++);yes(g.outbound("peer",f(6,pkg.manifest()),now++,true,true));}
 static OfficialOtaGate transfer(){OfficialOtaGate g=gate(true);checks(g);arm(g);start(g);manifest(g);return g;}
 static void chunk(OfficialOtaGate g,int at,int size){Object r=OtaFrame.object("Name","nuttx_ap.bin","Start",at,"Size",size);g.inbound("peer",f(7,r),now++);yes(g.outbound("peer",OtaFrame.encode(7,r,pkg.slice("nuttx_ap.bin",at,size)),now++,true,true));}
 public static void main(String[] args)throws Exception{
  pkg=OtaPackage.verify(Files.readAllBytes(Paths.get(args[0])),OtaProfile.TMG3_REPAIRED);
  OfficialOtaGate source=gate(true);
  read(source,1,null,OtaFrame.object("OsVersion",OtaProfile.TMG3_REPAIRED.version,"OtaValidationState","idle","OtaValidationConfirmed",true,"PackageTypes",Arrays.asList("files")));read(source,2,null,OtaFrame.object("BatteryLevel",80));read(source,11,null,OtaFrame.object("Result",true));yes(source.readyToArm(now));yes(!source.freshBoot(now));
  OfficialOtaGate pre=gate(true);yes(!pre.readyToArm(now));
  read(pre,1,null,version());read(pre,11,null,OtaFrame.object("Result",true));yes(!pre.readyToArm(now));
  read(pre,2,null,OtaFrame.object("BatteryLevel",49));yes(!pre.readyToArm(now));
  read(pre,2,null,OtaFrame.object("BatteryLevel",72));yes(pre.readyToArm(now));
  yes(!pre.pending()&&pre.phase()==OfficialOtaGate.Phase.LOCKED);now+=120001;yes(!pre.readyToArm(now));
  pre=gate(true);checks(pre);yes(pre.readyToArm(now));
  yes(pre.outbound("peer",f(11,null),now++,true,true));yes(pre.readyToArm(now));
  pre.inbound("peer",f(11,OtaFrame.object("Result",false)),now++);yes(!pre.readyToArm(now));
  read(pre,11,null,OtaFrame.object("Result",true));yes(pre.readyToArm(now));
  yes(pre.outbound("peer",f(11,null),now++,true,true));now+=120001;yes(!pre.readyToArm(now));
  pre=gate(true);checks(pre);arm(pre);yes(pre.outbound("peer",f(11,null),now++,true,true));
  start(pre);yes(pre.pending());yes(!pre.freshBoot(now));commits=0; // official Mode2 races a redundant poll, within old reply TTL
  pre=gate(true);checks(pre);now+=70000;yes(pre.readyToArm(now));
  read(pre,1,null,version());read(pre,2,null,OtaFrame.object("BatteryLevel",72));
  now+=50001;yes(!pre.readyToArm(now)); // idle is expired even though battery/version are fresh
  for(int type=1;type<=12;type++){
   OfficialOtaGate locked=gate(true);boolean allowed=locked.outbound("peer",f(type,null),now++,true,true);
   yes(allowed==(type==1||type==2||type==11));yes(!locked.pending());
  }
  for(int type=4;type<=10;type++){
   OfficialOtaGate restored=gate(true);restored.restore("peer",now++);
   yes(!restored.outbound("peer",f(type,null),now++,true,true));yes(restored.pending());
  }
  OfficialOtaGate g=gate(true);checks(g);yes(!g.outbound("peer",f(4,OtaFrame.object("Mode",2)),now++,true,true));yes(commits==0&&!g.pending());
  g=gate(false);checks(g);arm(g);yes(!g.outbound("peer",f(4,OtaFrame.object("Mode",2)),now++,true,true));yes(!g.pending());
  g=gate(true);arm(g);yes(!g.outbound("peer",f(4,OtaFrame.object("Mode",2)),now++,true,true));
  for(int variant=0;variant<6;variant++){
   g=gate(true);checks(g);arm(g);if(variant==0)now+=900001;if(variant==1)now+=120001;
   yes(!g.outbound(variant==2?"other":"peer",f(4,OtaFrame.object("Mode",variant==3?1:2)),now++,variant!=4,variant!=5));yes(!g.pending());
  }
  g=gate(true);checks(g);read(g,2,null,OtaFrame.object("BatteryLevel",49));arm(g);yes(!g.outbound("peer",f(4,OtaFrame.object("Mode",2)),now++,true,true));
  g=gate(true);checks(g);arm(g);start(g);yes(!g.outbound("peer",f(4,OtaFrame.object("Mode",2)),now++,true,true));yes(g.pending()&&g.phase()==OfficialOtaGate.Phase.UNKNOWN);
  g=gate(true);checks(g);arm(g);start(g);g.inbound("peer",f(5,null),now++);yes(!g.outbound("peer",f(5,OtaFrame.object("OtaVersion","wrong")),now++,true,true));
  g=gate(true);checks(g);arm(g);start(g);g.inbound("peer",f(5,null),now++);yes(g.outbound("peer",f(5,OtaFrame.object("OtaVersion",OtaProfile.TMG3_REPAIRED.version)),now++,true,true));g.inbound("peer",f(6,OtaFrame.object("OtaVersion",OtaProfile.TMG3_REPAIRED.version)),now++);List<Object> bad=BoundedJson.list(pkg.manifest());BoundedJson.map(bad.get(0)).put("BurnAddr",1);yes(!g.outbound("peer",f(6,bad),now++,true,true));
  g=transfer();for(Object value:BoundedJson.list(pkg.manifest())){String member=(String)BoundedJson.map(value).get("Name");Object part=OtaFrame.object("Name",member,"Start",0,"Size",Math.min(100,pkg.size(member)));g.inbound("peer",f(7,part),now++);yes(g.outbound("peer",OtaFrame.encode(7,part,pkg.slice(member,0,Math.min(100,pkg.size(member)))),now++,true,true));}
  g=transfer();Object bt=OtaFrame.object("Name","bt_controller.bin","Start",0,"Size",51200);g.inbound("peer",f(7,bt),now++);yes(g.outbound("peer",OtaFrame.encode(7,bt,pkg.slice("bt_controller.bin",0,51200)),now++,true,true));yes(g.covered()==0);
  g=transfer();Object r=OtaFrame.object("Name","nuttx_ap.bin","Start",0,"Size",100);g.inbound("peer",f(7,r),now++);byte[] data=pkg.slice("nuttx_ap.bin",0,100);data[2]^=1;yes(!g.outbound("peer",OtaFrame.encode(7,r,data),now++,true,true));
  g=transfer();yes(!g.outbound("peer",OtaFrame.encode(7,r,pkg.slice("nuttx_ap.bin",0,100)),now++,true,true));
  g=transfer();g.inbound("peer",f(7,OtaFrame.object("Name","../nuttx_ap.bin","Start",0,"Size",1)),now++);yes(g.phase()==OfficialOtaGate.Phase.UNKNOWN);
  g=transfer();g.inbound("peer",f(9,null),now++);yes(g.phase()==OfficialOtaGate.Phase.UNKNOWN);
  g=transfer();chunk(g,0,100);chunk(g,0,100);yes(g.covered()==100);g.tick("",now++);yes(g.phase()==OfficialOtaGate.Phase.UNKNOWN);yes(!g.outbound("peer",f(7,r),now++,true,true));
  g=transfer();g.tick("peer",now+60001);yes(g.phase()==OfficialOtaGate.Phase.UNKNOWN);
  g=gate(true);g.restore("peer",now++);read(g,1,null,version());read(g,11,null,OtaFrame.object("Result",true));yes(g.freshBoot(now));yes(!g.outbound("peer",f(4,OtaFrame.object("Mode",2)),now++,true,true));
  g=gate(true);g.inbound("peer",f(1,version()),now++);yes(!g.freshBoot(now)); // unsolicited replies are not evidence
  g=gate(true);yes(g.outbound("peer",f(1,null),now++,true,true));now+=15001;g.inbound("peer",f(1,version()),now++);read(g,11,null,OtaFrame.object("Result",true));yes(!g.freshBoot(now));
  g=transfer();chunk(g,0,10);g.tick("peer",now-100);yes(g.phase()==OfficialOtaGate.Phase.UNKNOWN);
  g=transfer();g.inbound("peer",f(7,r),now++);g.inbound("peer",f(7,r),now++);yes(g.phase()==OfficialOtaGate.Phase.UNKNOWN);
  g=transfer();for(int at=0;at<OtaProfile.TMG3_REPAIRED.apSize;at+=51200)chunk(g,at,Math.min(51200,OtaProfile.TMG3_REPAIRED.apSize-at));yes(g.covered()==OtaProfile.TMG3_REPAIRED.apSize);g.inbound("peer",f(9,null),now++);yes(g.phase()==OfficialOtaGate.Phase.INSTALLING&&g.pending());g.tick("",now++);yes(g.phase()==OfficialOtaGate.Phase.INSTALLING);
  g=transfer();for(int at=0;at<OtaProfile.TMG3_REPAIRED.apSize;at+=51200)chunk(g,at,Math.min(51200,OtaProfile.TMG3_REPAIRED.apSize-at));
  read(g,1,null,version());read(g,11,null,OtaFrame.object("Result",true));yes(!g.freshBoot(now));
  g.inbound("peer",f(9,null),now++);yes(g.phase()==OfficialOtaGate.Phase.INSTALLING);yes(!g.freshBoot(now));
  read(g,1,null,version());yes(!g.freshBoot(now));read(g,11,null,OtaFrame.object("Result",true));yes(g.freshBoot(now));
  read(g,1,null,OtaFrame.object("OsVersion",OtaPackage.VERSION,"OtaValidationState","idle","OtaValidationConfirmed",true,"PackageTypes",Arrays.asList("files")));yes(!g.freshBoot(now));
  read(g,1,null,version());yes(g.freshBoot(now));now+=60001;yes(!g.freshBoot(now));
  read(g,11,null,OtaFrame.object("Result",true));yes(!g.freshBoot(now));read(g,1,null,version());yes(g.freshBoot(now));
  yes(g.phase()==OfficialOtaGate.Phase.INSTALLING&&g.pending());
  System.out.println("Official OTA passive gate: "+checks+" assertions passed. Simulated wire only; no hardware sends.");
 }
}
