"""Select the Android sources included in the TMG4 addon."""
from pathlib import Path
import sys

SOURCE = Path(__file__).resolve().parent / 'src/com/turboio/addon'
EXCLUDED = frozenset(('MapConfiguration.java', 'NavCore.java', 'NavSessionPolicy.java', 'NavSimulation.java', 'NavigationUI.java', 'NativeNavCodec.java', 'NativeNavRoute.java', 'NativeNavSession.java', 'NativeNavigation.java', 'FocusCodec.java', 'FocusController.java', 'FocusUI.java', 'ReaderBridge.java', 'ReaderCodec.java', 'ReaderLibrary.java', 'ReaderUI.java', 'MusicApi.java', 'MusicBridge.java', 'MusicCodec.java', 'MusicPlayer.java', 'MusicUI.java'))

def sources():
    return sorted(p for p in SOURCE.glob('*.java') if p.name not in EXCLUDED)

if __name__ == '__main__':
    Path(sys.argv[1]).write_text(''.join('"' + p.as_posix() + '"\n' for p in sources()), encoding='utf-8')
