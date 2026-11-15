"""News business is gone from the Android addon; shared channel and other features stay.

Checks the removal itself: deleted sources and asset, no remaining wiring,
no bundled news asset, and the generic host symbol still mapped.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src/com/turboio/addon"
ART = ROOT / "assets/art"

failures = []


def check(condition, message):
    if not condition:
        failures.append(message)


def read(name):
    return (SRC / name).read_text(encoding="utf-8")


for name in ("NewsUI.java", "NewsTele.java"):
    check(not (SRC / name).exists(), name + " still exists")
check(not (ART / "news.png").exists(), "assets/art/news.png still exists")

for path in sorted(SRC.glob("*.java")):
    body = path.read_text(encoding="utf-8")
    for token in ("NewsUI", "NewsTele"):
        check(token not in body, path.name + " still references " + token)

transfer = read("NativeTransfer.java")
check("biz==20" not in transfer, "NativeTransfer still routes business 20")
check("biz!=15" in transfer, "NativeTransfer lost the launcher business gate")

for name in ("BackgroundWork.java", "MusicPlayer.java", "TurboAddon.java"):
    body = read(name)
    check('"新闻"' not in body, name + " still registers the news background task")

editorial = read("EditorialUI.java")
tabs = re.search(r"labels\s*=\s*\{([^}]*)\}", editorial)
check(tabs is not None, "EditorialUI tab labels not found")
if tabs is not None:
    check("新闻" not in tabs.group(1), "EditorialUI still shows a news tab")
    check("CaptionUI.show" in editorial, "EditorialUI lost the caption tab")

caption = read("CaptionUI.java")
check("EditorialUI.input" in caption, "CaptionUI does not use the shared input control")

host_business = read("HostBusiness.java")
check('"TELEPROMPTER"' in host_business and '"20"' in host_business, "HostBusiness lost the generic teleprompter symbol")

sys.path.insert(0, str(ROOT))
from addon_assets import assets

bundled = assets()
check("assets/turboio/art/news.png" not in bundled, "news asset is still bundled")

if failures:
    for message in failures:
        print("FAIL: " + message)
    raise SystemExit(1)
print("News removal checks passed")
