package com.turboio.addon;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

/** Candidate bytes and preparation snapshots are checked independently of devices. */
public final class Tmg4OtaProfileTest {
 public static void main(String[] args)throws Exception{
  OtaProfile profile;
  try{profile=(OtaProfile)OtaProfile.class.getDeclaredField("TMG4_10611").get(null);}
  catch(NoSuchFieldException e){throw new AssertionError("TMG4 candidate identity is not registered",e);}
  if(!"TMG4-10611".equals(profile.token)||!"Strix OS 1.0.6.11".equals(profile.version))throw new AssertionError("base/custom identity mixed");
  if(!"Strix_OS_1.0.6.11".equals(profile.directory))throw new AssertionError("preparation identity");
  if(OtaProfile.fromJournal(profile.sha,profile.token)!=profile)throw new AssertionError("TMG4 journal identity missing");
  if(OtaProfile.fromJournal(OtaProfile.TMG3_10611.sha,profile.token)!=null)throw new AssertionError("history relabeled");
  if(!OfficialOtaAuthorization.tokenMatches("TMG4-10611",profile)||OfficialOtaAuthorization.tokenMatches("TMIG1",profile))throw new AssertionError("authorization identity");
  if(!profile.hasOfficialVersions()||!"0100060011".equals(profile.queryVersionName())||profile.queryVersionCode()!=100060011||!profile.acceptsSource("Strix OS 1.0.6.11")||profile.acceptsSource("wrong")||!profile.acceptsQuery("01.00.06.0011")||profile.acceptsQuery("01.00.05.0005")||profile.acceptsQuery("01.00.04.0012"))throw new AssertionError("TMG4 version evidence mismatch");
  if(!OtaProfile.TAP1.acceptsQuery("01.00.04.0012")||OtaProfile.TAP1.acceptsQuery("01.00.06.0011")||!OtaProfile.TMIG1.acceptsQuery("01.00.05.0005")||OtaProfile.TMG3_10611.hasOfficialVersions()||OtaProfile.STOCK10611.hasOfficialVersions())throw new AssertionError("historical profile changed");
  byte[] archive=Files.readAllBytes(Paths.get(args[0]));
  OtaPackage candidate=OtaPackage.verify(archive,profile);
  if(candidate.size("nuttx_ap.bin")!=profile.apSize||BoundedJson.list(candidate.manifest()).size()!=16||profile.pins().length!=17)throw new AssertionError("candidate members");
  for(OtaProfile old:new OtaProfile[]{OtaProfile.STOCK10611,OtaProfile.TMG3_10611,OtaProfile.TMIG1}){
   try{OtaPackage.verify(archive,old);throw new AssertionError("old profile accepted TMG4");}catch(IOException expected){}
  }
  Path root=Files.createTempDirectory("tmg4-preparation-");
  Path relative=Paths.get("files/ota/"+profile.directory),directory=root.resolve(relative);Files.createDirectories(directory);
  try{
   try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(archive))){ZipEntry e;while((e=in.getNextEntry())!=null)Files.copy(in,directory.resolve(e.getName()));}
   OtaPackage frozen=OtaPackage.freezePreparedDirectory(root,relative,profile);
   if(frozen.profile!=profile||frozen.size("nuttx_ap.bin")!=profile.apSize)throw new AssertionError("snapshot identity");
   byte[] first=frozen.slice("nuttx_ap.bin",0,16);Files.write(directory.resolve("nuttx_ap.bin"),new byte[]{0});
   if(!java.util.Arrays.equals(first,frozen.slice("nuttx_ap.bin",0,16)))throw new AssertionError("snapshot changed");
   try{OtaPackage.freezePreparedDirectory(root,relative,profile);throw new AssertionError("changed directory accepted");}catch(IOException expected){}
  }finally{try(java.util.stream.Stream<Path> paths=Files.walk(root)){for(Path p:(Iterable<Path>)paths.sorted(java.util.Comparator.reverseOrder())::iterator)Files.delete(p);}}
  OfficialOtaGate gate=new OfficialOtaGate(peer->{throw new AssertionError("unexpected start");},profile);gate.bind("glasses");
  if(gate.active()||gate.readyToArm(1000))throw new AssertionError("profile implicitly armed");
  if(!gate.outbound("glasses",OtaFrame.encode(1,null,null),1000,true,false))throw new AssertionError("version query blocked");
  gate.inbound("glasses",OtaFrame.encode(1,OtaFrame.object("OsVersion","Strix OS 1.0.6.11","OtaValidationState","idle","OtaValidationConfirmed",true,"PackageTypes",java.util.Arrays.asList("files")),null),1001);
  if(!gate.preflight(1002).contains("版本=true")||gate.readyToArm(1002))throw new AssertionError("measured source rejected or implicitly armed");
  if(!gate.outbound("glasses",OtaFrame.encode(1,null,null),1003,true,false))throw new AssertionError("version query blocked");
  gate.inbound("glasses",OtaFrame.encode(1,OtaFrame.object("OsVersion","wrong","OtaValidationState","idle","OtaValidationConfirmed",true,"PackageTypes",java.util.Arrays.asList("files")),null),1004);
  if(!gate.preflight(1005).contains("版本=false"))throw new AssertionError("mismatched source accepted");
  if(gate.outbound("glasses",OtaFrame.encode(4,OtaFrame.object("Mode",2),null),1001,true,true))throw new AssertionError("unauthorized start");
  try(OfficialOtaFeed feed=new OfficialOtaFeed(()->1000,()->true,false,profile)){
   int port=feed.start(0);
   String query=OfficialOtaFeed.PATHS[1]+"?packageType=firmware&packageName=strix+OS&glassesType=S3&deviceType=android&versionCode=01.00.05.0005";
   OfficialOtaFeed.Reply reply=feed.reply("GET "+query+" HTTP/1.1\r\nHost: 127.0.0.1:"+port+"\r\n\r\n");
   if(reply.code!=200||BoundedJson.map(BoundedJson.read(reply.body)).get("data")!=null)throw new AssertionError("legacy metadata leaked");
   String measured=query.replace("01.00.05.0005","01.00.06.0011");
   String req="GET "+measured+" HTTP/1.1\r\nHost: 127.0.0.1:"+port+"\r\n\r\n";
   if(BoundedJson.map(BoundedJson.read(feed.reply(req).body)).get("data")!=null)throw new AssertionError("feed implicitly armed");
   feed.arm(archive);
   java.util.Map<String,Object> meta=BoundedJson.map(BoundedJson.map(BoundedJson.read(feed.reply(req).body)).get("data"));
   if(!"0100060011".equals(meta.get("versionName"))||((Number)meta.get("versionCode")).intValue()!=100060011)throw new AssertionError("experimental response mismatch");
   if(BoundedJson.map(BoundedJson.read(feed.reply("GET "+query+" HTTP/1.1\r\nHost: 127.0.0.1:"+port+"\r\n\r\n").body)).get("data")!=null)throw new AssertionError("mismatched query advertised");
  }
  System.out.println("TMG4 archive, snapshot, historical isolation and closed OTA policy passed");
 }
}
