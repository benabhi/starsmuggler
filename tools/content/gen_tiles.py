# Generates tiles.json from the map tile scans (recursos/map_tiles/individual_tiles), transcribed
# by hand below, and the processed tile images the app shows. Run from the repo root:
#   python tools/content/gen_tiles.py
#
# Each tile is half a planet. `side` is where the planet is on the scan (left or right); the
# three edge slots (0 top, 1 middle, 2 bottom) are where travel lines leave the planet's flat
# edge to meet the other tile of the planet. Positions are symbol centers as fractions of the
# scan. Restriction is the symbol color (r204g); `air` marks a white area name (good air inside).
import json
import os

SCANS = "recursos/map_tiles/individual_tiles/"
DATA = "content/src/main/assets/content/tiles.json"
IMAGES = "app/src/main/assets/tiles/"


def area(id, type, restriction, x, y, air=False, bank=False, medical=False):
    a = {"id": id, "type": type, "restriction": restriction, "position": {"x": x, "y": y}}
    if air:
        a["ownAir"] = True
    if bank:
        a["bank"] = True
    if medical:
        a["medical"] = True
    return a


def link(a, b, steps=None):
    """A connector between two areas: triple when steps is None, else single with land steps."""
    if steps is None:
        return {"from": a, "to": b, "kind": "triple"}
    return {"from": a, "to": b, "kind": "single", "steps": steps}


def edge(a, slot, kind, steps=None):
    c = {"from": a, "edgeSlot": slot, "kind": kind}
    if steps is not None:
        c["steps"] = steps
    return c


