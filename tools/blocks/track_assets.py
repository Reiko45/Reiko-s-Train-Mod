#!/usr/bin/env python3
"""
Makes the track block's textures and models (straight and diagonal) and the freight wagon's screen. Run from the
repository root:

    python3 tools/blocks/track_assets.py
"""
import json
import math
import os
import zlib

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/reikotrains")


def noise(*key):
    return (zlib.crc32(repr(key).encode()) & 0xFFFF) / 0xFFFF * 2 - 1


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3]) + (255,)


def textures():
    os.makedirs(f"{ASSETS}/textures/block", exist_ok=True)
    # Rail: columns 0-1 the polished top, 2-3 the side, the rest the plain steel the ends use.
    rail = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            if x < 2:
                c = shade((196, 198, 204), 1 + 0.04 * noise(x, y))
            elif x < 4:
                c = shade((110, 104, 100), 1 + 0.08 * noise(x, y, 1))
            else:
                c = shade((128, 124, 120), 1 + 0.06 * noise(x, y, 2))
            rail.putpixel((x, y), c)
    rail.save(f"{ASSETS}/textures/block/track_rail.png")
    # Sleeper: rows 0-2 the top (wood grain along it), rows 3-4 its sides, the rest spare.
    sleeper = Image.new("RGBA", (16, 16))
    base = (92, 66, 44)
    for y in range(16):
        for x in range(16):
            grain = 0.9 + 0.12 * math.sin(x * 1.7 + y * 0.4 + noise(y) * 2)
            if y < 3:
                c = shade(base, grain * (1 + 0.05 * noise(x, y)))
                if y == 0 or y == 2:
                    c = shade(c, 0.88)
            else:
                c = shade(base, 0.72 * grain * (1 + 0.05 * noise(x, y)))
            sleeper.putpixel((x, y), c)
    sleeper.save(f"{ASSETS}/textures/block/track_sleeper.png")


def face(texture, uv, cull=None):
    f = {"texture": texture, "uv": uv}
    return f


def sleeper(z0, z1, rotation=None):
    e = {"from": [-4, 0, z0], "to": [20, 2, z1], "faces": {
        "up": face("#sleeper", [0, 0, 16, 3]),
        "north": face("#sleeper", [0, 3, 16, 5]), "south": face("#sleeper", [0, 3, 16, 5]),
        "east": face("#sleeper", [0, 3, 3, 5]), "west": face("#sleeper", [0, 3, 3, 5]),
        "down": face("#sleeper", [0, 0, 16, 3])}}
    if rotation:
        e["rotation"] = rotation
    return e


def rail(x0, z0, z1, rotation=None):
    e = {"from": [x0, 2, z0], "to": [x0 + 2, 4, z1], "faces": {
        "up": face("#rail", [0, 0, 2, 16]),
        "east": face("#rail", [2, 0, 4, 16]), "west": face("#rail", [2, 0, 4, 16]),
        "north": face("#rail", [4, 0, 6, 2]), "south": face("#rail", [4, 0, 6, 2])}}
    if rotation:
        e["rotation"] = rotation
    return e


