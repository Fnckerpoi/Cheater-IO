package com.turboio.addon;
import java.nio.file.*;
import java.util.*;
import java.net.URI;
import java.util.concurrent.atomic.*;
public final class Tmg3OtaFeedTest {
 static int checks;
 static void ok(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
 static String request(int port,String target){return "GET "+target+" HTTP/1.1\r\nHost: 127.0.0.1:"+port+"\r\n\r\n";}
 static Object data(OfficialOtaFeed.Reply reply){ok(reply.code==200);return BoundedJson.map(BoundedJson.read(reply.body)).get("data");}
 public static void main(String[] args)throws Exception{
  byte[] zip=Files.readAllBytes(Paths.get(args[0]));AtomicLong clock=new AtomicLong(1000);AtomicBoolean guard=new AtomicBoolean(true);
  OtaProfile profile=OtaProfile.TMG3;
  try(OfficialOtaFeed feed=new OfficialOtaFeed(clock::get,guard::get,false,profile)){
   int port=feed.start(0);
   String query=OfficialOtaFeed.PATHS[0]+"?packageType=firmware&packageName=strix+OS&glassesType=S3&deviceType=android&versionCode=01.00.05.0012&glassesOsVersion=01.00.05.0012";
   ok(data(feed.reply(request(port,query)))==null);feed.arm(zip);
   Map<String,Object> meta=BoundedJson.map(data(feed.reply(request(port,query))));
   ok("0100050012".equals(meta.get("versionName")));
   ok(((Number)meta.get("versionCode")).intValue()==100050012);
   ok(((Number)meta.get("apkSize")).intValue()==profile.zipSize);
   ok(OtaPackage.hash("MD5",zip).equals(meta.get("apkMd5")));
   String path=new URI((String)meta.get("downloadUrl")).getRawPath();
   ok(Arrays.equals(zip,feed.reply(request(port,path)).body));
   ok(data(feed.reply(request(port,query.replace("packageType=firmware","packageType=app"))))==null);
   byte[] changed=zip.clone();changed[400]^=1;
   try{feed.arm(changed);throw new AssertionError("corrupt candidate accepted");}catch(java.io.IOException expected){checks++;}
   feed.disarm();ok(data(feed.reply(request(port,query)))==null);ok(feed.reply(request(port,path)).code==404);
   feed.arm(zip);clock.set(901000);ok(data(feed.reply(request(port,query)))==null);
  }
  for(int type=1;type<=255;type++)ok(OtaFrame.readOnly(OtaFrame.encode(type,null,null))==(type==1||type==2||type==11));
  System.out.println("TMG3 feed metadata/download/isolation: "+checks+" checks passed; no device I/O");
 }
}
