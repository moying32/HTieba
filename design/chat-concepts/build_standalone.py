"""Build portable, offline HTML concepts without third-party resources."""
from pathlib import Path
import sys

sys.stdout.reconfigure(encoding='utf-8')
root = Path(__file__).resolve().parent
html = (root / 'index.html').read_text(encoding='utf-8')
css = (root / 'style.css').read_text(encoding='utf-8')
js = (root / 'app.js').read_text(encoding='utf-8')
assert '</script' not in js.lower()
html = html.replace('<link rel="stylesheet" href="style.css">', '<style>\n' + css + '\n</style>')
html = html.replace('<script src="app.js"></script>', '<script>\n' + js + '\n</script>')
for concept, filename in [
    ('a', '氢贴吧-私信与吧群-三方案.html'),
    ('a', '方案A-统一收件箱.html'),
    ('b', '方案B-双轨分区.html'),
    ('c', '方案C-紧凑对话流.html'),
]:
    output = root / filename
    output.write_text(html.replace('data-start-concept="a"', f'data-start-concept="{concept}"'), encoding='utf-8')
    print(f'{filename}: {output.stat().st_size:,} bytes')
