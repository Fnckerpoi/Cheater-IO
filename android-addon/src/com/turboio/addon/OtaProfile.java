package com.turboio.addon;

/** Immutable identities. Legacy static package APIs always select TAP1. */
final class OtaProfile {
 static final OtaProfile TAP1=new OtaProfile("TAP1-TEST-01",OtaPackage.VERSION,OtaPackage.NAME,OtaPackage.ZIP_SIZE,OtaPackage.AP_SIZE,OtaPackage.ZIP_SHA,null);
 static final OtaProfile TMIG1=new OtaProfile("TMIG1","Strix OS 1.0.5.5","TMIG1 · Strix OS 1.0.5.5",8824204,7373064,"f1d3616cfde5d128a9b60baab420559e38d19300838b0b87ceb1010f2ac5c940",new String[]{
  "OtaFileInfo.json 2700 ff1fa879bfb219b719cecb1cd56a788fb6ae7693d7f154cc0681e0a7b4f75c9d",
  "bt_controller.bin 625364 0060cc360b04577e8f5ce8f5d54fabde6103a6fef5a1a052260097920165a0c6",
  "cb_fw_venus.bin 114592 8799f12071bdac082218e3601c24ab0989275fc2f7db74e37b8314a225c2cf7d",
  "fac_test_img.bin 1821552 18eb0901c3e0daeae48dd5f015252b6931ea7368f0160c2c985c4e74296d17eb",
  "images.bin 383431 219ba2f7935850a059ac7e6a1ffa181056ce70891501db28238940dfbdc44cc5",
  "lotties.bin 651481 ad66d83a3394034f825a12769aad292fc03e59fa865fe06bc43536b918a0b32b",
  "nuttx_ap.bin 7373064 30522ac7d27a6750274fc2441cf7dd8f3d0ba70d43671e5595b411daa3b135f8",
  "nuttx_apc1.bin 1423532 40ea15260a56b6c642120b3234a871662d0e74709deab14cda64be0949dacb40",
  "nuttx_audio.bin 1461920 549a72c032595e3829da7d63b7e1fd13cceda1a55dfefa7a7cad02db5a9b42ab",
  "nuttx_bth.bin 1189216 d48cb821f30045ea681cae0bfdab766c6d02be6f7b2bd5a75e1c41dc127d1a30",
  "ota_installer_progress.bin 5616 6fd65d296a667e1a7d94ec129adfd26458a4008ef403b331f855192c36eb3e3f",
  "pil_algo_up_demo_nand.dll 504388 e266726fe055ac6ec6f785a9440e8da7421402f2b4d3d7889039ad9b94e119c2",
  "pil_algo_vad_demo.dll 433900 a3253e86dcdcb92a4d0d32fb4a0800da8761138c021a93d1c1dbca51268b66a2",
  "pil_algo_wakeup_dll_nand.dll 287560 d1594cb8658b228e52c2606d02f1eab696d35a11ed42ddcb725e76a745f5550f",
  "rives.bin 689431 836bc019f7152890b94cafafe2edaa024f317526318349f7833319e41158b104",
  "smf.json 23510 2713dc3a302a65c101fa981500cd8b217766c526b6045bf02db5e9e6763d076d"});
 final String token,version,name,sha,directory;final int zipSize,apSize;private final String[] pins;
 private OtaProfile(String token,String version,String name,int zipSize,int apSize,String sha,String[] pins){this.token=token;this.version=version;this.name=name;this.zipSize=zipSize;this.apSize=apSize;this.sha=sha;this.pins=pins;this.directory="Strix_OS_"+version.substring(9);}
 boolean matchesJournal(String candidate,String token){return sha.equals(candidate)&&this.token.equals(token);}
 String[] pins(){return pins.clone();}
 boolean acceptsSource(String version){return this.version.equals(version)||(this==TMIG1&&OtaPackage.VERSION.equals(version));}
 boolean acceptsQuery(String version){return "01.00.04.0012".equals(version)||(this==TMIG1&&"01.00.05.0005".equals(version));}
}
