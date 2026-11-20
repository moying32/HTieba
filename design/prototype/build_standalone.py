"""Bundle the authored prototype into a portable, dependency-free HTML file."""
import base64
from pathlib import Path

ROOT = Path(__file__).resolve().parent
html = (ROOT / 'index.html').read_text(encoding='utf-8')
css = (ROOT / 'style.css').read_text(encoding='utf-8')
js = (ROOT / 'app.js').read_text(encoding='utf-8')
for filename in ('icon.svg', 'landscape.svg'):
    data = 'data:image/svg+xml;base64,' + base64.b64encode((ROOT / filename).read_bytes()).decode('ascii')
    html = html.replace(filename, data)
    js = js.replace(filename, data)
html = html.replace('<link rel="stylesheet" href="style.css">', '<style>\n' + css + '\n</style>')
html = html.replace('<script src="app.js"></script>', '<script>\n' + js + '\n</script>')
output = ROOT / '氢贴吧-交互设计稿.html'
output.write_text(html, encoding='utf-8')
print(f'Built {output} ({output.stat().st_size:,} bytes)')
