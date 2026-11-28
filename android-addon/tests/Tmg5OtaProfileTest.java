package com.turboio.addon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/** The new widgets_v2 iteration keeps the TMG4 identities immutable. */
public final class Tmg5OtaProfileTest {
 public static void main(String[] args)throws Exception{
  OtaProfile profile=OtaProfile.TMG5_10611;
  if(!"TMG5-10611".equals(profile.token)||!"Strix OS 1.0.6.11".equals(profile.version))throw new AssertionError("TMG5 product identity");
  if(profile==OtaProfile.TMG4_3_10611||profile.sha.equals(OtaProfile.TMG4_3_10611.sha)||profile.token.equals(OtaProfile.TMG4_3_10611.token))throw new AssertionError("TMG4.3 identity relabeled");
  if(OtaProfile.fromJournal(OtaProfile.TMG4_3_10611.sha,OtaProfile.TMG4_3_10611.token)!=OtaProfile.TMG4_3_10611||OtaProfile.fromJournal(profile.sha,profile.token)!=profile)throw new AssertionError("journal identity");
  if(!OtaProfile.TMG4_3_10611.directory.equals(profile.directory))throw new AssertionError("source preparation layout changed unexpectedly");
  byte[] archive=Files.readAllBytes(Paths.get(args[0]));
  OtaPackage verified=OtaPackage.verify(archive,profile);
  if(verified.profile!=profile||verified.size("nuttx_ap.bin")!=profile.apSize||profile.pins().length!=17)throw new AssertionError("TMG5 artifact identity");
  try{OtaPackage.verify(archive,OtaProfile.TMG4_3_10611);throw new AssertionError("TMG4.3 profile accepted TMG5 artifact");}catch(IOException expected){}
  System.out.println("TMG5 artifact identity and TMG4 history isolation passed");
 }
}
