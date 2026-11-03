package com.turboio.addon;

/** Immutable identities. Legacy static package APIs always select TAP1. */
final class OtaProfile {
 static final OtaProfile TAP1=new OtaProfile("TAP1-TEST-01",OtaPackage.VERSION,OtaPackage.NAME,OtaPackage.ZIP_SIZE,OtaPackage.AP_SIZE,OtaPackage.ZIP_SHA,null);
 static final OtaProfile TMIG1=new OtaProfile("TMIG1","Strix OS 1.0.5.5","TMIG1 · Strix OS 1.0.5.5",8824204,7373064,"f1d3616cfde5d128a9b60baab420559e38d19300838b0b87ceb1010f2ac5c940","Strix_OS_1.0.5.5",new String[]{
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
 static final OtaProfile TMG2=new OtaProfile("TMG2-CANDIDATE-NOT-APPROVED","TMG2-CANDIDATE-NOT-APPROVED","StrixOS-1.0.5.5-TurboIO-TMG2-CANDIDATE-NOT-APPROVED.zip",8842896,7408584,"6ae710233feec65e23816f4c89e75584de5f1abdde1f0aa8d74c4a3535ca8473","Strix_OS_1.0.5.5",new String[]{
  "OtaFileInfo.json 2700 2438f3ad4f7966ff5d1a4b489b8fb931f6410933063b43a8dc092cd4dc5acdae",
  "bt_controller.bin 625364 0060cc360b04577e8f5ce8f5d54fabde6103a6fef5a1a052260097920165a0c6",
  "cb_fw_venus.bin 114592 8799f12071bdac082218e3601c24ab0989275fc2f7db74e37b8314a225c2cf7d",
  "fac_test_img.bin 1821552 18eb0901c3e0daeae48dd5f015252b6931ea7368f0160c2c985c4e74296d17eb",
  "images.bin 383431 219ba2f7935850a059ac7e6a1ffa181056ce70891501db28238940dfbdc44cc5",
  "lotties.bin 651481 ad66d83a3394034f825a12769aad292fc03e59fa865fe06bc43536b918a0b32b",
  "nuttx_ap.bin 7408584 13c1b953ac50b91c87ebbf039b3795eb82e6c47912e7e7d2bfd911f989c14261",
  "nuttx_apc1.bin 1423532 40ea15260a56b6c642120b3234a871662d0e74709deab14cda64be0949dacb40",
  "nuttx_audio.bin 1461920 549a72c032595e3829da7d63b7e1fd13cceda1a55dfefa7a7cad02db5a9b42ab",
  "nuttx_bth.bin 1189216 d48cb821f30045ea681cae0bfdab766c6d02be6f7b2bd5a75e1c41dc127d1a30",
  "ota_installer_progress.bin 5616 6fd65d296a667e1a7d94ec129adfd26458a4008ef403b331f855192c36eb3e3f",
  "pil_algo_up_demo_nand.dll 504388 e266726fe055ac6ec6f785a9440e8da7421402f2b4d3d7889039ad9b94e119c2",
  "pil_algo_vad_demo.dll 433900 a3253e86dcdcb92a4d0d32fb4a0800da8761138c021a93d1c1dbca51268b66a2",
  "pil_algo_wakeup_dll_nand.dll 287560 d1594cb8658b228e52c2606d02f1eab696d35a11ed42ddcb725e76a745f5550f",
  "rives.bin 689431 836bc019f7152890b94cafafe2edaa024f317526318349f7833319e41158b104",
  "smf.json 23510 2713dc3a302a65c101fa981500cd8b217766c526b6045bf02db5e9e6763d076d"});
 static final OtaProfile TMG3=new OtaProfile("TMG3-CANDIDATE-NOT-APPROVED","Strix OS 1.0.5.12","StrixOS-1.0.5.12-TurboIO-TMG3-CANDIDATE.zip",8869710,7515400,"2e5eff12002191523c35d708f83dbb42422d724e5abd390f74a9416c3788cb93","Strix_OS_1.0.5.12",new String[]{
  "OtaFileInfo.json 2700 19a9f36700350367b76ecaf2c780957404d3f33d0f43bb3e8393b8964f2093f6",
  "bt_controller.bin 625364 0060cc360b04577e8f5ce8f5d54fabde6103a6fef5a1a052260097920165a0c6",
  "cb_fw_venus.bin 114592 8799f12071bdac082218e3601c24ab0989275fc2f7db74e37b8314a225c2cf7d",
  "fac_test_img.bin 1821552 18eb0901c3e0daeae48dd5f015252b6931ea7368f0160c2c985c4e74296d17eb",
  "images.bin 383431 219ba2f7935850a059ac7e6a1ffa181056ce70891501db28238940dfbdc44cc5",
  "lotties.bin 651481 ad66d83a3394034f825a12769aad292fc03e59fa865fe06bc43536b918a0b32b",
  "nuttx_ap.bin 7515400 e05134fe3bfcb961c98db775a4463af09aec95a0dadece256c2441ff7cc60f09",
  "nuttx_apc1.bin 1435740 abee438702d29936a01e60b863f3f3ecdea73097d60c09bcded1a61606098e08",
  "nuttx_audio.bin 1461920 549a72c032595e3829da7d63b7e1fd13cceda1a55dfefa7a7cad02db5a9b42ab",
  "nuttx_bth.bin 1194132 a7c4618ac992170d71fe0951ef6febfa46f13409c0bfc73c06ba74b0a00033f0",
  "ota_installer_progress.bin 5616 6fd65d296a667e1a7d94ec129adfd26458a4008ef403b331f855192c36eb3e3f",
  "pil_algo_up_demo_nand.dll 504388 e266726fe055ac6ec6f785a9440e8da7421402f2b4d3d7889039ad9b94e119c2",
  "pil_algo_vad_demo.dll 433900 5391fd024f1dfd38d031aeff5b1f4e91b13ca6d51f30e67763cd9fdf9db521a8",
  "pil_algo_wakeup_dll_nand.dll 287560 d1594cb8658b228e52c2606d02f1eab696d35a11ed42ddcb725e76a745f5550f",
  "rives.bin 689431 836bc019f7152890b94cafafe2edaa024f317526318349f7833319e41158b104",
  "smf.json 23510 2713dc3a302a65c101fa981500cd8b217766c526b6045bf02db5e9e6763d076d"});
 static final OtaProfile STOCK127=new OtaProfile("STOCK-127-CONTROL","Strix OS 1.0.5.12","Strix OS 1.0.5.12 · 原厂未修改对照",8769953,7313036,"b7bfd3cc73528e3e9995ad227dc877c8334d43dc8051436e2d2ccff2ca0b4133","Strix_OS_1.0.5.12",new String[]{
  "OtaFileInfo.json 2714 8fbe3663f0698c6e3c77422d1abd846afe88401118aa8770d4d84efa628f7408",
  "bt_controller.bin 625364 0060cc360b04577e8f5ce8f5d54fabde6103a6fef5a1a052260097920165a0c6",
  "cb_fw_venus.bin 114592 8799f12071bdac082218e3601c24ab0989275fc2f7db74e37b8314a225c2cf7d",
  "fac_test_img.bin 1821552 18eb0901c3e0daeae48dd5f015252b6931ea7368f0160c2c985c4e74296d17eb",
  "images.bin 383431 219ba2f7935850a059ac7e6a1ffa181056ce70891501db28238940dfbdc44cc5",
  "lotties.bin 651481 ad66d83a3394034f825a12769aad292fc03e59fa865fe06bc43536b918a0b32b",
  "nuttx_ap.bin 7313036 af701a6525fbf703854b3d63e757734546c20bdb5d50e246867dc6a0565713ae",
  "nuttx_apc1.bin 1435740 abee438702d29936a01e60b863f3f3ecdea73097d60c09bcded1a61606098e08",
  "nuttx_audio.bin 1461920 549a72c032595e3829da7d63b7e1fd13cceda1a55dfefa7a7cad02db5a9b42ab",
  "nuttx_bth.bin 1194132 a7c4618ac992170d71fe0951ef6febfa46f13409c0bfc73c06ba74b0a00033f0",
  "ota_installer_progress.bin 5616 6fd65d296a667e1a7d94ec129adfd26458a4008ef403b331f855192c36eb3e3f",
  "pil_algo_up_demo_nand.dll 504388 e266726fe055ac6ec6f785a9440e8da7421402f2b4d3d7889039ad9b94e119c2",
  "pil_algo_vad_demo.dll 433900 5391fd024f1dfd38d031aeff5b1f4e91b13ca6d51f30e67763cd9fdf9db521a8",
  "pil_algo_wakeup_dll_nand.dll 287560 d1594cb8658b228e52c2606d02f1eab696d35a11ed42ddcb725e76a745f5550f",
  "rives.bin 689431 836bc019f7152890b94cafafe2edaa024f317526318349f7833319e41158b104",
  "smf.json 23510 2713dc3a302a65c101fa981500cd8b217766c526b6045bf02db5e9e6763d076d"});
 static final OtaProfile TMG3_REPAIRED=new OtaProfile("TMG3-REPAIRED-127-01","Strix OS 1.0.5.12","StrixOS-1.0.5.12-TurboIO-TMG3-REPAIRED-OFFLINE-NOT-APPROVED.zip",8869710,7515400,"adf7a6fa6531c6246e0a067343310ac4c662ddd5b76fbe3aba457a8c4ac68c87","Strix_OS_1.0.5.12",new String[]{
  "OtaFileInfo.json 2700 96c3f7df58e114a71059f088a6b3cb014b501e65f4ca4b0debe35c1d225afe9a",
  "bt_controller.bin 625364 0060cc360b04577e8f5ce8f5d54fabde6103a6fef5a1a052260097920165a0c6",
  "cb_fw_venus.bin 114592 8799f12071bdac082218e3601c24ab0989275fc2f7db74e37b8314a225c2cf7d",
  "fac_test_img.bin 1821552 18eb0901c3e0daeae48dd5f015252b6931ea7368f0160c2c985c4e74296d17eb",
  "images.bin 383431 219ba2f7935850a059ac7e6a1ffa181056ce70891501db28238940dfbdc44cc5",
  "lotties.bin 651481 ad66d83a3394034f825a12769aad292fc03e59fa865fe06bc43536b918a0b32b",
  "nuttx_ap.bin 7515400 20094d589b2453ba97ac764da625e86dcc78b4f76cb00f2a5fd5cba486187f3f",
  "nuttx_apc1.bin 1435740 abee438702d29936a01e60b863f3f3ecdea73097d60c09bcded1a61606098e08",
  "nuttx_audio.bin 1461920 549a72c032595e3829da7d63b7e1fd13cceda1a55dfefa7a7cad02db5a9b42ab",
  "nuttx_bth.bin 1194132 a7c4618ac992170d71fe0951ef6febfa46f13409c0bfc73c06ba74b0a00033f0",
  "ota_installer_progress.bin 5616 6fd65d296a667e1a7d94ec129adfd26458a4008ef403b331f855192c36eb3e3f",
  "pil_algo_up_demo_nand.dll 504388 e266726fe055ac6ec6f785a9440e8da7421402f2b4d3d7889039ad9b94e119c2",
  "pil_algo_vad_demo.dll 433900 5391fd024f1dfd38d031aeff5b1f4e91b13ca6d51f30e67763cd9fdf9db521a8",
  "pil_algo_wakeup_dll_nand.dll 287560 d1594cb8658b228e52c2606d02f1eab696d35a11ed42ddcb725e76a745f5550f",
  "rives.bin 689431 836bc019f7152890b94cafafe2edaa024f317526318349f7833319e41158b104",
  "smf.json 23510 2713dc3a302a65c101fa981500cd8b217766c526b6045bf02db5e9e6763d076d"});
 final String token,version,name,sha,directory;final int zipSize,apSize;private final String[] pins;
 private OtaProfile(String token,String version,String name,int zipSize,int apSize,String sha,String[] pins){this(token,version,name,zipSize,apSize,sha,"Strix_OS_"+version.substring(9),pins);}
 private OtaProfile(String token,String version,String name,int zipSize,int apSize,String sha,String directory,String[] pins){this.token=token;this.version=version;this.name=name;this.zipSize=zipSize;this.apSize=apSize;this.sha=sha;this.pins=pins==null?null:pins.clone();this.directory=directory;}
 boolean matchesJournal(String candidate,String token){return sha.equals(candidate)&&this.token.equals(token);}
 String[] pins(){return pins==null?null:pins.clone();}
 boolean acceptsSource(String version){return this.version.equals(version)||(this==TMIG1&&OtaPackage.VERSION.equals(version));}
 boolean acceptsQuery(String version){return ((this==TAP1||this==TMIG1||this==TMG3)&&"01.00.04.0012".equals(version))||(this==TMIG1&&"01.00.05.0005".equals(version))||((this==TMG3||this==STOCK127||this==TMG3_REPAIRED)&&"01.00.05.0012".equals(version));}
}
