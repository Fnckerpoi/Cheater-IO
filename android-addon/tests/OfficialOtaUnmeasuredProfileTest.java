package com.turboio.addon;
import java.io.*;
import java.nio.file.*;

public final class OfficialOtaUnmeasuredProfileTest {
 public static void main(String[] args)throws Exception{
  byte[] stock=Files.readAllBytes(Paths.get(args[0]));
  OtaPackage candidate=OtaPackage.verify(stock,OtaProfile.STOCK10611);
  OfficialOtaGate gate=new OfficialOtaGate(peer->true,OtaProfile.STOCK10611);gate.bind("glasses");
  try{gate.arm(candidate,"glasses",OtaProfile.STOCK10611.token,1000);throw new AssertionError("unmeasured profile armed");}
  catch(IllegalStateException expected){}
  try(OfficialOtaFeed feed=new OfficialOtaFeed(()->1000,()->true,true,OtaProfile.STOCK10611)){
   int port=feed.start(0);
   try{feed.arm(stock);throw new AssertionError("unmeasured profile advertised an open download window");}
   catch(IOException expected){}
   String query=OfficialOtaFeed.PATHS[1]+"?packageType=firmware&packageName=strix+OS&glassesType=S3&deviceType=android&versionCode=01.00.05.0005";
   OfficialOtaFeed.Reply reply=feed.reply("GET "+query+" HTTP/1.1\r\nHost: 127.0.0.1:"+port+"\r\n\r\n");
   if(reply.code!=200||BoundedJson.map(BoundedJson.read(reply.body)).get("data")!=null)throw new AssertionError("legacy update leaked into new baseline");
  }
  System.out.println("Unmeasured OTA profile stays closed without legacy metadata");
 }
}
