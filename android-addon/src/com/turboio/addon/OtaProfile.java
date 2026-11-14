package com.turboio.addon;

/** Immutable identities. Legacy static package APIs always select TAP1. */
final class OtaProfile {
    static final OtaProfile TAP1 = new OtaProfile("TAP1-TEST-01", OtaPackage.VERSION, OtaPackage.NAME,
            OtaPackage.ZIP_SIZE, OtaPackage.AP_SIZE, OtaPackage.ZIP_SHA, null);
    static final OtaProfile TMIG1 = new OtaProfile("TMIG1", "Strix OS 1.0.5.5", "TMIG1 · Strix OS 1.0.5.5", 8824204,
            7373064, "f1d3616cfde5d128a9b60baab420559e38d19300838b0b87ceb1010f2ac5c940", new String[] {
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
                    "smf.json 23510 2713dc3a302a65c101fa981500cd8b217766c526b6045bf02db5e9e6763d076d" });
    static final OtaProfile STOCK10611 = new OtaProfile("STOCK-1.0.6.11", "Strix OS 1.0.6.11",
            "Strix OS 1.0.6.11 · 原厂对照", 9472935, 7967244,
            "e403d73b84c20277f183788d778beb7a0ad8336af27efb112c66b7169fcaa320", new String[] {
                    "OtaFileInfo.json 2901 c5c09444e28bde6d13c9916aca17dd78b19e676854be4991b260e134d55f91a9",
                    "amap_rtos_sdk 775548 a14c47776864b5786bf61e4c37e159259c8d7c9b94ad6664e4526eadc445574a",
                    "bt_controller.bin 632384 dd935af210755d94c43995c62d9ec03e469157cd14965b1642c83d2f1daa440b",
                    "cb_fw_venus.bin 114592 8799f12071bdac082218e3601c24ab0989275fc2f7db74e37b8314a225c2cf7d",
                    "fac_test_img.bin 1821552 18eb0901c3e0daeae48dd5f015252b6931ea7368f0160c2c985c4e74296d17eb",
                    "images.bin 390937 404ae1d33592db7592467b24bc29c1c783260fb7cf53f4be22838097f38fa501",
                    "lotties.bin 661305 a8b5fa78e57775a025d90aa94d41245495a555c7ca4399b226b515bc03997162",
                    "nuttx_ap.bin 7967244 9bbf7be31ebb7f8600e6ea4bf51831a151e2af6d4eb8e6ccfcf55d67f2e71260",
                    "nuttx_apc1.bin 1442112 c24794d051df5803152fac770051374aff4bd34f6d41ef8ab39853e7b4d05ba9",
                    "nuttx_audio.bin 1461920 549a72c032595e3829da7d63b7e1fd13cceda1a55dfefa7a7cad02db5a9b42ab",
                    "nuttx_bth.bin 1250856 1fd910ea03124cd26d010c3ea3ef6a1b68dc61bd6283dcd07d2ad744d28759ec",
                    "nuttx_ota.bin 536384 016cdd0ba9424512c9669d229b7c81d94eebdb24e15ba3c1c7b461ca32f2bc1a",
                    "ota_installer_progress.bin 5616 6fd65d296a667e1a7d94ec129adfd26458a4008ef403b331f855192c36eb3e3f",
                    "pil_algo_up_demo_nand.dll 504388 e266726fe055ac6ec6f785a9440e8da7421402f2b4d3d7889039ad9b94e119c2",
                    "pil_algo_vad_demo.dll 433900 0153a5a8e6b69ca0b9dd4cc742d45a758351d8209e604f38dbfe046c31f29f01",
                    "pil_algo_wakeup_dll_nand.dll 287560 d1594cb8658b228e52c2606d02f1eab696d35a11ed42ddcb725e76a745f5550f",
                    "smf.json 23556 43998e7acf06c8f9bc39635655d79120b7061a46f7b23f791f8699536f81836d" });
    static final OtaProfile TMG3_10611 = new OtaProfile("TMG3-1.0.6.11", "Strix OS 1.0.6.11",
            "TMG3 · Strix OS 1.0.6.11 · 离线候选", 9570156, 8170888,
            "c451dce891907de6efbde767f0ab351fd7703f3dff35e76bf0d049d78a643732", new String[] {
                    "OtaFileInfo.json 2886 c23539dc084ceddb3688b5b463e945de16f1ee85452b94002893ad019ef461ae",
                    "amap_rtos_sdk 775548 a14c47776864b5786bf61e4c37e159259c8d7c9b94ad6664e4526eadc445574a",
                    "bt_controller.bin 632384 dd935af210755d94c43995c62d9ec03e469157cd14965b1642c83d2f1daa440b",
                    "cb_fw_venus.bin 114592 8799f12071bdac082218e3601c24ab0989275fc2f7db74e37b8314a225c2cf7d",
                    "fac_test_img.bin 1821552 18eb0901c3e0daeae48dd5f015252b6931ea7368f0160c2c985c4e74296d17eb",
                    "images.bin 390937 404ae1d33592db7592467b24bc29c1c783260fb7cf53f4be22838097f38fa501",
                    "lotties.bin 661305 a8b5fa78e57775a025d90aa94d41245495a555c7ca4399b226b515bc03997162",
                    "nuttx_ap.bin 8170888 f2099b09845ad7e9833a1b27524f844c1e49eeb1868fd09d5072de3247fac0f7",
                    "nuttx_apc1.bin 1442112 c24794d051df5803152fac770051374aff4bd34f6d41ef8ab39853e7b4d05ba9",
                    "nuttx_audio.bin 1461920 549a72c032595e3829da7d63b7e1fd13cceda1a55dfefa7a7cad02db5a9b42ab",
                    "nuttx_bth.bin 1250856 1fd910ea03124cd26d010c3ea3ef6a1b68dc61bd6283dcd07d2ad744d28759ec",
                    "nuttx_ota.bin 536384 016cdd0ba9424512c9669d229b7c81d94eebdb24e15ba3c1c7b461ca32f2bc1a",
                    "ota_installer_progress.bin 5616 6fd65d296a667e1a7d94ec129adfd26458a4008ef403b331f855192c36eb3e3f",
                    "pil_algo_up_demo_nand.dll 504388 e266726fe055ac6ec6f785a9440e8da7421402f2b4d3d7889039ad9b94e119c2",
                    "pil_algo_vad_demo.dll 433900 0153a5a8e6b69ca0b9dd4cc742d45a758351d8209e604f38dbfe046c31f29f01",
                    "pil_algo_wakeup_dll_nand.dll 287560 d1594cb8658b228e52c2606d02f1eab696d35a11ed42ddcb725e76a745f5550f",
                    "smf.json 23556 43998e7acf06c8f9bc39635655d79120b7061a46f7b23f791f8699536f81836d" });
    static final OtaProfile TMG4_10611 = new OtaProfile("TMG4-10611", "Strix OS 1.0.6.11",
            "TMG4-10611 · Strix OS 1.0.6.11 · 离线候选", 9538901, 8125128,
            "554c897a995ed4dafd9d2d8ae11317398de8c927f618897fa1aa9a1b8a47f26e", new String[] {
                    "OtaFileInfo.json 2886 6adf9203ea2d56ef9f0acf7bae09d3cce20ae05e9511641020019f33b8f5ccb0",
                    "amap_rtos_sdk 775548 a14c47776864b5786bf61e4c37e159259c8d7c9b94ad6664e4526eadc445574a",
                    "bt_controller.bin 632384 dd935af210755d94c43995c62d9ec03e469157cd14965b1642c83d2f1daa440b",
                    "cb_fw_venus.bin 114592 8799f12071bdac082218e3601c24ab0989275fc2f7db74e37b8314a225c2cf7d",
                    "fac_test_img.bin 1821552 18eb0901c3e0daeae48dd5f015252b6931ea7368f0160c2c985c4e74296d17eb",
                    "images.bin 390937 404ae1d33592db7592467b24bc29c1c783260fb7cf53f4be22838097f38fa501",
                    "lotties.bin 661305 a8b5fa78e57775a025d90aa94d41245495a555c7ca4399b226b515bc03997162",
                    "nuttx_ap.bin 8125128 53d98bf5eeb0944da6e0f6fe85bb66cfb149fb362b8ba1593101950136380b05",
                    "nuttx_apc1.bin 1442112 c24794d051df5803152fac770051374aff4bd34f6d41ef8ab39853e7b4d05ba9",
                    "nuttx_audio.bin 1461920 549a72c032595e3829da7d63b7e1fd13cceda1a55dfefa7a7cad02db5a9b42ab",
                    "nuttx_bth.bin 1250856 1fd910ea03124cd26d010c3ea3ef6a1b68dc61bd6283dcd07d2ad744d28759ec",
                    "nuttx_ota.bin 536384 016cdd0ba9424512c9669d229b7c81d94eebdb24e15ba3c1c7b461ca32f2bc1a",
                    "ota_installer_progress.bin 5616 6fd65d296a667e1a7d94ec129adfd26458a4008ef403b331f855192c36eb3e3f",
                    "pil_algo_up_demo_nand.dll 504388 e266726fe055ac6ec6f785a9440e8da7421402f2b4d3d7889039ad9b94e119c2",
                    "pil_algo_vad_demo.dll 433900 0153a5a8e6b69ca0b9dd4cc742d45a758351d8209e604f38dbfe046c31f29f01",
                    "pil_algo_wakeup_dll_nand.dll 287560 d1594cb8658b228e52c2606d02f1eab696d35a11ed42ddcb725e76a745f5550f",
                    "smf.json 23556 43998e7acf06c8f9bc39635655d79120b7061a46f7b23f791f8699536f81836d" });
    final String token, version, name, sha, directory;
    final int zipSize, apSize;
    private final String[] pins;

