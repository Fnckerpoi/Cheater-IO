#!/usr/bin/env python3
"""Prepare a local, unsigned ordinary-addon IPA; never sign or install."""
import argparse
import copy
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import plistlib
import shutil
import subprocess
import zipfile

BUNDLE = "com.duriea.turboio.research"
HTTP_POLICY = "turboio-http-any-ip-v1"
AMAP_POLICY = "turboio-amap-navi-11.3.100-search-9.8.1-v1"
AMAP_RESOURCES = ("AMap.bundle", "AMapNavi.bundle", "AMapSearch.bundle")
FINE_GRAINED_ATS_KEYS = (
    "NSAllowsArbitraryLoadsForMedia",
    "NSAllowsArbitraryLoadsInWebContent",
    "NSAllowsLocalNetworking",
)


def local_http_info(info):
    result = copy.deepcopy(info)
    if result.get("CFBundleIdentifier") != BUNDLE:
        raise ValueError("Only the separate ordinary research bundle is allowed")
    ats = result.setdefault("NSAppTransportSecurity", {})
    # iOS ignores NSAllowsArbitraryLoads when any finer-grained key is present.
    # This is deliberately app-wide, not a per-plugin ATS exception.
    ats["NSAllowsArbitraryLoads"] = True
    for key in FINE_GRAINED_ATS_KEYS:
        ats.pop(key, None)
    for host, policy in ats.get("NSExceptionDomains", {}).items():
        try:
            ipaddress.ip_address(host)
        except ValueError:
            continue
        policy["NSExceptionAllowsInsecureHTTPLoads"] = True
    purpose = "访问你自行配置的 IP 模型服务（包括局域网与公网）；HTTP 会明文传输 API Key 和对话，可能被监听或篡改，请优先使用 HTTPS。"
    old = result.get("NSLocalNetworkUsageDescription", "")
    result["NSLocalNetworkUsageDescription"] = (old + "\n" if old else "") + purpose
    result["CFBundleDisplayName"] = "TurboIO Research"
    return result


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def archive_payload(payload, ipa):
    payload, ipa = Path(payload), Path(ipa)
    # Resource forks and Finder/xattr sidecars are not iOS resources. If the
    # signer seals ._ files and the installer drops them, verification fails.
    # Exclude metadata at archive time; never mutate the original app or xattrs.
    subprocess.run(["/usr/bin/ditto", "--norsrc", "--noextattr", "--noacl", "-c", "-k", "--keepParent", payload.name, str(ipa)], cwd=payload.parent, check=True)


def validate_http_addon(addon_bytes):
    if HTTP_POLICY.encode() + b"\0" not in addon_bytes or BUNDLE.encode() + b"\0" not in addon_bytes:
        raise ValueError("Compile the ordinary arbitrary-IP HTTP research addon first")


def validate_amap_addon(addon_bytes, sdk_root, background=False):
    enabled = AMAP_POLICY.encode() + b"\0" in addon_bytes
    has_sdk = b"_OBJC_CLASS_$_AMapNaviWalkManager\0" in addon_bytes
    if enabled != has_sdk:
        raise ValueError("Unsupported AMap build; rebuild with the pinned SDK integration")
    if enabled != bool(sdk_root):
        raise ValueError("AMap addon and --amap-sdk-root must be supplied together")
    if background and not enabled:
        raise ValueError("--navigation-background requires an AMap build and resources")
    return enabled


def navigation_info(info, background=False):
    result = copy.deepcopy(info)
    if not result.get("NSLocationWhenInUseUsageDescription"):
        result["NSLocationWhenInUseUsageDescription"] = "用于你主动开启的地图定位与步行、骑行、驾车导航；开始实时导航后使用位置，停止后结束。"
    if background:
        modes = result.setdefault("UIBackgroundModes", [])
        if not isinstance(modes, list) or any(not isinstance(mode, str) for mode in modes):
            raise ValueError("Invalid UIBackgroundModes; inspect the original app")
        if "location" not in modes:
            modes.append("location")
    return result


