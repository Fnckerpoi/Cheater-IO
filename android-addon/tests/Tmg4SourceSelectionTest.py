import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tmg4_sources import sources
names = {path.name for path in sources()}
for name in ('NavigationUI.java','NavCore.java','NativeNavigation.java','FocusCodec.java','ReaderCodec.java','MusicPlayer.java'):
    assert name not in names, name
for name in ('NavGlasses.java','DisplayCodec.java','SharedCodec.java','CloudCaption.java','ScadaCaptionController.java','AppCodec.java'):
    assert name in names, name
print('TMG4 source selection passed')
