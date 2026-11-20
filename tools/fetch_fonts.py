"""Fetch unmodified OFL fonts and retain their licenses and SHA-256 provenance."""
import concurrent.futures
import hashlib
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
FONTS = [
    ('noto_sans_sc', 'https://raw.githubusercontent.com/google/fonts/main/ofl/notosanssc/NotoSansSC%5Bwght%5D.ttf', 'https://raw.githubusercontent.com/google/fonts/main/ofl/notosanssc/OFL.txt'),
    ('wenkai_lite', 'https://raw.githubusercontent.com/lxgw/LxgwWenKai-Lite/main/fonts/TTF/LXGWWenKaiLite-Regular.ttf', 'https://raw.githubusercontent.com/lxgw/LxgwWenKai-Lite/main/OFL.txt'),
    ('zcool_xiaowei', 'https://raw.githubusercontent.com/google/fonts/main/ofl/zcoolxiaowei/ZCOOLXiaoWei-Regular.ttf', 'https://raw.githubusercontent.com/google/fonts/main/ofl/zcoolxiaowei/OFL.txt'),
]

def download(item):
    name, url, license_url = item
    font_path = ROOT / 'app/src/main/res/font' / (name + '.ttf')
    license_path = ROOT / 'app/src/main/assets/font_licenses' / (name + '.txt')
    for source, target in [(url, font_path), (license_url, license_path)]:
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.exists():
            request = urllib.request.Request(source, headers={'User-Agent': 'HydrogenTieba-font-build'})
            with urllib.request.urlopen(request, timeout=60) as response:
                data = response.read(32 * 1024 * 1024)
            if target == font_path and data[:4] not in (b'\x00\x01\x00\x00', b'OTTO'):
                raise ValueError('Invalid font response: ' + name)
            if target == license_path and b'SIL OPEN FONT LICENSE' not in data.upper():
                raise ValueError('Missing OFL license: ' + name)
            target.write_bytes(data)
    return dict(name=name, source=url, license_source=license_url, bytes=font_path.stat().st_size,
                sha256=hashlib.sha256(font_path.read_bytes()).hexdigest())

if __name__ == '__main__':
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        records = list(pool.map(download, FONTS))
    (ROOT / 'app/src/main/assets/font_licenses/provenance.json').write_text(json.dumps(records, indent=2), encoding='utf8')
    print(json.dumps(records, indent=2))
