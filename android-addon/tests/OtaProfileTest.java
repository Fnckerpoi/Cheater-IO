package com.turboio.addon;

public final class OtaProfileTest {
 static int checks;
 static void ok(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
 public static void main(String[] args)throws Exception{
  ok(OtaProfile.TAP1.token.equals("TAP1-TEST-01"));
  ok(OtaProfile.TAP1.version.equals("Strix OS 1.0.4.12"));
  ok(OtaProfile.TAP1.name.equals("TAP1-TEST-01 · 菜单显示TEST"));
  ok(OtaProfile.TAP1.zipSize==9320672&&OtaProfile.TAP1.apSize==9590856);
  ok(OtaProfile.TAP1.sha.equals("5ef21d8c18501d03fad6edee4fe039649cb567352e30dbd234081ecf97140afa"));
  ok(OtaProfile.TAP1.directory.equals("Strix_OS_1.0.4.12")&&OtaProfile.TAP1.pins()==null);
  ok(OtaProfile.TMIG1.token.equals("TMIG1"));
  ok(OtaProfile.TMIG1.version.equals("Strix OS 1.0.5.5"));
  ok(OtaProfile.TMIG1.name.equals("TMIG1 · Strix OS 1.0.5.5"));
  ok(OtaProfile.TMIG1.zipSize==8824204&&OtaProfile.TMIG1.apSize==7373064);
  ok(OtaProfile.TMIG1.sha.equals("f1d3616cfde5d128a9b60baab420559e38d19300838b0b87ceb1010f2ac5c940"));
  ok(OtaProfile.TMIG1.directory.equals("Strix_OS_1.0.5.5"));

  OtaProfile p=OtaProfile.TMG2;
  ok(p.token.equals("TMG2-CANDIDATE-NOT-APPROVED"));
  ok(p.version.equals("TMG2-CANDIDATE-NOT-APPROVED"));
  ok(p.name.equals("StrixOS-1.0.5.5-TurboIO-TMG2-CANDIDATE-NOT-APPROVED.zip"));
  ok(p.zipSize==8842896&&p.apSize==7408584);
  ok(p.sha.equals("6ae710233feec65e23816f4c89e75584de5f1abdde1f0aa8d74c4a3535ca8473"));
  ok(p.directory.equals("Strix_OS_1.0.5.5"));
  String[] pins=p.pins();ok(pins!=null&&pins.length==16);
  String[] original=p.pins();String first=original[0];pins[0]="modified";ok(p.pins()[0].equals(first));
  original[1]="modified";ok(p.pins()[1].equals("bt_controller.bin 625364 0060cc360b04577e8f5ce8f5d54fabde6103a6fef5a1a052260097920165a0c6"));
  ok(OtaProfile.TAP1.acceptsQuery("01.00.04.0012"));
  ok(!OtaProfile.TAP1.acceptsQuery("01.00.05.0005"));
  ok(OtaProfile.TMIG1.acceptsQuery("01.00.04.0012"));
  ok(OtaProfile.TMIG1.acceptsQuery("01.00.05.0005"));
  ok(!p.acceptsQuery("01.00.04.0012"));
  ok(!p.acceptsQuery("01.00.05.0005"));
  ok(!p.acceptsQuery("99.99.99.9999"));
  java.util.Map<String,String> query=OfficialOtaFeed.query("/g/test?packageType=firmware&packageName=strix+OS&glassesType=S3&deviceType=android&versionCode=01.00.04.0012");
  ok(OfficialOtaFeed.firmware(query,OtaProfile.TAP1));
  ok(OfficialOtaFeed.firmware(query,OtaProfile.TMIG1));
  ok(!OfficialOtaFeed.firmware(query,p));
  query.put("versionCode","01.00.05.0005");
  ok(!OfficialOtaFeed.firmware(query,OtaProfile.TAP1));
  ok(OfficialOtaFeed.firmware(query,OtaProfile.TMIG1));
  ok(!OfficialOtaFeed.firmware(query,p));
  query.put("deviceType","iOS");
  ok(!OfficialOtaFeed.firmware(query,OtaProfile.TMIG1));
  ok(OtaProfile.TMIG1.acceptsSource(OtaPackage.VERSION));
  ok(OtaProfile.TMIG1.acceptsSource(OtaProfile.TMIG1.version));
  ok(!p.acceptsSource(OtaPackage.VERSION));
  ok(!p.acceptsSource(OtaProfile.TMIG1.version));
  ok(p.matchesJournal(p.sha,p.token));
  ok(!p.matchesJournal(OtaProfile.TAP1.sha,OtaProfile.TAP1.token));
  ok(!p.matchesJournal(OtaProfile.TMIG1.sha,OtaProfile.TMIG1.token));
  ok(!p.matchesJournal(p.sha,OtaProfile.TAP1.token));
  ok(!p.matchesJournal(p.sha,OtaProfile.TMIG1.token));
  ok(!OtaProfile.TAP1.matchesJournal(p.sha,p.token));
  ok(!OtaProfile.TMIG1.matchesJournal(p.sha,p.token));
  OtaProfile p3=OtaProfile.TMG3;
  ok(p3.token.equals("TMG3-CANDIDATE-NOT-APPROVED"));
  ok(p3.version.equals("Strix OS 1.0.5.12"));
  ok(p3.name.equals("StrixOS-1.0.5.12-TurboIO-TMG3-CANDIDATE.zip"));
  ok(p3.zipSize==8869710&&p3.apSize==7515400);
  ok(p3.sha.equals("2e5eff12002191523c35d708f83dbb42422d724e5abd390f74a9416c3788cb93"));
  ok(p3.directory.equals("Strix_OS_1.0.5.12"));
  String[] pins3=p3.pins();ok(pins3!=null&&pins3.length==16);
  String first3=pins3[0];pins3[0]="modified";ok(p3.pins()[0].equals(first3));
  ok(p3.acceptsQuery("01.00.04.0012"));
  ok(!p3.acceptsQuery("01.00.05.0005"));
  java.util.Map<String,String> q3=OfficialOtaFeed.query("/g/test?packageType=firmware&packageName=strix+OS&glassesType=S3&deviceType=android&versionCode=01.00.04.0012");
  ok(OfficialOtaFeed.firmware(q3,p3));
  ok(p3.matchesJournal(p3.sha,p3.token));
  ok(!p3.matchesJournal(p.sha,p.token));
  java.util.Map<String,String> actual=OfficialOtaFeed.query("/g/xxxxxxxA78p2M?packageType=firmware&packageName=strix+OS&glassesType=S3&deviceType=android&versionCode=01.00.05.0012&glassesOsVersion=01.00.05.0012");
  ok(OfficialOtaFeed.firmware(actual,p3));
  for(OtaProfile other:new OtaProfile[]{OtaProfile.TAP1,OtaProfile.TMIG1,OtaProfile.TMG2})ok(!OfficialOtaFeed.firmware(actual,other));
  for(String key:new String[]{"packageType","packageName","glassesType","deviceType","versionCode"}){java.util.Map<String,String> invalid=new java.util.HashMap<>(actual);invalid.put(key,"unexpected");ok(!OfficialOtaFeed.firmware(invalid,p3));}
  System.out.println("OTA profile identities: "+checks+" checks passed");
 }
}