def models():
    os.makedirs(f"{ASSETS}/models/block", exist_ok=True)
    textures_ = {"particle": "reikotrains:block/track_rail", "rail": "reikotrains:block/track_rail", "sleeper": "reikotrains:block/track_sleeper"}
    # Straight, running north-south: two sleepers, two rails (gauge 14 px, matching the curves).
    straight = {"parent": "block/block", "ambientocclusion": False, "textures": textures_,
                "elements": [sleeper(2.5, 5.5), sleeper(10.5, 13.5), rail(0, 0, 16), rail(14, 0, 16)]}
    with open(f"{ASSETS}/models/block/track_straight.json", "w") as out:
        json.dump(straight, out, indent=2)
    # Diagonal: the same, turned 45 degrees, from corner to corner (22.6 px long).
    half = 8 * math.sqrt(2)
    rot = {"origin": [8, 0, 8], "axis": "y", "angle": 45}
    z0, z1 = round(8 - half, 3), round(8 + half, 3)
    sleepers = []
    for centre in (8 - half * 2 / 3, 8, 8 + half * 2 / 3):
        sleepers.append(sleeper(round(centre - 1.5, 3), round(centre + 1.5, 3), rot))
    diagonal = {"parent": "block/block", "ambientocclusion": False, "textures": textures_,
                "elements": sleepers + [rail(0, z0, z1, rot), rail(14, z0, z1, rot)]}
    with open(f"{ASSETS}/models/block/track_diagonal.json", "w") as out:
        json.dump(diagonal, out, indent=2)
    os.makedirs(f"{ASSETS}/blockstates", exist_ok=True)
    with open(f"{ASSETS}/blockstates/track.json", "w") as out:
        json.dump({"variants": {
            "axis=z": {"model": "reikotrains:block/track_straight"},
            "axis=x": {"model": "reikotrains:block/track_straight", "y": 90},
            "axis=se": {"model": "reikotrains:block/track_diagonal"},
            "axis=ne": {"model": "reikotrains:block/track_diagonal", "y": 90},
        }}, out, indent=2)


def wagon_screen():
    """The freight wagon's screen: 18 x 6 slots over the player's inventory, in the vanilla container style."""
    columns, rows = 18, 6
    width, height = columns * 18 + 16, 114 + rows * 18
    img = Image.new("RGBA", (512, 256), (0, 0, 0, 0))
    bg, light, dark, black = (198, 198, 198, 255), (255, 255, 255, 255), (85, 85, 85, 255), (0, 0, 0, 255)
    for y in range(height):
        for x in range(width):
            img.putpixel((x, y), bg)
    # rounded outer border, like vanilla's
    for x in range(2, width - 2):
        img.putpixel((x, 0), black)
        img.putpixel((x, height - 1), black)
        img.putpixel((x, 1), light)
        img.putpixel((x, 2), light)
        img.putpixel((x, height - 2), dark)
        img.putpixel((x, height - 3), dark)
    for y in range(2, height - 2):
        img.putpixel((0, y), black)
        img.putpixel((width - 1, y), black)
        img.putpixel((1, y), light)
        img.putpixel((2, y), light)
        img.putpixel((width - 2, y), dark)
        img.putpixel((width - 3, y), dark)
    for (x, y) in ((1, 1), (width - 2, height - 2)):
        img.putpixel((x, y), black)
    for (x, y) in ((0, 0), (1, 0), (0, 1), (width - 1, 0), (width - 2, 0), (width - 1, 1), (0, height - 1), (1, height - 1),
                   (0, height - 2), (width - 1, height - 1), (width - 2, height - 1), (width - 1, height - 2)):
        img.putpixel((x, y), (0, 0, 0, 0))
    img.putpixel((width - 2, 1), bg)
    img.putpixel((1, height - 2), bg)

    def slot(sx, sy):
        for i in range(18):
            img.putpixel((sx - 1 + i, sy - 1), (55, 55, 55, 255))
            img.putpixel((sx - 1, sy - 1 + i), (55, 55, 55, 255))
            img.putpixel((sx - 1 + i, sy + 16), light)
            img.putpixel((sx + 16, sy - 1 + i), light)
        for i in range(16):
            for j in range(16):
                img.putpixel((sx + i, sy + j), (139, 139, 139, 255))
        img.putpixel((sx - 1, sy + 16), bg)
        img.putpixel((sx + 16, sy - 1), bg)

    for r in range(rows):
        for c in range(columns):
            slot(8 + c * 18, 18 + r * 18)
    inv_x = 8 + (columns - 9) * 9
    top = 103 + (rows - 4) * 18
    for r in range(3):
        for c in range(9):
            slot(inv_x + c * 18, top + r * 18)
    for c in range(9):
        slot(inv_x + c * 18, top + 58)
    os.makedirs(f"{ASSETS}/textures/gui", exist_ok=True)
    img.save(f"{ASSETS}/textures/gui/freight_wagon.png")


def main():
    textures()
    models()
    wagon_screen()
    print("track textures, models and the wagon screen written")


if __name__ == "__main__":
    main()
