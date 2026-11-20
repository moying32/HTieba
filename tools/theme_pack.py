"""Create the documented data-only .h2theme container. No third-party dependencies."""
import argparse
import io
import json
from pathlib import Path
import zipfile

MAGIC = b'HYDROGEN-THEME/1\n'

def pack(source: Path, destination: Path):
    theme = json.loads(source.read_text(encoding='utf8'))
    assert theme['format'] == 'hydrogen-tieba-theme'
    assert theme['schemaVersion'] == 1 and theme['target'] == 'dev.hydrogen.tieba'
    raw = json.dumps(theme, ensure_ascii=False, indent=2).encode('utf8')
    if len(raw) > 32768: raise ValueError('theme.json exceeds 32 KB')
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('theme.json', raw)
    result = MAGIC + buffer.getvalue()
    if len(result) > 262144: raise ValueError('Package exceeds 256 KB')
    destination.write_bytes(result)
    print(destination)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('json', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    pack(args.json, args.output)
