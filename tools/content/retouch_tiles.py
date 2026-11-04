# Retouches the map tile scans for the app (SMUG-40, PRD P4): cleans the scanner's edge lines,
# balances the color and doubles the size for high-density screens. The image keeps its aspect
# ratio, so the area positions in tiles.json (fractions of the scan) still match. Used by
# gen_tiles.py; it can also run alone from the repo root:
#   python tools/content/retouch_tiles.py
import os

import numpy

from PIL import Image, ImageEnhance, ImageFilter, ImageOps

SCANS = "recursos/map_tiles/individual_tiles/"
IMAGES = "app/src/main/assets/tiles/"

# Pixels at each edge of a scan that carry the paper border and scanner lines.
EDGE = 10
# Twice the scan for 2x-3x screens; the scans hold no more detail than that.
SCALE = 2
# The share of darkest and lightest pixels clipped when stretching each channel.
CUTOFF = 0.5
SATURATION = 1.12
CONTRAST = 1.06
QUALITY = 88


def clean_edges(image):
    """Replaces the outer band with the pixels just inside it, so no paper or scan line shows."""
    pixels = numpy.asarray(image)
    inner = pixels[EDGE:-EDGE, EDGE:-EDGE]
    return Image.fromarray(numpy.pad(inner, ((EDGE, EDGE), (EDGE, EDGE), (0, 0)), mode="edge"))


def balance(image):
    """Stretches each channel between its darkest and lightest pixels: neutral black space, white labels."""
    channels = [ImageOps.autocontrast(c, cutoff=CUTOFF) for c in image.split()]
    image = Image.merge("RGB", channels)
    image = ImageEnhance.Color(image).enhance(SATURATION)
    return ImageEnhance.Contrast(image).enhance(CONTRAST)


def retouch(scan):
    image = Image.open(scan).convert("RGB")
    image = balance(clean_edges(image))
    w, h = image.size
    image = image.resize((w * SCALE, h * SCALE), Image.LANCZOS)
    return image.filter(ImageFilter.UnsharpMask(radius=1.6, percent=60, threshold=3))


def run(names):
    """Writes IMAGES/<id>.webp for each tile id in [names], mapped to its scan file name."""
    os.makedirs(IMAGES, exist_ok=True)
    for tile_id, scan in names.items():
        retouch(SCANS + scan).save(IMAGES + "%s.webp" % tile_id.lower(), "WEBP", quality=QUALITY, method=6)


if __name__ == "__main__":
    ids = ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "asteroids"]
    run({i: "tile_%s.png" % ("ASTEROIDS" if i == "asteroids" else i) for i in ids})
    print(len(ids), "tiles retouched")
