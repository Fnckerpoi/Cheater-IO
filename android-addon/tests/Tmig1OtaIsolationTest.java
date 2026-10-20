package com.turboio.addon;
import java.nio.file.*;import java.io.*;
public final class Tmig1OtaIsolationTest {
 interface Work{void run()throws Exception;}
 static int checks;static void rejects(Work w)throws Exception{try{w.run();throw new AssertionError("cross-profile accepted");}catch(IOException|IllegalArgumentException|IllegalStateException expected){checks++;}}
 public static void main(String[] args)throws Exception{
  byte[] tmig=Files.readAllBytes(Paths.get(args[0])),tap=Files.readAllBytes(Paths.get(args[1])),stock=Files.readAllBytes(Paths.get(args[2]));
  OtaPackage newer=OtaPackage.verify(tmig,OtaProfile.TMIG1),older=OtaPackage.verify(tap);
  rejects(()->OtaPackage.verify(tmig));rejects(()->OtaPackage.verify(tap,OtaProfile.TMIG1));rejects(()->OtaPackage.verify(stock,OtaProfile.TMIG1));
  rejects(()->new OtaSession(newer));new OtaSession(older);checks++;
  OfficialOtaGate legacy=new OfficialOtaGate(p->true);legacy.bind("peer");rejects(()->legacy.arm(newer,"peer","TAP1-TEST-01",1000));
  OfficialOtaGate modern=new OfficialOtaGate(p->true,OtaProfile.TMIG1);modern.bind("peer");rejects(()->modern.arm(older,"peer","TMIG1",1000));rejects(()->modern.arm(newer,"peer","TAP1-TEST-01",1000));
  String[] pins=OtaProfile.TMIG1.pins();pins[0]="corrupted";if(OtaProfile.TMIG1.pins()[0].equals("corrupted"))throw new AssertionError("mutable pins");checks++;
  if(!OtaProfile.TMIG1.directory.equals("Strix_OS_1.0.5.5"))throw new AssertionError("directory");checks++;
  if(OtaProfile.TMIG1.matchesJournal(OtaPackage.ZIP_SHA,"TAP1-TEST-01")||OtaProfile.TMIG1.matchesJournal(OtaProfile.TMIG1.sha,"")||OtaProfile.TMIG1.matchesJournal("bad","TMIG1")||!OtaProfile.TMIG1.matchesJournal(OtaProfile.TMIG1.sha,"TMIG1"))throw new AssertionError("journal identity");checks++;
  System.out.println("TMIG1 isolation: "+checks+" checks passed");
 }
}
