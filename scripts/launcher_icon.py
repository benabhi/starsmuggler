# Builds the adaptive launcher icon foreground (SMUG-7): the "star smuggler" logo of the box
# cover, set in the theme's display font (Shrikhand), orange-red on the cream background.
# The cover scan is too small to crop the logo, so it is redrawn from the font.
# Usage: python scripts/launcher_icon.py [preview.png]
# The layer is ink on transparency, so it also works as the themed (monochrome) icon; the
# cream comes from the adaptive icon's background colour.
import os
import sys
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'app', 'src', 'main', 'res')
FONT = os.path.join(RES, 'font', 'shrikhand_regular.ttf')
RED = (0xE8, 0x43, 0x2B)
CREAM = (0xF3, 0xEA, 0xD3)

# Work at 16x mdpi: the 108 dp layer is 1728 px.
SIZE = 1728
# Launchers mask the layer down to the central 66 dp; keep the logo inside a circle of 64 dp.
SAFE = SIZE * 64 / 108
# The cover logo climbs to the right.
TILT = 7


def logo_mask():
    font = ImageFont.truetype(FONT, 400)
    lines = ['star', 'smuggler']
    boxes = [font.getbbox(line) for line in lines]
    widths = [b[2] - b[0] for b in boxes]
    heights = [b[3] - b[1] for b in boxes]
    gap = 40
    w = max(widths)
    h = sum(heights) + gap
    canvas = Image.new('L', (w + 200, h + 200), 0)
    draw = ImageDraw.Draw(canvas)
    y = 100
    for line, box, width, height in zip(lines, boxes, widths, heights):
        # "star" sits over the start of "smuggler", as on the box.
        x = 100 if line == 'smuggler' else 100 + (w - width) // 6
        draw.text((x - box[0], y - box[1]), line, font=font, fill=255)
        y += height + gap
    canvas = canvas.crop(canvas.getbbox())
    canvas = canvas.rotate(TILT, resample=Image.BICUBIC, expand=True)
    return canvas.crop(canvas.getbbox())


def foreground():
    mask = logo_mask()
    # Fit the logo's box inside the safe circle: its diagonal is the circle's diameter.
    w, h = mask.size
    scale = SAFE / (w * w + h * h) ** 0.5
    mask = mask.resize((round(w * scale), round(h * scale)), Image.LANCZOS)
    alpha = Image.new('L', (SIZE, SIZE), 0)
    alpha.paste(mask, ((SIZE - mask.width) // 2, (SIZE - mask.height) // 2))
    layer = Image.new('RGBA', (SIZE, SIZE), RED + (0,))
    layer.putalpha(alpha)
    return layer


def main():
    layer = foreground()
    for name, px in [('mdpi', 108), ('hdpi', 162), ('xhdpi', 216), ('xxhdpi', 324), ('xxxhdpi', 432)]:
        d = os.path.join(RES, 'mipmap-' + name)
        os.makedirs(d, exist_ok=True)
        layer.resize((px, px), Image.LANCZOS).save(os.path.join(d, 'ic_launcher_foreground.png'), optimize=True)
    if len(sys.argv) > 1:
        # What a round launcher shows: the cream disc with the logo, cut to the 66 dp viewport.
        preview = Image.new('RGBA', (SIZE, SIZE), CREAM + (255,))
        preview.alpha_composite(layer)
        inset = round(SIZE * 21 / 108)
        preview = preview.crop((inset, inset, SIZE - inset, SIZE - inset))
        circle = Image.new('L', preview.size, 0)
        ImageDraw.Draw(circle).ellipse((0, 0) + preview.size, fill=255)
        preview.putalpha(circle)
        preview.resize((512, 512), Image.LANCZOS).save(sys.argv[1])
    print('ok')


if __name__ == '__main__':
    main()