TILES = [
    {"id": "A", "side": "right", "areas": [
        area("space_station", "space_station", "purple", 0.16, 0.51, air=True, bank=True),
        area("industrial", "industrial", "yellow", 0.61, 0.41, air=True, bank=True),
        area("craters", "craters", "white", 0.84, 0.49),
        area("mining", "mining", "white", 0.63, 0.63),
    ], "connectors": [
        link("industrial", "craters", 3), link("industrial", "mining"), link("mining", "craters", 4),
        edge("industrial", 0, "single", 7), edge("craters", 1, "triple"), edge("mining", 2, "single"),
    ]},
    {"id": "B", "side": "left", "areas": [
        area("rough_terrain", "rough_terrain", "white", 0.25, 0.27),
        area("military", "military", "red", 0.13, 0.53, air=True, bank=True),
        area("prison", "prison", "red", 0.39, 0.41, air=True),
        area("colony", "colony", "white", 0.19, 0.68, air=True, bank=True, medical=True),
    ], "connectors": [
        link("rough_terrain", "prison"), link("rough_terrain", "colony", 5), link("prison", "colony", 4),
        link("military", "colony"),
        edge("rough_terrain", 0, "single"), edge("military", 1, "triple"), edge("colony", 2, "single", 7),
    ]},
    {"id": "C", "side": "right", "areas": [
        area("space_station", "space_station", "purple", 0.15, 0.49, air=True, bank=True),
        area("rough_terrain", "rough_terrain", "white", 0.78, 0.24),
        area("scientific", "scientific", "red", 0.64, 0.50, air=True, bank=True),
        area("city", "city", "yellow", 0.85, 0.51, air=True, bank=True, medical=True),
        area("industrial", "industrial", "yellow", 0.78, 0.72, air=True, bank=True),
    ], "connectors": [
        link("rough_terrain", "city", 2), link("scientific", "city"), link("scientific", "industrial"),
        link("city", "industrial"),
        edge("rough_terrain", 0, "single", 4), edge("city", 1, "triple"), edge("industrial", 2, "single"),
    ]},
    {"id": "D", "side": "left", "areas": [
        area("colony", "colony", "white", 0.19, 0.30, air=True, bank=True, medical=True),
        area("gambling", "gambling", "red", 0.15, 0.49, air=True, bank=True),
        area("spaceport", "spaceport", "white", 0.38, 0.49, air=True, bank=True),
        area("industrial", "industrial", "yellow", 0.18, 0.69, air=True, bank=True),
    ], "connectors": [
        link("colony", "gambling"), link("colony", "spaceport", 3), link("gambling", "industrial"),
        link("spaceport", "industrial", 3),
        edge("colony", 0, "single"), edge("gambling", 1, "triple"), edge("industrial", 2, "single", 6),
    ]},
    {"id": "E", "side": "right", "areas": [
        area("space_station", "space_station", "purple", 0.15, 0.53, air=True, bank=True),
        area("rural", "rural", "white", 0.79, 0.24, air=True, bank=True),
        area("city", "city", "yellow", 0.66, 0.52, air=True, bank=True, medical=True),
        area("prison", "prison", "red", 0.86, 0.51, air=True),
        area("mining", "mining", "white", 0.78, 0.79),
    ], "connectors": [
        link("rural", "city", 2), link("city", "prison"), link("city", "mining", 3), link("rural", "mining", 3),
        edge("city", 0, "single", 4), edge("prison", 1, "triple"), edge("city", 2, "single"),
    ]},
    {"id": "F", "side": "left", "areas": [
        area("rural", "rural", "white", 0.11, 0.21, air=True, bank=True),
        area("slum_north", "slum", "yellow", 0.34, 0.33, air=True, bank=True),
        area("city", "city", "yellow", 0.10, 0.50, air=True, bank=True, medical=True),
        area("gambling", "gambling", "red", 0.36, 0.57, air=True, bank=True),
        area("slum_south", "slum", "white", 0.24, 0.73, air=True, bank=True),
    ], "connectors": [
        link("rural", "slum_north"), link("rural", "city", 2), link("slum_north", "gambling"),
        link("city", "slum_south", 2), link("gambling", "slum_south"),
        edge("rural", 0, "single"), edge("city", 1, "triple"), edge("slum_south", 2, "single", 3),
    ]},
    {"id": "G", "side": "right", "areas": [
        area("slum", "slum", "yellow", 0.75, 0.29, air=True, bank=True),
        area("palace", "palace", "red", 0.60, 0.52, air=True),
        area("spaceport", "spaceport", "white", 0.89, 0.52, air=True, bank=True),
        area("city", "city", "yellow", 0.77, 0.67, air=True, bank=True, medical=True),
    ], "connectors": [
        link("slum", "palace"), link("slum", "city"), link("slum", "spaceport"), link("palace", "city"),
        link("city", "spaceport"),
        edge("slum", 0, "single", 8), edge("spaceport", 1, "triple"), edge("city", 2, "single"),
    ]},
    {"id": "H", "side": "left", "areas": [
        area("craters", "craters", "white", 0.25, 0.31),
        area("ruins", "ruins", "white", 0.11, 0.55),
        area("military", "military", "red", 0.37, 0.55, air=True, bank=True),
        area("spaceport", "spaceport", "white", 0.27, 0.74, air=True, bank=True),
    ], "connectors": [
        link("craters", "ruins", 2), link("craters", "military"), link("ruins", "spaceport", 2),
        link("military", "spaceport"),
        edge("craters", 0, "single"), edge("ruins", 1, "triple"), edge("spaceport", 2, "single", 5),
    ]},
    {"id": "I", "side": "right", "areas": [
        area("colony", "colony", "white", 0.78, 0.37, air=True, bank=True, medical=True),
        area("mining", "mining", "white", 0.57, 0.54),
        area("scientific", "scientific", "red", 0.82, 0.54, air=True, bank=True),
        area("rough_terrain", "rough_terrain", "white", 0.79, 0.80),
    ], "connectors": [
        link("colony", "mining", 3), link("colony", "scientific"), link("mining", "scientific", 3),
        link("mining", "rough_terrain", 2),
        edge("colony", 0, "single", 3), edge("scientific", 1, "triple"), edge("rough_terrain", 2, "single"),
    ]},
    {"id": "J", "side": "left", "areas": [
        area("ruins", "ruins", "white", 0.22, 0.26),
        area("industrial", "industrial", "yellow", 0.13, 0.53, air=True, bank=True),
        area("colony", "colony", "white", 0.39, 0.57, air=True, bank=True, medical=True),
        area("rural", "rural", "white", 0.22, 0.73, air=True, bank=True),
    ], "connectors": [
        link("ruins", "industrial", 4), link("ruins", "colony", 3), link("industrial", "colony", 2),
        link("industrial", "rural", 3), link("rural", "colony"),
        edge("ruins", 0, "single"), edge("industrial", 1, "triple"), edge("rural", 2, "single", 5),
    ]},
    {"id": "K", "side": "right", "areas": [
        area("slum", "slum", "white", 0.65, 0.29, air=True, bank=True),
        area("spaceport", "spaceport", "white", 0.68, 0.48, air=True, bank=True),
        area("mining", "mining", "white", 0.86, 0.48, air=True),
        area("industrial", "industrial", "yellow", 0.62, 0.70, air=True, bank=True),
    ], "connectors": [
        link("slum", "spaceport"), link("slum", "industrial"), link("spaceport", "mining"),
        link("spaceport", "industrial"),
        edge("spaceport", 0, "single", 10), edge("mining", 1, "triple"), edge("spaceport", 2, "single"),
    ]},
    {"id": "asteroids", "areas": [
        area("asteroid_1", "asteroid", "purple", 0.16, 0.55),
        area("asteroid_2", "asteroid", "purple", 0.33, 0.34),
        area("asteroid_3", "asteroid", "purple", 0.53, 0.21),
        area("asteroid_4", "asteroid", "purple", 0.73, 0.12),
        area("asteroid_5", "asteroid", "purple", 0.39, 0.81),
        area("asteroid_6", "asteroid", "purple", 0.85, 0.42),
        area("space_station", "space_station", "purple", 0.60, 0.59, air=True, bank=True),
    ], "connectors": []},
]

SCAN_NAMES = {t["id"]: "tile_%s.png" % ("ASTEROIDS" if t["id"] == "asteroids" else t["id"]) for t in TILES}

tiles = []
for t in TILES:
    out = {"id": t["id"]}
    if "side" in t:
        out["planetSide"] = t["side"]
    out["image"] = "tiles/%s.webp" % t["id"].lower()
    out["areas"] = t["areas"]
    out["connectors"] = t["connectors"]
    tiles.append(out)

with open(DATA, "w", encoding="utf-8", newline="\n") as f:
    json.dump({"formatVersion": 1, "tiles": tiles}, f, ensure_ascii=False, indent=1)
    f.write("\n")

# The app shows the scans retouched as WebP (SMUG-40); the originals stay in recursos/, outside the repo.
if os.path.isdir(SCANS):
    import sys

    sys.path.insert(0, os.path.dirname(__file__))
    import retouch_tiles

    retouch_tiles.run({t["id"]: SCAN_NAMES[t["id"]] for t in TILES})

print(len(tiles), "tiles,", sum(len(t["areas"]) for t in tiles), "areas,", sum(len(t["connectors"]) for t in tiles), "connectors")
