package com.turboio.addon;
import java.util.*;
public final class OfficialOtaVersionEvidenceTest {
 static void ok(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] a)throws Exception {
  try(OfficialOtaFeed f=new OfficialOtaFeed(()->0L,()->true,false,OtaProfile.TMG4_10611)){
   int port=f.start(0);String p=OfficialOtaFeed.PATHS[0];
   f.reply("GET "+p+"?packageType=firmware&packageName=strix+OS&versionCode=01.02.03&token=SECRET HTTP/1.1\r\nHost: 127.0.0.1:"+port+"\r\n\r\n");
   String s=f.status();ok(s.contains("01.02.03"));ok(!s.contains("SECRET"));
   f.reply("GET "+p+"?packageType=app&versionCode=OTHERSECRET HTTP/1.1\r\nHost: 127.0.0.1:"+port+"\r\n\r\n");ok(!f.status().contains("OTHERSECRET"));
  }
  Map<String,Object> j=new HashMap<>();j.put("OsVersion","1.0.6.11");j.put("token","SECRET");j.put("Result",true);j.put("OtaValidationState","idle");j.put("OtaValidationConfirmed",false);j.put("PackageTypes",Arrays.asList("files","SECRET"));
  String d=OfficialOtaFeed.readbackEvidence(j);ok(d.contains("1.0.6.11"));ok(d.contains("OtaValidationState=idle"));ok(d.contains("OtaValidationConfirmed=false"));ok(d.contains("supportsFiles=true"));ok(d.contains("Result=true"));ok(!d.contains("SECRET"));
  j.put("OsVersion",String.join("",Collections.nCopies(81,"x")));ok(!OfficialOtaFeed.readbackEvidence(j).contains("xxxxxxxx"));
  System.out.println("Official OTA version evidence passed");
 }
}
