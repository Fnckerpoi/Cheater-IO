package com.turboio.addon;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

/** TMG4.3 candidate bytes and preparation snapshots are checked independently of devices. */
public final class Tmg43OtaProfileTest {
 public static void main(String[] args)throws Exception{
  OtaProfile profile;
  try{profile=(OtaProfile)OtaProfile.class.getDeclaredField("TMG4_3_10611").get(null);}
  catch(NoSuchFieldException e){throw new AssertionError("TMG4.3 candidate identity is not registered",e);}
  if(!"TMG4.3-10611".equals(profile.token)||!OtaProfile.TMG4_1_10611.version.equals(profile.version))throw new AssertionError("base/custom identity mixed");
  if(!OtaProfile.TMG4_1_10611.directory.equals(profile.directory))throw new AssertionError("preparation identity");
  if(OtaProfile.fromJournal(profile.sha,profile.token)!=profile)throw new AssertionError("TMG4.3 journal identity missing");
  if(OtaProfile.fromJournal(OtaProfile.TMG4_1_10611.sha,profile.token)!=null||OtaProfile.fromJournal(OtaProfile.TMG4_1_10611.sha,OtaProfile.TMG4_1_10611.token)!=OtaProfile.TMG4_1_10611)throw new AssertionError("history relabeled");
  if(!OfficialOtaAuthorization.tokenMatches("TMG4.3-10611",profile)||OfficialOtaAuthorization.tokenMatches("TMG4.1-10611",profile))throw new AssertionError("authorization identity");
  if(!profile.hasOfficialVersions()||!"0100060011".equals(profile.queryVersionName())||profile.queryVersionCode()!=100060011||!profile.acceptsSource(OtaProfile.TMG4_1_10611.version)||profile.acceptsSource("wrong")||!profile.acceptsQuery("01.00.06.0011")||profile.acceptsQuery("01.00.05.0005"))throw new AssertionError("TMG4.3 version evidence mismatch");
  byte[] archive=Files.readAllBytes(Paths.get(args[0]));
  OtaPackage candidate=OtaPackage.verify(archive,profile);
  if(candidate.size("nuttx_ap.bin")!=profile.apSize||BoundedJson.list(candidate.manifest()).size()!=16||profile.pins().length!=17)throw new AssertionError("candidate members");
  for(OtaProfile old:new OtaProfile[]{OtaProfile.STOCK10611,OtaProfile.TMG3_10611,OtaProfile.TMIG1,OtaProfile.TMG4_10611,OtaProfile.TMG4_1_10611}){
   try{OtaPackage.verify(archive,old);throw new AssertionError("old profile accepted TMG4.3");}catch(IOException expected){}
  }
  Path root=Files.createTempDirectory("tmg43-preparation-");
  Path relative=Paths.get("files/ota/"+profile.directory),directory=root.resolve(relative);Files.createDirectories(directory);
  try{
   try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(archive))){ZipEntry e;while((e=in.getNextEntry())!=null)Files.copy(in,directory.resolve(e.getName()));}
   OtaPackage frozen=OtaPackage.freezePreparedDirectory(root,relative,profile);
   if(frozen.profile!=profile||frozen.size("nuttx_ap.bin")!=profile.apSize)throw new AssertionError("snapshot identity");
  }finally{try(java.util.stream.Stream<Path> paths=Files.walk(root)){for(Path p:(Iterable<Path>)paths.sorted(java.util.Comparator.reverseOrder())::iterator)Files.delete(p);}}
  OfficialOtaGate gate=new OfficialOtaGate(peer->{throw new AssertionError("unexpected start");},profile);gate.bind("glasses");
  if(!gate.outbound("glasses",OtaFrame.encode(1,null,null),1000,true,false))throw new AssertionError("version query blocked");
  gate.inbound("glasses",OtaFrame.encode(1,OtaFrame.object("OsVersion",OtaProfile.TMG4_1_10611.version,"OtaValidationState","idle","OtaValidationConfirmed",true,"PackageTypes",java.util.Arrays.asList("files")),null),1001);
  if(!gate.preflight(1002).contains("版本=true")||gate.readyToArm(1002))throw new AssertionError("measured source rejected or implicitly armed");
  try(OfficialOtaFeed feed=new OfficialOtaFeed(()->1000,()->true,false,profile)){
   int port=feed.start(0);
   String req="GET "+OfficialOtaFeed.PATHS[1]+"?packageType=firmware&packageName=strix+OS&glassesType=S3&deviceType=android&versionCode=01.00.06.0011 HTTP/1.1\r\nHost: 127.0.0.1:"+port+"\r\n\r\n";
   feed.arm(archive);
   java.util.Map<String,Object> meta=BoundedJson.map(BoundedJson.map(BoundedJson.read(feed.reply(req).body)).get("data"));
   if(!"0100060011".equals(meta.get("versionName"))||((Number)meta.get("versionCode")).intValue()!=100060011)throw new AssertionError("experimental response mismatch");
  }
  System.out.println("TMG4.3 archive, snapshot, historical isolation and closed OTA policy passed");
 }
}