    private OtaProfile(String token, String version, String name, int zipSize, int apSize, String sha, String[] pins) {
        this.token = token;
        this.version = version;
        this.name = name;
        this.zipSize = zipSize;
        this.apSize = apSize;
        this.sha = sha;
        this.pins = pins;
        this.directory = "Strix_OS_" + version.substring(9);
    }

    static OtaProfile fromJournal(String candidate, String token) {
        for (OtaProfile p : new OtaProfile[] { TAP1, TMIG1, TMG3_10611, TMG4_10611 }) {
            if (p.matchesJournal(candidate, token)) return p;
        }
        return null;
    }

    boolean matchesJournal(String candidate, String token) {
        return sha.equals(candidate) && this.token.equals(token);
    }

    String[] pins() {
        return pins.clone();
    }

    boolean hasOfficialVersions() { return this == TAP1 || this == TMIG1 || this == TMG4_10611; }

    // TMG4 response values are experiment-selected, not vendor response evidence.
    String queryVersionName() { return this == TMG4_10611 ? "0100060011" : this == TMIG1 ? "0100050005" : this == TAP1 ? "0100040012" : null; }
    int queryVersionCode() { return this == TMG4_10611 ? 100060011 : this == TMIG1 ? 100050005 : this == TAP1 ? 100040012 : 0; }

    boolean acceptsSource(String version) {
        return hasOfficialVersions()
                && (this.version.equals(version) || (this == TMIG1 && OtaPackage.VERSION.equals(version)));
    }

    boolean acceptsQuery(String version) {
        return hasOfficialVersions()
                && (this == TMG4_10611 ? "01.00.06.0011".equals(version) : "01.00.04.0012".equals(version) || (this == TMIG1 && "01.00.05.0005".equals(version)));
    }
}
