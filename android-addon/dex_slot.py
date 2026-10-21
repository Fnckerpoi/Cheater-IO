"""Choose the first DEX slot after all vendor DEX files, at least classes4."""
import re


def addon_dex_slot(names):
    indices = [1 if name == 'classes.dex' else int(match.group(1))
               for name in names
               if (match := re.fullmatch(r'classes([2-9][0-9]*|1[0-9]+)?\.dex', name))]
    return 'classes%d.dex' % max(4, max(indices, default=0) + 1)

