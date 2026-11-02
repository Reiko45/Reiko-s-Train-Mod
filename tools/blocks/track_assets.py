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




# ----------------------------------------------------------------------------------------------------------------------
# Signals and stations


def solid(name, base, edge=None, noise_amount=0.05):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = shade(base, 1 + noise_amount * noise(name, x, y))
            if edge and (x in (0, 15) or y in (0, 15)):
                c = shade(edge, 1)
            img.putpixel((x, y), c)
    img.save(f"{ASSETS}/textures/block/{name}.png")


def lamp(name, color, lit):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if r > 7.5:
                c = (20, 20, 22, 255)
            elif lit:
                f = 1.25 - 0.45 * (r / 7.5)
                c = shade(color, f)
                if r < 2.5:
                    c = shade((255, 255, 255), 1) if color[0] < 200 or color[1] < 200 else c
                    c = tuple(min(255, int(v * 0.6 + w * 0.4)) for v, w in zip(c[:3], (255, 255, 240))) + (255,)
            else:
                c = shade(color, 0.28 + 0.06 * (1 - r / 7.5))
            img.putpixel((x, y), c)
    img.save(f"{ASSETS}/textures/block/{name}.png")


def signal_textures():
    solid("signal_metal", (64, 66, 72))
    solid("signal_head", (22, 22, 24))
    lamp("signal_red_on", (255, 60, 40), True)
    lamp("signal_red_off", (255, 60, 40), False)
    lamp("signal_green_on", (60, 255, 110), True)
    lamp("signal_green_off", (60, 255, 110), False)
    # The station board: navy with a cream border round the 16 x 9 face; the posts are painted metal.
    board = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = shade((26, 44, 96), 1 + 0.04 * noise("board", x, y))
            if y < 9 and (x in (0, 15) or y in (0, 8)):
                c = shade((232, 222, 190), 1)
            board.putpixel((x, y), c)
    board.save(f"{ASSETS}/textures/block/station_board.png")
    solid("station_post", (44, 52, 80))


def element(frm, to, textures, lit=None):
    """A box; textures maps face -> (texture, uv); lit is the face that glows."""
    faces = {}
    for f, (tex, uv) in textures.items():
        faces[f] = {"texture": tex, "uv": uv}
        if f == lit:
            faces[f]["neoforge_data"] = {"block_light": 15, "sky_light": 15}
    return {"from": frm, "to": to, "faces": faces}


def all_faces(tex, uv=(0, 0, 16, 16)):
    return {f: (tex, list(uv)) for f in ("north", "south", "east", "west", "up", "down")}


def signal_models():
    for name, red_lit in (("signal_stop", True), ("signal_clear", False)):
        tex = {"particle": "reikotrains:block/signal_metal", "metal": "reikotrains:block/signal_metal",
               "head": "reikotrains:block/signal_head",
               "red": f"reikotrains:block/signal_red_{'on' if red_lit else 'off'}",
               "green": f"reikotrains:block/signal_green_{'off' if red_lit else 'on'}"}
        elements = [
            element([5, 0, 5], [11, 1, 11], all_faces("#metal")),
            element([7, 1, 7], [9, 20, 9], all_faces("#metal")),
            element([5.5, 17, 7], [10.5, 31, 9.5], all_faces("#head")),
            element([6.5, 25, 6.5], [9.5, 28, 7], {"north": ("#red", [0, 0, 16, 16]), "east": ("#head", [0, 0, 1, 3]),
                                                   "west": ("#head", [0, 0, 1, 3]), "up": ("#head", [0, 0, 3, 1]),
                                                   "down": ("#head", [0, 0, 3, 1])}, lit="north" if red_lit else None),
            element([6.5, 19.5, 6.5], [9.5, 22.5, 7], {"north": ("#green", [0, 0, 16, 16]), "east": ("#head", [0, 0, 1, 3]),
                                                       "west": ("#head", [0, 0, 1, 3]), "up": ("#head", [0, 0, 3, 1]),
                                                       "down": ("#head", [0, 0, 3, 1])}, lit=None if red_lit else "north"),
            element([6, 28, 5], [10, 28.5, 7], all_faces("#head", (0, 0, 4, 2))),
            element([6, 22.5, 5], [10, 23, 7], all_faces("#head", (0, 0, 4, 2))),
        ]
        model = {"parent": "block/block", "ambientocclusion": False, "textures": tex, "elements": elements}
        with open(f"{ASSETS}/models/block/{name}.json", "w") as out:
            json.dump(model, out, indent=2)
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for clear in ("true", "false"):
            v = {"model": f"reikotrains:block/signal_{'clear' if clear == 'true' else 'stop'}"}
            if y:
                v["y"] = y
            variants[f"clear={clear},facing={facing}"] = v
    with open(f"{ASSETS}/blockstates/signal.json", "w") as out:
        json.dump({"variants": variants}, out, indent=2)


def station_models():
    tex = {"particle": "reikotrains:block/station_post", "post": "reikotrains:block/station_post", "board": "reikotrains:block/station_board"}
    board_faces = {"north": ("#board", [0, 0, 16, 9]), "south": ("#board", [0, 0, 16, 9]),
                   "east": ("#post", [0, 0, 2, 9]), "west": ("#post", [0, 0, 2, 9]),
                   "up": ("#post", [0, 0, 16, 2]), "down": ("#post", [0, 0, 16, 2])}
    elements = [
        element([1, 0, 7.25], [2.5, 19, 8.75], all_faces("#post", (0, 0, 2, 16))),
        element([13.5, 0, 7.25], [15, 19, 8.75], all_faces("#post", (0, 0, 2, 16))),
        element([0, 19, 7], [16, 28, 9], board_faces),
    ]
    model = {"parent": "block/block", "ambientocclusion": False, "textures": tex, "elements": elements}
    with open(f"{ASSETS}/models/block/station.json", "w") as out:
        json.dump(model, out, indent=2)
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for powered in ("true", "false"):
            v = {"model": "reikotrains:block/station"}
            if y:
                v["y"] = y
            variants[f"facing={facing},powered={powered}"] = v
    with open(f"{ASSETS}/blockstates/station.json", "w") as out:
        json.dump({"variants": variants}, out, indent=2)


def signals_and_stations():
    signal_textures()
    signal_models()
    station_models()


if __name__ == "__main__":
    main()
    signals_and_stations()
