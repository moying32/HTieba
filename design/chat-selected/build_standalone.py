"""Bundle the selected C design into a single offline document."""
from pathlib import Path
import sys
sys.stdout.reconfigure(encoding='utf-8')
root=Path(__file__).resolve().parent
html=(root/'index.html').read_text(encoding='utf-8')
for filename in ['base.css','refinements.css']:
    html=html.replace(f'<link rel="stylesheet" href="{filename}">','<style>\n'+(root/filename).read_text(encoding='utf-8')+'\n</style>')
for filename in ['base.js','refinements.js']:
    js=(root/filename).read_text(encoding='utf-8')
    assert '</script' not in js.lower()
    html=html.replace(f'<script src="{filename}"></script>','<script>\n'+js+'\n</script>')
output=root/'氢贴吧-私信与吧群-C方案深化.html'
output.write_text(html,encoding='utf-8')
print(f'{output.name}: {output.stat().st_size:,} bytes')