def prepare(app, addon, out, node, amap_sdk_root=None, navigation_background=False):
    app, addon = Path(app).resolve(strict=True), Path(addon).resolve(strict=True)
    out = Path(out).resolve()
    if out.exists() or out == app or app in out.parents:
        raise ValueError("Output must be a new separate directory; existing files are never overwritten")
    info = plistlib.loads((app / "Info.plist").read_bytes())
    if (info.get("CFBundleIdentifier"), info.get("CFBundleShortVersionString"), info.get("CFBundleVersion")) != ("com.rayneo.venus.pub", "1.0.5", "201"):
        raise ValueError("Requires original official 1.0.5 (201) input")
    if any((app / name).exists() for name in ["TurboIOPrivateBootstrap.json", "TurboIOKnowledgeConnection.json", "TIOAMapPrivate.json"]):
        raise ValueError("Input contains private bootstrap configuration")
    addon_bytes = addon.read_bytes()
    validate_http_addon(addon_bytes)
    amap_enabled = validate_amap_addon(addon_bytes, amap_sdk_root, navigation_background)
    experimental_symbols = [b"_TIOOTAFlashBuild", b"_TNVStart", b"_TMMusicConsume", b"_TFFocusConsume", b"_TIOOpenLocalTranslation"]
    if any(symbol in addon_bytes for symbol in experimental_symbols):
        raise ValueError("Only ordinary addon supported; no firmware/translation experimental modules")
    node = shutil.which(node) if node else shutil.which("node")
    if not node:
        raise ValueError("Node.js required; supply --node /absolute/path/to/node")
    version = subprocess.check_output([node, "--version"], text=True).strip().lstrip("v").split(".")
    if tuple(int(part) for part in version[:3]) < (22, 16, 0):
        raise ValueError("Node.js 22.16+ required")
    resource_tool = Path(__file__).resolve().parent / "amap-resources.mjs"
    resources = []
    if amap_enabled:
        sdk_root = Path(amap_sdk_root)
        if not sdk_root.is_absolute():
            raise ValueError("--amap-sdk-root must be absolute")
        sdk_root = sdk_root.resolve(strict=True)
        # Preflight all resources and destination collisions before creating output.
        resources = json.loads(subprocess.check_output([node, str(resource_tool), "--sdk-root", str(sdk_root), "--check-app", str(app)], text=True))
        if resources != list(AMAP_RESOURCES):
            raise ValueError("Unexpected AMap resource inventory")
    source_hash = sha256(app / "Runner")
    source_plist_hash = sha256(app / "Info.plist")
    out.mkdir(mode=0o700)
    prepared = out / "Payload" / "Runner.app"
    embed = Path(__file__).resolve().parent / "macho-embed.mjs"
    # Keep repository's exact UUID, encryption, architecture, dependency,
    # duplicate-injection and source-integrity preflights. No security bypass.
    subprocess.run([node, str(embed), str(app), str(prepared), str(addon), BUNDLE], check=True)
    plist = prepared / "Info.plist"
    if amap_enabled:
        subprocess.run([node, str(resource_tool), "--sdk-root", str(sdk_root), "--app", str(prepared)], check=True)
    updated = local_http_info(plistlib.loads(plist.read_bytes()))
    if amap_enabled:
        updated = navigation_info(updated, navigation_background)
    with open(plist, "wb") as stream:
        plistlib.dump(updated, stream)
    subprocess.run(["/usr/bin/plutil", "-lint", str(plist)], check=True)
    ipa = out / ("TurboIO-HTTP-Navigation-unsigned.ipa" if amap_enabled else "TurboIO-LocalHTTP-unsigned.ipa")
    archive_payload(out / "Payload", ipa)
    os.chmod(ipa, 0o600)
    subprocess.run(["/usr/bin/unzip", "-tq", str(ipa)], check=True)
    with zipfile.ZipFile(ipa) as archive:
        metadata = [name for name in archive.namelist() if any(part.startswith("._") or part == "__MACOSX" for part in Path(name).parts)]
        if metadata:
            raise ValueError("IPA contains macOS metadata; refuse signing input")
        if amap_enabled:
            for resource in AMAP_RESOURCES:
                prefix = "Payload/Runner.app/" + resource + "/"
                if not any(name.startswith(prefix) and not name.endswith("/") for name in archive.namelist()):
                    raise ValueError("IPA is missing AMap resource contents")
        if hashlib.sha256(archive.read("Payload/Runner.app/Frameworks/TurboIOPrivateAddon.dylib")).hexdigest() != sha256(addon):
            raise ValueError("Packaged addon integrity mismatch")
    preserved = source_hash == sha256(app / "Runner") and source_plist_hash == sha256(app / "Info.plist")
    if not preserved:
        raise ValueError("Source integrity check failed")
    report = {
        "status": "PREPARED_UNSIGNED_REQUIRES_SIDELOADLY_SIGNING",
        "bundle": BUNDLE,
        "http_endpoint_policy": HTTP_POLICY,
        "http_hosts": "numeric IPv4/IPv6, public and private; user configured",
        "http_port_range": [1, 65535],
        "addon_sha256": sha256(addon),
        "ipa_sha256": sha256(ipa),
        "source_preserved": preserved,
        "vendor_global_ats_policy_preserved": False,
        "app_wide_ats_relaxed": True,
        "fine_grained_ats_keys_removed": [key for key in FINE_GRAINED_ATS_KEYS if key in info.get("NSAppTransportSecurity", {})],
        "sideloadly_extra_injection": False,
        "macos_metadata_entries": 0,
        "no_keys_or_models_bundled": True,
        "no_device_install_or_firmware_write": True,
        "amap_enabled": amap_enabled,
        "amap_build_policy": AMAP_POLICY if amap_enabled else None,
        "amap_resource_bundles": resources,
        "amap_key_bundle_required": BUNDLE if amap_enabled else None,
        "navigation_background_requested": navigation_background,
        "navigation_background_declared": "location" in updated.get("UIBackgroundModes", []),
        "online_navigation_verified": False,
    }
    with open(out / "local-http-preparation.json", "x", encoding="utf-8") as stream:
        json.dump(report, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    print("Unsigned IPA:", ipa)
    return ipa


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--app", required=True)
    parser.add_argument("--addon", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--node")
    parser.add_argument("--amap-sdk-root", help="Absolute pinned SDK root; required for an AMap-linked addon")
    parser.add_argument("--navigation-background", action="store_true", help="Explicitly add location background mode if missing; never grants OS permission")
    args = parser.parse_args()
    prepare(args.app, args.addon, args.out, args.node, args.amap_sdk_root, args.navigation_background)


if __name__ == "__main__":
    main()
