import copy
import subprocess
import plistlib
import shutil
from pathlib import Path
import tempfile
import unittest
import zipfile
from local_http_package import BUNDLE, HTTP_POLICY, AMAP_POLICY, FINE_GRAINED_ATS_KEYS, local_http_info, archive_payload, validate_http_addon, validate_amap_addon, navigation_info


class LocalHTTPPackageTests(unittest.TestCase):
    def test_archive_never_includes_appledouble_even_with_finder_metadata(self):
        with tempfile.TemporaryDirectory(prefix="tio-signing-regression-") as directory:
            root = Path(directory).resolve()
            self.assertTrue(root.name.startswith("tio-signing-regression-"))
            payload = root / "Payload"
            app = payload / "Runner.app"
            app.mkdir(parents=True)
            resource = app / "fixture.txt"
            resource.write_bytes(b"synthetic resource")
            metadata_value = (b"\x00" * 8 + b"\x01" + b"\x00" * 23).hex()
            subprocess.run(["/usr/bin/xattr", "-wx", "com.apple.FinderInfo", metadata_value, str(resource)], check=True)
            before = subprocess.check_output(["/usr/bin/xattr", "-px", "com.apple.FinderInfo", str(resource)])
            ipa = root / "fixture.ipa"
            archive_payload(payload, ipa)
            with zipfile.ZipFile(ipa) as archive:
                names = archive.namelist()
                metadata = [name for name in names if any(part.startswith("._") or part == "__MACOSX" for part in Path(name).parts)]
                self.assertEqual(metadata, [], "AppleDouble can be sealed by the signer then discarded by the installer")
                self.assertEqual(archive.read("Payload/Runner.app/fixture.txt"), b"synthetic resource")
            self.assertEqual(subprocess.check_output(["/usr/bin/xattr", "-px", "com.apple.FinderInfo", str(resource)]), before)

    def test_signed_resources_survive_metadata_aware_extraction(self):
        # Reproduce the actual chain: archive -> signer-style unzip -> resource
        # sealing -> installer-style metadata-aware extraction -> verification.
        # Use only a local synthetic macOS app and ad-hoc signature, no account.
        with tempfile.TemporaryDirectory(prefix="tio-signature-pipeline-") as directory:
            root = Path(directory).resolve()
            self.assertTrue(root.name.startswith("tio-signature-pipeline-"))
            payload = root / "source" / "Payload"
            app = payload / "Fixture.app"
            app.mkdir(parents=True)
            shutil.copyfile("/usr/bin/true", app / "Fixture")
            (app / "Fixture").chmod(0o755)
            (app / "Info.plist").write_bytes(plistlib.dumps({
                "CFBundleIdentifier": "com.example.tio-signature-fixture",
                "CFBundleExecutable": "Fixture", "CFBundlePackageType": "APPL",
                "CFBundleVersion": "1", "CFBundleShortVersionString": "1.0",
            }))
            resource = app / "fixture.txt"
            resource.write_bytes(b"synthetic signed resource")
            metadata = (b"\x00" * 8 + b"\x01" + b"\x00" * 23).hex()
            subprocess.run(["/usr/bin/xattr", "-wx", "com.apple.FinderInfo", metadata, str(resource)], check=True)
            for legacy in [True, False]:
                label = "legacy" if legacy else "fixed"
                before = root / (label + "-before.ipa")
                if legacy:
                    subprocess.run(["/usr/bin/ditto", "-c", "-k", "--keepParent", "Payload", str(before)], cwd=payload.parent, check=True)
                else:
                    archive_payload(payload, before)
                signing = root / (label + "-signing")
                signing.mkdir()
                subprocess.run(["/usr/bin/unzip", "-q", str(before), "-d", str(signing)], check=True)
                signed_app = signing / "Payload" / "Fixture.app"
                subprocess.run(["/usr/bin/codesign", "--force", "--sign", "-", "--timestamp=none", str(signed_app)], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                after = root / (label + "-signed.ipa")
                # A normal ZIP preserves what the signing tool sealed.
                with zipfile.ZipFile(after, "w", zipfile.ZIP_DEFLATED) as archive:
                    for path in sorted((signing / "Payload").rglob("*")):
                        if path.is_file(): archive.write(path, path.relative_to(signing))
                extracted = root / (label + "-installed")
                extracted.mkdir()
                subprocess.run(["/usr/bin/ditto", "-x", "-k", str(after), str(extracted)], check=True)
                if legacy:
                    # iOS does not retain FinderInfo. Remove this synthetic
                    # restored attribute so a detritus error cannot mask the
                    # missing sealed AppleDouble resource we are testing.
                    restored = extracted / "Payload" / "Fixture.app" / "fixture.txt"
                    self.assertTrue(root in restored.resolve().parents)
                    subprocess.run(["/usr/bin/xattr", "-d", "com.apple.FinderInfo", str(restored)], check=True)
                verdict = subprocess.run(["/usr/bin/codesign", "--verify", "--strict", "--verbose=2", str(extracted / "Payload" / "Fixture.app")], capture_output=True, text=True)
                if legacy:
                    self.assertNotEqual(verdict.returncode, 0, "Legacy metadata path must reproduce broken sealing")
                    self.assertIn("sealed resource", verdict.stderr)
                else:
                    self.assertEqual(verdict.returncode, 0, "Corrected input must preserve code signature resources")

    def test_arbitrary_ip_policy_removes_ats_precedence_conflicts(self):
        source = {
            "CFBundleIdentifier": BUNDLE,
            "NSAppTransportSecurity": {
                "NSAllowsArbitraryLoads": False,
                "NSAllowsArbitraryLoadsInWebContent": True,
                "NSExceptionDomains": {"vendor.example": {"NSExceptionMinimumTLSVersion": "TLSv1.2"}},
            },
        }
        before = copy.deepcopy(source)
        result = local_http_info(source)
        self.assertEqual(source, before)
        ats = result["NSAppTransportSecurity"]
        self.assertEqual(ats["NSExceptionDomains"], before["NSAppTransportSecurity"]["NSExceptionDomains"])
        self.assertTrue(ats["NSAllowsArbitraryLoads"])
        for key in FINE_GRAINED_ATS_KEYS:
            self.assertNotIn(key, ats)
        self.assertIn("明文", result["NSLocalNetworkUsageDescription"])
        self.assertIn("公网", result["NSLocalNetworkUsageDescription"])
        self.assertEqual(result["CFBundleDisplayName"], "TurboIO Research")

    def test_global_allowance_is_explicit_even_in_strict_host(self):
        for present in [False, True]:
            for value in [False, True]:
                ats = {key: value for key in FINE_GRAINED_ATS_KEYS} if present else {}
                with self.subTest(present=present, value=value):
                    result = local_http_info({"CFBundleIdentifier": BUNDLE, "NSAppTransportSecurity": ats})
                    self.assertEqual(result["NSAppTransportSecurity"], {"NSAllowsArbitraryLoads": True})

    def test_existing_ip_exceptions_do_not_override_global_http(self):
        result = local_http_info({"CFBundleIdentifier": BUNDLE, "NSAppTransportSecurity": {
            "NSExceptionDomains": {
                "203.0.113.20": {"NSExceptionAllowsInsecureHTTPLoads": False},
                "2001:db8::20": {"NSExceptionMinimumTLSVersion": "TLSv1.2"},
                "vendor.example": {"NSExceptionAllowsInsecureHTTPLoads": False},
            },
        }})
        domains = result["NSAppTransportSecurity"]["NSExceptionDomains"]
        self.assertTrue(domains["203.0.113.20"]["NSExceptionAllowsInsecureHTTPLoads"])
        self.assertTrue(domains["2001:db8::20"]["NSExceptionAllowsInsecureHTTPLoads"])
        self.assertEqual(domains["2001:db8::20"]["NSExceptionMinimumTLSVersion"], "TLSv1.2")
        self.assertFalse(domains["vendor.example"]["NSExceptionAllowsInsecureHTTPLoads"])

    def test_binary_marker_rejects_old_pinned_or_https_only_builds(self):
        current = HTTP_POLICY.encode() + b"\0" + BUNDLE.encode() + b"\0"
        validate_http_addon(current)
        for addon in [b"turboio-https-only\0" + BUNDLE.encode() + b"\0",
                      b"http://192.168.31.101:8080/v1/chat/completions\0" + BUNDLE.encode() + b"\0",
                      HTTP_POLICY.encode() + b"\0com.rayneo.venus.pub\0"]:
            with self.assertRaises(ValueError):
                validate_http_addon(addon)

    def test_existing_purpose_is_preserved(self):
        result = local_http_info({"CFBundleIdentifier": BUNDLE, "NSLocalNetworkUsageDescription": "Original purpose"})
        self.assertTrue(result["NSLocalNetworkUsageDescription"].startswith("Original purpose"))
        self.assertIn("公网", result["NSLocalNetworkUsageDescription"])

    def test_navigation_addon_requires_matching_resources(self):
        ordinary = HTTP_POLICY.encode() + b"\0" + BUNDLE.encode() + b"\0"
        navigation = ordinary + AMAP_POLICY.encode() + b"\0_OBJC_CLASS_$_AMapNaviWalkManager\0"
        self.assertFalse(validate_amap_addon(ordinary, None))
        self.assertFalse(validate_amap_addon(ordinary + b"AMapNaviWalkManager\0", None), "Offline dynamic class-name strings are not linked SDK code")
        self.assertTrue(validate_amap_addon(navigation, "/example/sdk"))
        for addon, root, background in [(navigation, None, False), (ordinary, "/example/sdk", False),
                                        (ordinary, None, True), (ordinary + b"_OBJC_CLASS_$_AMapNaviWalkManager\0", "/example/sdk", False),
                                        (ordinary + AMAP_POLICY.encode() + b"\0", "/example/sdk", False)]:
            with self.subTest(root=root, background=background), self.assertRaises(ValueError):
                validate_amap_addon(addon, root, background)

    def test_navigation_purpose_and_background_are_non_destructive(self):
        source = {"CFBundleIdentifier": BUNDLE, "UIBackgroundModes": ["audio", "bluetooth-central"]}
        before = copy.deepcopy(source)
        result = navigation_info(source)
        self.assertEqual(result["UIBackgroundModes"], source["UIBackgroundModes"])
        self.assertIn("导航", result["NSLocationWhenInUseUsageDescription"])
        result = navigation_info(source, True)
        self.assertEqual(result["UIBackgroundModes"], ["audio", "bluetooth-central", "location"])
        self.assertEqual(navigation_info(result, True), result)
        self.assertEqual(source, before)
        existing = {"NSLocationWhenInUseUsageDescription": "Original purpose", "UIBackgroundModes": ["location"]}
        self.assertEqual(navigation_info(existing, True), existing)
        self.assertNotIn("UIBackgroundModes", navigation_info({}))
        self.assertEqual(navigation_info({}, True)["UIBackgroundModes"], ["location"])
        with self.assertRaises(ValueError):
            navigation_info({"UIBackgroundModes": "location"}, True)

    def test_wrong_bundle_fails_closed(self):
        with self.assertRaises(ValueError):
            local_http_info({"CFBundleIdentifier": "com.rayneo.venus.pub"})


if __name__ == "__main__":
    unittest.main()
