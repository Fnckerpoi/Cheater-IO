package com.turboio.addon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class Stock10611ProfileTest {
 public static void main(String[] args)throws Exception{
  byte[] zip=Files.readAllBytes(Paths.get(args[0]));
  OtaPackage firmware=OtaPackage.verify(zip,OtaProfile.STOCK10611);
  if(firmware.profile!=OtaProfile.STOCK10611)throw new AssertionError("profile identity");
  if(BoundedJson.list(firmware.manifest()).size()!=16)throw new AssertionError("manifest count");
  if(firmware.size("nuttx_ap.bin")!=7967244||firmware.size("nuttx_ota.bin")!=536384||firmware.size("amap_rtos_sdk")!=775548)throw new AssertionError("payload sizes");
  if(OtaProfile.STOCK10611.acceptsSource("Strix OS 1.0.6.11")||OtaProfile.STOCK10611.acceptsQuery("01.00.04.0012"))throw new AssertionError("stock profile admitted to transfer");
  try{OtaPackage.verify(zip,OtaProfile.TMIG1);throw new AssertionError("old profile accepted new package");}catch(IOException expected){}
  if(!OtaProfile.TMIG1.version.equals("Strix OS 1.0.5.5"))throw new AssertionError("TMIG1 changed");
  if(args.length>1){
   byte[] candidate=Files.readAllBytes(Paths.get(args[1]));
   OtaPackage firmwareCandidate=OtaPackage.verify(candidate,OtaProfile.TMG3_10611);
   if(firmwareCandidate.profile!=OtaProfile.TMG3_10611||firmwareCandidate.size("nuttx_ap.bin")!=8170888)throw new AssertionError("candidate profile identity");
   if(OtaProfile.TMG3_10611.acceptsSource("Strix OS 1.0.6.11")||OtaProfile.TMG3_10611.acceptsQuery("01.00.04.0012"))throw new AssertionError("candidate admitted to transfer");
   try{OtaPackage.verify(candidate,OtaProfile.STOCK10611);throw new AssertionError("stock profile accepted candidate");}catch(IOException expected){}
  }
  System.out.println("1.0.6.11 stock archive verified; TMIG1 identity preserved");
 }
}
