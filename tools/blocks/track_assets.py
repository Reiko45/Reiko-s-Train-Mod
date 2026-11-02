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


def loader_screen():
    """The wagon loader's screen: a row of nine filter slots, room for two buttons, and the player's inventory."""
    width, height = 176, 166
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    bg, light, dark, black = (198, 198, 198, 255), (255, 255, 255, 255), (85, 85, 85, 255), (0, 0, 0, 255)
    for y in range(height):
        for x in range(width):
            img.putpixel((x, y), bg)
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

    def slot(sx, sy, inner=(139, 139, 139, 255)):
        for i in range(18):
            img.putpixel((sx - 1 + i, sy - 1), (55, 55, 55, 255))
            img.putpixel((sx - 1, sy - 1 + i), (55, 55, 55, 255))
            img.putpixel((sx - 1 + i, sy + 16), light)
            img.putpixel((sx + 16, sy - 1 + i), light)
        for i in range(16):
            for j in range(16):
                img.putpixel((sx + i, sy + j), inner)
        img.putpixel((sx - 1, sy + 16), bg)
        img.putpixel((sx + 16, sy - 1), bg)

    # Filter slots: a little bluer than inventory slots, as they only show items
    for c in range(9):
        slot(8 + c * 18, 20, (128, 136, 150, 255))
    for r in range(3):
        for c in range(9):
            slot(8 + c * 18, 84 + r * 18)
    for c in range(9):
        slot(8 + c * 18, 142)
    os.makedirs(f"{ASSETS}/textures/gui", exist_ok=True)
    img.save(f"{ASSETS}/textures/gui/wagon_loader.png")


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


def target(name, color):
    """A switch stand's target: a disc of colour, rimmed, on a black plate."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if r <= 6.2:
                c = shade(color, 1.08 - 0.18 * (r / 6.2) + 0.03 * noise(name, x, y))
            elif r <= 7.3:
                c = shade((232, 228, 214), 1)
            else:
                c = (20, 20, 22, 255)
            img.putpixel((x, y), c)
    img.save(f"{ASSETS}/textures/block/{name}.png")


def marker(name, color, lit):
    """The marker on a switch stand's post: a redstone-red box with a window showing what redstone sets it to."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            edge = x < 3 or x > 12 or y < 3 or y > 12
            if edge:
                c = shade((210, 34, 26) if lit else (140, 26, 20), 1 + 0.06 * noise(name, x, y))
            else:
                c = shade(color, (1.2 if lit else 0.85) - 0.1 * (abs(x - 7.5) + abs(y - 7.5)) / 5)
            img.putpixel((x, y), c)
    img.save(f"{ASSETS}/textures/block/{name}.png")


def switch_textures():
    target("switch_target_green", (40, 170, 72))
    target("switch_target_yellow", (236, 186, 30))
    solid("switch_lever", (168, 36, 30))
    for way, color in (("straight", (40, 190, 80)), ("throws", (240, 190, 30))):
        marker(f"switch_marker_{way}", color, False)
        marker(f"switch_marker_{way}_on", color, True)


