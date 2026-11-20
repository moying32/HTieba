from pathlib import Path
from PIL import Image, ImageOps, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'media' / 'bilibili-promo'
OUT.mkdir(parents=True, exist_ok=True)
names = [
'v066-final-home','v062-final-home','v061-thread','v061-comments-scroll1',
'v061-comments-scroll3','v061-floor-sheet','v061-dark150-font50-feed','v058-dark-feed',
'v065-gallery-open','v065-gallery-first','v065-gallery-last','v066-final-portrait',
'v066-player-paused','v066-speed-menu','v058-size-settings','v061-ui150-preview',
'v058-appearance','v058-final-feed','v060-search-final-light75','v061-font150-preview',
]
font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',16)
sheet=Image.new('RGB',(5*260,4*570),(225,228,226))
d=ImageDraw.Draw(sheet)
for i,name in enumerate(names):
    p=ROOT/'.tools/checks/screenshots'/f'{name}.png'
    if not p.exists(): continue
    im=Image.open(p).convert('RGB'); im.thumbnail((240,520))
    x=(i%5)*260+10; y=(i//5)*570+10
    sheet.paste(im,(x,y)); d.text((x,y+525),name,fill='black',font=font)
sheet.save(OUT/'asset-contact.jpg')
print(OUT/'asset-contact.jpg')
