import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from addon_assets import assets
bundled = assets()
for path in ('assets/turboio/reader/demo-7392.txt', 'assets/turboio/licenses/music-mit.txt', 'assets/turboio/art/music.png', 'assets/turboio/art/reading.png', 'assets/turboio/art/navigation.png', 'assets/turboio/art/focus.png', 'assets/turboio/art/focus-tomato-v2.png'):
    assert path not in bundled, path
for path in ('assets/turboio/art/dashboard-editor.png', 'assets/turboio/art/app-gallery.png', 'assets/turboio/gallery/catalog.json'):
    assert path in bundled, path
print('TMG4 asset selection passed')