def switch_models():
    """
    The switch stand: a cast base, a spindle, a target on top and a hand lever. Set normally the target's green face
    is square to the stand's front (it faces the junction); thrown, the spindle turns it a quarter round, to show the
    yellow face, and the lever is over the other way. A marker box on the post shows what redstone sets it to (green,
    it holds it straight on; yellow, it throws it), and lights up while redstone is holding it.
    """
    for thrown in (False, True):
        for way in ("throws", "straight"):
            for lit in (False, True):
                name = f"switch_stand_{'thrown' if thrown else 'normal'}_{way}{'_lit' if lit else ''}"
                plate = "#yellow" if thrown else "#green"
                tex = {"particle": "reikotrains:block/signal_metal", "metal": "reikotrains:block/signal_metal",
                       "head": "reikotrains:block/signal_head", "lever": "reikotrains:block/switch_lever",
                       "green": "reikotrains:block/switch_target_green", "yellow": "reikotrains:block/switch_target_yellow",
                       "marker": f"reikotrains:block/switch_marker_{way}{'_on' if lit else ''}"}
                if thrown:
                    target_box = element([7.5, 12, 4.5], [8.5, 19, 11.5], {"east": (plate, [0, 0, 16, 16]), "west": (plate, [0, 0, 16, 16]),
                                                                            "north": ("#head", [0, 0, 1, 7]), "south": ("#head", [0, 0, 1, 7]),
                                                                            "up": ("#head", [0, 0, 1, 7]), "down": ("#head", [0, 0, 1, 7])})
                else:
                    target_box = element([4.5, 12, 7.5], [11.5, 19, 8.5], {"north": (plate, [0, 0, 16, 16]), "south": (plate, [0, 0, 16, 16]),
                                                                            "east": ("#head", [0, 0, 1, 7]), "west": ("#head", [0, 0, 1, 7]),
                                                                            "up": ("#head", [0, 0, 7, 1]), "down": ("#head", [0, 0, 7, 1])})
                lever = element([10.5, 2.5, 7.5], [11.5, 10, 8.5], all_faces("#lever", (0, 0, 1, 8)))
                lever["rotation"] = {"origin": [11, 3, 8], "axis": "x", "angle": 45 if thrown else -45}
                grip = element([10.5, 2, 6.5], [12, 4, 9.5], all_faces("#head", (0, 0, 3, 2)))
                # The marker: a box round the post, its window on all four sides; lit, it glows.
                box = {"from": [5.5, 4.5, 5.5], "to": [10.5, 9.5, 10.5], "faces": {}}
                for f in ("north", "south", "east", "west"):
                    box["faces"][f] = {"texture": "#marker", "uv": [0, 0, 16, 16]}
                    if lit:
                        box["faces"][f]["neoforge_data"] = {"block_light": 15, "sky_light": 15}
                for f in ("up", "down"):
                    box["faces"][f] = {"texture": "#head", "uv": [0, 0, 5, 5]}
                elements = [
                    element([4, 0, 4], [12, 2, 12], all_faces("#metal", (0, 0, 8, 8))),
                    element([5, 2, 5], [11, 3, 11], all_faces("#head", (0, 0, 6, 6))),
                    element([7, 3, 7], [9, 12, 9], all_faces("#metal", (0, 0, 2, 9))),
                    box,
                    target_box,
                    grip,
                    lever,
                ]
                model = {"parent": "block/block", "ambientocclusion": False, "textures": tex, "elements": elements}
                with open(f"{ASSETS}/models/block/{name}.json", "w") as out:
                    json.dump(model, out, indent=2)
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for thrown in ("false", "true"):
            for powered in ("false", "true"):
                for straight in ("false", "true"):
                    model = (f"reikotrains:block/switch_stand_{'thrown' if thrown == 'true' else 'normal'}"
                             f"_{'straight' if straight == 'true' else 'throws'}{'_lit' if powered == 'true' else ''}")
                    v = {"model": model}
                    if y:
                        v["y"] = y
                    variants[f"facing={facing},powered={powered},redstone_straight={straight},thrown={thrown}"] = v
    with open(f"{ASSETS}/blockstates/switch_stand.json", "w") as out:
        json.dump({"variants": variants}, out, indent=2)
    for old in ("switch_stand_normal", "switch_stand_thrown"):
        path = f"{ASSETS}/models/block/{old}.json"
        if os.path.exists(path):
            os.remove(path)


def loader_textures():
    """The wagon loader: a riveted steel box, its chute, and arrows on top for which way the goods go."""
    steel, frame = (70, 92, 110), (40, 52, 64)

    def panel(name, draw=None):
        img = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(16):
                c = shade(steel, 1 + 0.05 * noise(name, x, y) - (0.08 if y in (5, 10) else 0))
                if x in (0, 15) or y in (0, 15):
                    c = shade(frame, 1)
                elif (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
                    c = shade((150, 165, 178), 1)
                img.putpixel((x, y), c)
        if draw:
            draw(img)
        img.save(f"{ASSETS}/textures/block/{name}.png")

    def arrow(color, up):
        def draw(img):
            for row in range(5):
                y = (3 + row) if up else (12 - row)
                for x in range(8 - row, 8 + row):
                    img.putpixel((x, y), shade(color, 1))
            for y in (range(8, 13) if up else range(3, 8)):
                for x in (7, 8):
                    img.putpixel((x, y), shade(color, 1))
        return draw

    def hatch(img):
        for y in range(3, 11):
            for x in range(3, 13):
                edge = x in (3, 12) or y in (3, 10)
                img.putpixel((x, y), shade(frame, 0.8) if edge else (24, 26, 30, 255))
    panel("wagon_loader_side")
    panel("wagon_loader_top_load", arrow((70, 200, 90), True))
    panel("wagon_loader_top_unload", arrow((235, 140, 40), False))
    panel("wagon_loader_front", hatch)
    solid("wagon_loader_chute", (24, 26, 30), edge=(40, 52, 64))
    lamp("wagon_loader_lamp_on", (255, 190, 60), True)
    lamp("wagon_loader_lamp_off", (255, 190, 60), False)


def loader_models():
    for mode in ("load", "unload"):
        for active in (False, True):
            name = f"wagon_loader_{mode}{'_active' if active else ''}"
            tex = {"particle": "reikotrains:block/wagon_loader_side", "side": "reikotrains:block/wagon_loader_side",
                   "top": f"reikotrains:block/wagon_loader_top_{mode}", "front": "reikotrains:block/wagon_loader_front",
                   "chute": "reikotrains:block/wagon_loader_chute",
                   "lamp": f"reikotrains:block/wagon_loader_lamp_{'on' if active else 'off'}"}
            spout = element([4.5, 8, -5], [11.5, 12, 1], {"north": ("#chute", [0, 0, 7, 4]), "east": ("#side", [0, 4, 6, 8]),
                                                          "west": ("#side", [0, 4, 6, 8]), "up": ("#side", [4, 0, 11, 6]),
                                                          "down": ("#side", [4, 0, 11, 6])})
            spout["rotation"] = {"origin": [8, 10, 0], "axis": "x", "angle": -22.5}
            elements = [
                element([0, 0, 0], [16, 16, 16], {"north": ("#front", [0, 0, 16, 16]), "south": ("#side", [0, 0, 16, 16]),
                                                  "east": ("#side", [0, 0, 16, 16]), "west": ("#side", [0, 0, 16, 16]),
                                                  "up": ("#top", [0, 0, 16, 16]), "down": ("#side", [0, 0, 16, 16])}),
                spout,
                element([6.5, 12.5, -0.5], [9.5, 14.5, 0], {"north": ("#lamp", [0, 0, 16, 16]), "east": ("#side", [0, 0, 1, 2]),
                                                           "west": ("#side", [0, 0, 1, 2]), "up": ("#side", [0, 0, 3, 1]),
                                                           "down": ("#side", [0, 0, 3, 1])}, lit="north" if active else None),
            ]
            model = {"parent": "block/block", "textures": tex, "elements": elements}
            with open(f"{ASSETS}/models/block/{name}.json", "w") as out:
                json.dump(model, out, indent=2)
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for mode in ("load", "unload"):
            for active in ("false", "true"):
                v = {"model": f"reikotrains:block/wagon_loader_{mode}{'_active' if active == 'true' else ''}"}
                if y:
                    v["y"] = y
                variants[f"active={active},facing={facing},mode={mode}"] = v
    with open(f"{ASSETS}/blockstates/wagon_loader.json", "w") as out:
        json.dump({"variants": variants}, out, indent=2)


def crossing_textures():
    solid("crossing_arm_white", (236, 234, 226), noise_amount=0.03)
    solid("crossing_arm_red", (196, 34, 28), noise_amount=0.04)
    # The lamp board's face, 14 x 7 in the top left: black, with a white rim.
    board = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = shade((22, 22, 24), 1 + 0.05 * noise("crossing_board", x, y))
            if x < 14 and y < 7 and (x in (0, 13) or y in (0, 6)):
                c = shade((236, 234, 226), 1)
            board.putpixel((x, y), c)
    board.save(f"{ASSETS}/textures/block/crossing_board.png")


def crossing_models():
    """
    The level crossing's post (it faces north: the arm comes down towards -z): a base, a pole, the arm's mechanism box
    on its right, and on top a board with two red lamps on each face, hooded. The arm and the lit lamps are drawn by
    its renderer, as they move and flash.
    """
    tex = {"particle": "reikotrains:block/signal_metal", "metal": "reikotrains:block/signal_metal",
           "head": "reikotrains:block/signal_head", "board": "reikotrains:block/crossing_board",
           "lamp": "reikotrains:block/signal_red_off"}
    board_faces = {"east": ("#board", [0, 0, 14, 7]), "west": ("#board", [0, 0, 14, 7]),
                   "north": ("#head", [0, 0, 2, 7]), "south": ("#head", [0, 0, 2, 7]),
                   "up": ("#head", [0, 0, 2, 14]), "down": ("#head", [0, 0, 2, 14])}
    elements = [
        element([5, 0, 5], [11, 2, 11], all_faces("#metal", (0, 0, 6, 6))),
        element([7, 2, 7], [9, 21, 9], all_faces("#metal", (0, 0, 2, 16))),
        element([9, 10, 5.5], [12, 19, 10.5], all_faces("#head", (0, 0, 5, 9))),
        element([7, 21, 1], [9, 28, 15], board_faces),
    ]
    for z in (4, 12):
        for x0, x1, face, hood in ((9, 9.25, "east", (9, 11)), (6.75, 7, "west", (5, 7))):
            elements.append(element([x0, 22, z - 2.5], [x1, 27, z + 2.5], {face: ("#lamp", [0, 0, 16, 16])}))
            elements.append(element([hood[0], 27, z - 3], [hood[1], 27.5, z + 3], all_faces("#head", (0, 0, 2, 6))))
    model = {"parent": "block/block", "ambientocclusion": False, "textures": tex, "elements": elements}
    with open(f"{ASSETS}/models/block/level_crossing.json", "w") as out:
        json.dump(model, out, indent=2)
    multipart = []
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        apply = {"model": "reikotrains:block/level_crossing"}
        if y:
            apply["y"] = y
        multipart.append({"when": {"facing": facing}, "apply": apply})
    with open(f"{ASSETS}/blockstates/level_crossing.json", "w") as out:
        json.dump({"multipart": multipart}, out, indent=2)
    # The barrier under the lowered arm can't be seen: a model with nothing in it.
    with open(f"{ASSETS}/models/block/level_crossing_arm.json", "w") as out:
        json.dump({"textures": {"particle": "reikotrains:block/crossing_arm_white"}}, out, indent=2)
    with open(f"{ASSETS}/blockstates/level_crossing_arm.json", "w") as out:
        json.dump({"variants": {"axis=x": {"model": "reikotrains:block/level_crossing_arm"},
                                "axis=z": {"model": "reikotrains:block/level_crossing_arm"}}}, out, indent=2)


def charger_textures():
    teal = (34, 104, 112)
    solid("charger_body", teal, shade(teal, 0.7))
    solid("charge_bar_on", (110, 240, 120), noise_amount=0.03)
    solid("charge_bar_off", (26, 62, 34), noise_amount=0.04)
    solid("charger_plug", (236, 150, 30), noise_amount=0.04)
    lamp("charger_lamp_on", (90, 180, 255), True)
    lamp("charger_lamp_off", (90, 180, 255), False)
    # The display: a dark window in a lighter bezel, for the bars to sit in.
    display = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = shade((20, 22, 26), 1 + 0.05 * noise("charger_display", x, y))
            if x in (3, 12) or y in (0, 10):
                c = shade((150, 156, 160), 1)
            elif x < 3 or x > 12:
                c = shade(teal, 1 + 0.05 * noise("charger_display", x, y))
            display.putpixel((x, y), c)
    display.save(f"{ASSETS}/textures/block/charger_display.png")
    # The warning sign low on its back: a yellow triangle, rimmed in black, with a black lightning flash, on the
    # cabinet's teal (the face shows the 10 x 10 from (3, 5)).
    art = ["....KK....",
           "...KYYK...",
           "...KYKK...",
           "..KYYKYK..",
           "..KYKKYK..",
           ".KYYYKYYK.",
           ".KYYKYYYK.",
           "KYYYKYYYYK",
           "KYYYYYYYYK",
           "KKKKKKKKKK"]
    sign = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = shade(teal, 1 + 0.05 * noise("charger_sign", x, y))
            if 3 <= x <= 12 and 5 <= y <= 14:
                ch = art[y - 5][x - 3]
                c = (24, 24, 26) if ch == "K" else (236, 196, 40) if ch == "Y" else c
            sign.putpixel((x, y), c)
    sign.save(f"{ASSETS}/textures/block/charger_sign.png")


def charger_models():
    """
    The charging point (it faces north: its cable reaches out towards the track at -z): a cabinet on a plinth, with a
    lamp on top that lights while it's charging and a cable with an orange plug lying out towards the track. On its
    back, facing whoever's on the platform side, a warning sign low down and a display above with four bars showing how
    full its store is.
    """
    for bars in range(5):
        for charging in (False, True):
            name = f"charging_point_{bars}{'_charging' if charging else ''}"
            tex = {"particle": "reikotrains:block/charger_body", "body": "reikotrains:block/charger_body",
                   "sign": "reikotrains:block/charger_sign", "display": "reikotrains:block/charger_display",
                   "head": "reikotrains:block/signal_head", "metal": "reikotrains:block/signal_metal",
                   "on": "reikotrains:block/charge_bar_on", "off": "reikotrains:block/charge_bar_off",
                   "plug": "reikotrains:block/charger_plug",
                   "lamp": f"reikotrains:block/charger_lamp_{'on' if charging else 'off'}"}
            lower = all_faces("#body", (3, 5, 13, 15))
            lower["south"] = ("#sign", [3, 5, 13, 15])
            upper = all_faces("#body", (3, 0, 13, 11))
            upper["south"] = ("#display", [2, 0, 14, 11])
            elements = [
                element([2, 0, 3], [14, 1, 13], all_faces("#metal", (0, 0, 12, 10))),
                element([3, 1, 4], [13, 11, 12], lower),
                element([3, 11, 4], [13, 22, 12], upper),
                element([2.5, 22, 3.5], [13.5, 23, 12.5], all_faces("#head", (0, 0, 11, 9))),
                element([7, 23, 7], [9, 25, 9], all_faces("#lamp", (4, 4, 12, 12)), lit="north" if charging else None),
                element([7.25, 2.5, 0.5], [8.75, 4, 4], all_faces("#head", (0, 0, 2, 4))),
                element([6.5, 1.75, 0], [9.5, 4.75, 1], all_faces("#plug", (0, 0, 3, 3))),
            ]
            if charging:
                for f in ("south", "east", "west", "up"):
                    elements[4]["faces"][f]["neoforge_data"] = {"block_light": 15, "sky_light": 15}
            for i in range(4):
                lit = i < bars
                y0 = 12.5 + 2.25 * i
                faces = {"south": ("#on" if lit else "#off", [0, 0, 6, 1.5]), "up": ("#off", [0, 0, 6, 0.25]),
                         "down": ("#off", [0, 0, 6, 0.25]), "east": ("#off", [0, 0, 0.25, 1.5]), "west": ("#off", [0, 0, 0.25, 1.5])}
                elements.append(element([5, y0, 12], [11, y0 + 1.5, 12.25], faces, lit="south" if lit else None))
            model = {"parent": "block/block", "ambientocclusion": False, "textures": tex, "elements": elements}
            with open(f"{ASSETS}/models/block/{name}.json", "w") as out:
                json.dump(model, out, indent=2)
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for bars in range(5):
            for charging in ("false", "true"):
                v = {"model": f"reikotrains:block/charging_point_{bars}{'_charging' if charging == 'true' else ''}"}
                if y:
                    v["y"] = y
                variants[f"charging={charging},facing={facing},level={bars}"] = v
    with open(f"{ASSETS}/blockstates/charging_point.json", "w") as out:
        json.dump({"variants": variants}, out, indent=2)


def signals_and_stations():
    signal_textures()
    signal_models()
    station_models()
    switch_textures()
    switch_models()
    loader_textures()
    loader_models()
    crossing_textures()
    crossing_models()
    charger_textures()
    charger_models()


if __name__ == "__main__":
    main()
    signals_and_stations()
    loader_screen()
