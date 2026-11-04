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
    lamp("signal_yellow_on", (255, 196, 40), True)
    lamp("signal_yellow_off", (255, 196, 40), False)
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


def three_light_signal_models():
    """
    The three-light signal: the same post as the two-light one, with a taller head carrying red over yellow over
    green, each with a hood; one model for each aspect, with its lamp lit.
    """
    for name, lit in (("three_light_signal_stop", "red"), ("three_light_signal_caution", "yellow"), ("three_light_signal_clear", "green")):
        tex = {"particle": "reikotrains:block/signal_metal", "metal": "reikotrains:block/signal_metal",
               "head": "reikotrains:block/signal_head"}
        for colour in ("red", "yellow", "green"):
            tex[colour] = f"reikotrains:block/signal_{colour}_{'on' if colour == lit else 'off'}"
        elements = [
            element([5, 0, 5], [11, 1, 11], all_faces("#metal")),
            element([7, 1, 7], [9, 16, 9], all_faces("#metal")),
            element([5.5, 14, 7], [10.5, 31.5, 9.5], all_faces("#head")),
        ]
        for colour, y0 in (("red", 26.5), ("yellow", 21.5), ("green", 16.5)):
            elements.append(element([6.5, y0, 6.5], [9.5, y0 + 3, 7], {
                "north": ("#" + colour, [0, 0, 16, 16]), "east": ("#head", [0, 0, 1, 3]), "west": ("#head", [0, 0, 1, 3]),
                "up": ("#head", [0, 0, 3, 1]), "down": ("#head", [0, 0, 3, 1])}, lit="north" if colour == lit else None))
            elements.append(element([6, y0 + 3, 5], [10, y0 + 3.5, 7], all_faces("#head", (0, 0, 4, 2))))
        model = {"parent": "block/block", "ambientocclusion": False, "textures": tex, "elements": elements}
        with open(f"{ASSETS}/models/block/{name}.json", "w") as out:
            json.dump(model, out, indent=2)
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for clear in ("true", "false"):
            for caution in ("true", "false"):
                aspect = "stop" if clear == "false" else "caution" if caution == "true" else "clear"
                v = {"model": f"reikotrains:block/three_light_signal_{aspect}"}
                if y:
                    v["y"] = y
                variants[f"caution={caution},clear={clear},facing={facing}"] = v
    with open(f"{ASSETS}/blockstates/three_light_signal.json", "w") as out:
        json.dump({"variants": variants}, out, indent=2)
    with open(f"{ASSETS}/models/item/three_light_signal.json", "w") as out:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "reikotrains:item/three_light_signal"}}, out, indent=2)


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


def sensor_textures():
    yellow = (232, 190, 40)
    solid("sensor_body", yellow, (30, 30, 32), 0.04)
    lamp("sensor_eye_on", (255, 60, 40), True)
    lamp("sensor_eye_off", (255, 60, 40), False)
    # The back, where its filter item shows: a dark panel in a frame (the face shows the 9 x 7 from (3.5, 4)).
    back = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = shade((40, 42, 46), 1 + 0.05 * noise("sensor_back", x, y))
            if x in (3, 12) or y in (4, 10):
                c = shade(yellow, 1)
            back.putpixel((x, y), c)
    back.save(f"{ASSETS}/textures/block/sensor_back.png")
    # The top, with an arrow (or two) showing which way trains must be going to be sensed, seen from behind it: the
    # face shows the 8 x 8 from (4, 4), north (the track) at the top.
    arrows = {
        "right": ["........",
                  ".....K..",
                  ".....KK.",
                  "KKKKKKKK",
                  "KKKKKKKK",
                  ".....KK.",
                  ".....K..",
                  "........"],
        "both": ["........",
                 ".K....K.",
                 "KK....KK",
                 "KKKKKKKK",
                 "KKKKKKKK",
                 "KK....KK",
                 ".K....K.",
                 "........"],
    }
    arrows["left"] = [row[::-1] for row in arrows["right"]]
    for name, art in arrows.items():
        top = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(16):
                c = shade(yellow, 1 + 0.04 * noise("sensor_top", x, y))
                if 4 <= x < 12 and 4 <= y < 12 and art[y - 4][x - 4] == "K":
                    c = (24, 24, 26)
                top.putpixel((x, y), c)
        top.save(f"{ASSETS}/textures/block/sensor_top_{name}.png")


def sensor_models():
    """
    The track sensor (it faces north, its eye looking at the track at -z): a yellow head on a short post, a red eye
    on the front that lights while it senses a train, an arrow on top for which way trains must be going, and a frame
    on the back where its filter item shows.
    """
    for direction in ("both", "left", "right"):
        for powered in (False, True):
            name = f"track_sensor_{direction}{'_on' if powered else ''}"
            tex = {"particle": "reikotrains:block/sensor_body", "body": "reikotrains:block/sensor_body",
                   "metal": "reikotrains:block/signal_metal", "head": "reikotrains:block/signal_head",
                   "top": f"reikotrains:block/sensor_top_{direction}", "back": "reikotrains:block/sensor_back",
                   "eye": f"reikotrains:block/sensor_eye_{'on' if powered else 'off'}"}
            head = all_faces("#body", (3.5, 4, 12.5, 11))
            head["up"] = ("#top", [4, 4, 12, 12])
            head["north"] = ("#head", [3.5, 4, 12.5, 11])
            head["south"] = ("#back", [3.5, 4, 12.5, 11])
            elements = [
                element([3, 0, 3], [13, 1, 13], all_faces("#metal", (0, 0, 10, 10))),
                element([7, 1, 7], [9, 5, 9], all_faces("#metal", (0, 0, 2, 4))),
                element([3.5, 5, 4], [12.5, 12, 12], head),
                element([6, 6.5, 3.5], [10, 10.5, 4], {"north": ("#eye", [0, 0, 16, 16]), "east": ("#head", [0, 0, 0.5, 4]),
                                                       "west": ("#head", [0, 0, 0.5, 4]), "up": ("#head", [0, 0, 4, 0.5]),
                                                       "down": ("#head", [0, 0, 4, 0.5])}, lit="north" if powered else None),
            ]
            model = {"parent": "block/block", "ambientocclusion": False, "textures": tex, "elements": elements}
            with open(f"{ASSETS}/models/block/{name}.json", "w") as out:
                json.dump(model, out, indent=2)
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for direction in ("both", "left", "right"):
            for powered in ("false", "true"):
                v = {"model": f"reikotrains:block/track_sensor_{direction}{'_on' if powered == 'true' else ''}"}
                if y:
                    v["y"] = y
                variants[f"direction={direction},facing={facing},powered={powered}"] = v
    with open(f"{ASSETS}/blockstates/track_sensor.json", "w") as out:
        json.dump({"variants": variants}, out, indent=2)


# The speed sign's numbers, 6 x 11, drawn bold for the 32 x 32 sign face.
SIGN_DIGITS = {
    "0": [".####.", "##..##", "##..##", "##..##", "##..##", "##..##", "##..##", "##..##", "##..##", "##..##", ".####."],
    "1": ["..##..", ".###..", "####..", "..##..", "..##..", "..##..", "..##..", "..##..", "..##..", "..##..", "######"],
    "2": [".####.", "##..##", "....##", "....##", "...##.", "..##..", ".##...", "##....", "##....", "##....", "######"],
    "3": [".####.", "##..##", "....##", "....##", "..###.", "....##", "....##", "....##", "....##", "##..##", ".####."],
    "4": ["...##.", "..###.", ".####.", "##.##.", "##.##.", "##.##.", "######", "...##.", "...##.", "...##.", "...##."],
    "5": ["######", "##....", "##....", "#####.", "....##", "....##", "....##", "....##", "....##", "##..##", ".####."],
    "6": [".####.", "##..##", "##....", "##....", "#####.", "##..##", "##..##", "##..##", "##..##", "##..##", ".####."],
    "7": ["######", "....##", "....##", "...##.", "...##.", "..##..", "..##..", "..##..", ".##...", ".##...", ".##..."],
    "8": [".####.", "##..##", "##..##", "##..##", ".####.", "##..##", "##..##", "##..##", "##..##", "##..##", ".####."],
    "9": [".####.", "##..##", "##..##", "##..##", "##..##", ".#####", "....##", "....##", "....##", "##..##", ".####."],
}
SPEED_LIMITS = (10, 20, 30, 40, 50, 60, 70, 80, 90)
# The sign's LIMIT property, in order (as SpeedSignBlock.LIMITS): the end of a limit came before 60-90 did.
SIGN_STATES = (10, 20, 30, 40, 50, "end", 60, 70, 80, 90)


def speed_sign_textures():
    """
    The speed sign's faces, 32 x 32 for crisp numbers: a white disc in a red ring with the limit in black, the end of a
    limit as a white disc crossed by thin black lines, and the grey back. Round, so clear in the corners (the model is
    cut out).
    """
    def disc(name, paint):
        img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        for y in range(32):
            for x in range(32):
                r = math.hypot(x - 15.5, y - 15.5)
                if r <= 15.6:
                    img.putpixel((x, y), paint(x, y, r))
        img.save(f"{ASSETS}/textures/block/{name}.png")

    def limit(kmh):
        text = str(kmh)
        lit = set()
        x0 = 16 - (len(text) * 8 - 2) // 2
        for i, ch in enumerate(text):
            for row, line in enumerate(SIGN_DIGITS[ch]):
                for col, c in enumerate(line):
                    if c == "#":
                        lit.add((x0 + i * 8 + col, 10 + row))

        def paint(x, y, r):
            if r > 15:
                return shade((150, 24, 20), 1)
            if r > 11.2:
                return shade((214, 36, 30), 1 + 0.04 * noise("sign_ring", x, y))
            if (x, y) in lit:
                return shade((20, 20, 22), 1)
            return shade((244, 242, 236), 1 + 0.02 * noise("sign_face", x, y))
        return paint

    for kmh in SPEED_LIMITS:
        disc(f"speed_sign_{kmh}", limit(kmh))

    def end(x, y, r):
        if r > 14.6:
            return shade((90, 90, 96), 1)
        if any(abs(x + y - 31 - k * 6) <= 1 for k in (-2, -1, 0, 1, 2)) and r < 13.5:
            return shade((24, 24, 26), 1)
        return shade((244, 242, 236), 1 + 0.02 * noise("sign_face", x, y))
    disc("speed_sign_end", end)

    def back(x, y, r):
        if r > 14.6:
            return shade((52, 54, 60), 1)
        return shade((104, 106, 112), 1 + 0.05 * noise("sign_back", x, y))
    disc("speed_sign_back", back)


def speed_sign_models():
    """
    The speed sign (it faces north, its face towards trains coming from -z): a base, a post, and the round sign on top,
    a thin plate cut out of its texture.
    """
    variants = {}
    for index, kmh in enumerate(SIGN_STATES):
        name = f"speed_sign_{kmh}"
        tex = {"particle": "reikotrains:block/signal_metal", "metal": "reikotrains:block/signal_metal",
               "face": f"reikotrains:block/{name}", "back": "reikotrains:block/speed_sign_back"}
        elements = [
            element([5, 0, 5], [11, 1, 11], all_faces("#metal", (0, 0, 6, 6))),
            element([7, 1, 7], [9, 22, 9], all_faces("#metal", (0, 0, 2, 16))),
            element([2, 14, 6.5], [14, 26, 7], {"north": ("#face", [0, 0, 16, 16]), "south": ("#back", [16, 0, 0, 16])}),
        ]
        model = {"parent": "block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
                 "textures": tex, "elements": elements}
        with open(f"{ASSETS}/models/block/{name}.json", "w") as out:
            json.dump(model, out, indent=2)
        for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            v = {"model": f"reikotrains:block/{name}"}
            if y:
                v["y"] = y
            variants[f"facing={facing},limit={index}"] = v
    with open(f"{ASSETS}/blockstates/speed_sign.json", "w") as out:
        json.dump({"variants": variants}, out, indent=2)


def warning_sign(name, background, key):
    """A yellow warning triangle with a black lightning flash, on {background} (the 10 x 10 from (3, 5))."""
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
            c = shade(background, 1 + 0.05 * noise(key, x, y))
            if 3 <= x <= 12 and 5 <= y <= 14:
                ch = art[y - 5][x - 3]
                c = (24, 24, 26) if ch == "K" else (236, 196, 40) if ch == "Y" else c
            sign.putpixel((x, y), c)
    sign.save(f"{ASSETS}/textures/block/{name}.png")


def catenary_textures():
    # Galvanised steel for the masts and their arms; a weathered copper for the wires; brown glazed porcelain for the
    # insulators (lighter rings round them); concrete for the plinths.
    solid("mast_steel", (150, 154, 158), noise_amount=0.06)
    solid("mast_concrete", (168, 166, 158), shade((168, 166, 158), 0.85), 0.07)
    solid("catenary_wire", (112, 74, 48), noise_amount=0.08)
    ins = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            ins.putpixel((x, y), shade((128, 62, 36), (1.18 if x % 4 == 0 else 1) + 0.05 * noise("catenary_insulator", x, y)))
    ins.save(f"{ASSETS}/textures/block/catenary_insulator.png")
    # The mast's number plate: black on yellow.
    plate = Image.new("RGBA", (16, 16))
    digits = ["KKK.K.", "K.K.KK", "K.K..K", "K.K..K", "KKK.KKK"]
    for y in range(16):
        for x in range(16):
            c = shade((232, 196, 48), 1 + 0.04 * noise("mast_plate", x, y))
            if x in (0, 15) or y in (0, 15):
                c = shade((24, 24, 26), 1)
            elif 4 <= y <= 8 and 4 <= x <= 10 and digits[y - 4][x - 4:x - 3] == "K":
                c = shade((24, 24, 26), 1)
            plate.putpixel((x, y), c)
    plate.save(f"{ASSETS}/textures/block/mast_plate.png")
    # The substation: a green-grey transformer tank, darker lid and fins, a display for its bars, and a warning sign.
    green = (82, 110, 88)
    solid("substation_body", green, shade(green, 0.75))
    solid("substation_dark", shade(green, 0.7)[:3], noise_amount=0.05)
    display = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = shade((20, 22, 26), 1 + 0.05 * noise("substation_display", x, y))
            if x in (0, 15) or y in (0, 15):
                c = shade((150, 156, 160), 1)
            display.putpixel((x, y), c)
    display.save(f"{ASSETS}/textures/block/substation_display.png")
    warning_sign("substation_sign", green, "substation_sign")


def catenary_models():
    """
    The mast: a concrete plinth, a steel flange and the foot of the pole, two blocks of it (the rest of it, and its arm,
    are drawn by its renderer, reaching up and over the track to wherever the wire is), with a number plate on the side
    facing the track (north, as modelled).
    """
    tex = {"particle": "reikotrains:block/mast_steel", "steel": "reikotrains:block/mast_steel",
           "concrete": "reikotrains:block/mast_concrete", "plate": "reikotrains:block/mast_plate"}
    elements = [
        element([3, 0, 3], [13, 3, 13], all_faces("#concrete", (3, 3, 13, 13))),
        element([4.5, 3, 4.5], [11.5, 4, 11.5], all_faces("#steel", (0, 0, 7, 7))),
        element([6, 4, 6], [10, 32, 10], {**all_faces("#steel", (6, 0, 10, 16)), "up": ("#steel", [6, 6, 10, 10]),
                                           "down": ("#steel", [6, 6, 10, 10])}),
        element([6.5, 16, 5.5], [9.5, 20, 6], {**all_faces("#plate", (0, 0, 16, 16)), "north": ("#plate", [0, 0, 16, 16])}),
    ]
    with open(f"{ASSETS}/models/block/catenary_mast.json", "w") as out:
        json.dump({"parent": "block/block", "ambientocclusion": False, "textures": tex, "elements": elements}, out, indent=2)
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v = {"model": "reikotrains:block/catenary_mast"}
        if y:
            v["y"] = y
        variants[f"facing={facing}"] = v
    with open(f"{ASSETS}/blockstates/catenary_mast.json", "w") as out:
        json.dump({"variants": variants}, out, indent=2)

    """
    The substation (it faces north, towards whoever placed it): a transformer tank on a concrete pad, cooling fins down
    each side, three porcelain bushings on top with copper terminals, a display on its front with four bars showing how
    full the line's store is, a warning sign on its back, and a lamp on the lid that lights while the line is live.
    """
    for bars in range(5):
        for live in (False, True):
            name = f"substation_{bars}{'_live' if live else ''}"
            tex = {"particle": "reikotrains:block/substation_body", "body": "reikotrains:block/substation_body",
                   "dark": "reikotrains:block/substation_dark", "pad": "reikotrains:block/mast_concrete",
                   "sign": "reikotrains:block/substation_sign", "display": "reikotrains:block/substation_display",
                   "ins": "reikotrains:block/catenary_insulator", "copper": "reikotrains:block/catenary_wire",
                   "head": "reikotrains:block/signal_head", "on": "reikotrains:block/charge_bar_on",
                   "off": "reikotrains:block/charge_bar_off",
                   "lamp": f"reikotrains:block/charger_lamp_{'on' if live else 'off'}"}
            tank = all_faces("#body", (3, 3, 13, 14))
            tank["south"] = ("#sign", [3, 4, 13, 15])
            tank["up"] = ("#body", [3, 3, 13, 13])
            elements = [
                element([0, 0, 0], [16, 2, 16], all_faces("#pad", (0, 0, 16, 16))),
                element([3, 2, 3], [13, 13, 13], tank),
                element([2.5, 13, 2.5], [13.5, 14, 13.5], all_faces("#dark", (2, 2, 13, 13))),
                element([4, 3.5, 2.75], [12, 12.5, 3], all_faces("#display", (0, 0, 16, 16))),
                element([7, 14, 4], [9, 16, 6], all_faces("#lamp", (4, 4, 12, 12)), lit="north" if live else None),
            ]
            if live:
                for f in ("south", "east", "west", "up"):
                    elements[-1]["faces"][f]["neoforge_data"] = {"block_light": 15, "sky_light": 15}
            for z in (4, 6, 8, 10, 12):
                elements.append(element([13, 3, z - 0.5], [15, 12, z + 0.5], all_faces("#dark", (0, 0, 2, 9))))
                elements.append(element([1, 3, z - 0.5], [3, 12, z + 0.5], all_faces("#dark", (0, 0, 2, 9))))
            for x in (5, 8, 11):
                elements.append(element([x - 1, 14, 9], [x + 1, 20, 11], all_faces("#ins", (0, 0, 2, 6))))
                for y in (15, 17):
                    elements.append(element([x - 1.5, y, 8.5], [x + 1.5, y + 1, 11.5], all_faces("#ins", (0, 0, 3, 3))))
                elements.append(element([x - 0.5, 20, 9.5], [x + 0.5, 22, 10.5], all_faces("#copper", (0, 0, 1, 2))))
            for i in range(4):
                lit = i < bars
                y0 = 4.5 + 2 * i
                faces = {"north": ("#on" if lit else "#off", [0, 0, 6, 1.25]), "up": ("#off", [0, 0, 6, 0.25]),
                         "down": ("#off", [0, 0, 6, 0.25]), "east": ("#off", [0, 0, 0.25, 1.25]), "west": ("#off", [0, 0, 0.25, 1.25])}
                elements.append(element([5, y0, 2.5], [11, y0 + 1.25, 2.75], faces, lit="north" if lit else None))
            model = {"parent": "block/block", "ambientocclusion": False, "textures": tex, "elements": elements}
            with open(f"{ASSETS}/models/block/{name}.json", "w") as out:
                json.dump(model, out, indent=2)
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for bars in range(5):
            for live in ("false", "true"):
                v = {"model": f"reikotrains:block/substation_{bars}{'_live' if live == 'true' else ''}"}
                if y:
                    v["y"] = y
                variants[f"facing={facing},level={bars},live={live}"] = v
    with open(f"{ASSETS}/blockstates/substation.json", "w") as out:
        json.dump({"variants": variants}, out, indent=2)


ASPHALT = (59, 59, 62)


def street_textures():
    """
    Street track's road surface: asphalt like Reiko's Car Mod's, and (for the straight block's top) two grooved rails
    set into it: a bright steel head two pixels wide, the dark groove for the wheel flanges on its inside.
    """
    def asphalt(key, x, y):
        return shade(ASPHALT, 1 + 0.22 * noise(key, x, y))

    plain_road = Image.new("RGBA", (16, 16))
    railed = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = asphalt("street_road", x, y)
            plain_road.putpixel((x, y), c)
            if x in (0, 1, 14, 15):
                c = shade((168, 170, 174), (1.12 if x in (1, 14) else 1.0) + 0.04 * noise("street_rail", x, y))
            elif x in (2, 13):
                c = shade((26, 26, 28), 1 + 0.05 * noise("street_groove", x, y))
            railed.putpixel((x, y), c)
    plain_road.save(f"{ASSETS}/textures/block/street_road.png")
    railed.save(f"{ASSETS}/textures/block/street_track.png")
    solid("street_rail", (168, 170, 174), noise_amount=0.04)
    solid("street_groove", (26, 26, 28), noise_amount=0.05)


def street_models():
    """
    Street track: a whole block of road. Running north-south its top has the rails in it; on the diagonal the top is
    plain road and the rails lie on it (a hair above), turned 45 degrees and reaching over the corners into the road
    blocks either side, the same as the ordinary diagonal track's.
    """
    sides = {f: ("#side", [0, 0, 16, 16]) for f in ("north", "south", "east", "west", "down")}

    def cube(top):
        faces = dict(sides)
        faces["up"] = (top, [0, 0, 16, 16])
        el = element([0, 0, 0], [16, 16, 16], faces)
        for f in el["faces"]:
            el["faces"][f]["cullface"] = f
        return el

    tex = {"particle": "reikotrains:block/street_road", "side": "reikotrains:block/street_road", "top": "reikotrains:block/street_track",
           "road": "reikotrains:block/street_road", "rail": "reikotrains:block/street_rail", "groove": "reikotrains:block/street_groove"}
    straight = {"parent": "block/block", "textures": tex, "elements": [cube("#top")]}
    with open(f"{ASSETS}/models/block/street_track_straight.json", "w") as out:
        json.dump(straight, out, indent=2)
    half = 8 * math.sqrt(2)
    z0, z1 = round(8 - half, 3), round(8 + half, 3)
    rot = {"origin": [8, 16, 8], "axis": "y", "angle": 45}
    y = 16.15
    strips = []
    for x0, x1, t in ((0, 2, "#rail"), (2, 3, "#groove"), (13, 14, "#groove"), (14, 16, "#rail")):
        strips.append({"from": [x0, y, z0], "to": [x1, y, z1], "rotation": rot,
                       "faces": {"up": {"texture": t, "uv": [0, 0, x1 - x0, 16]}}})
    diagonal = {"parent": "block/block", "textures": tex, "elements": [cube("#road")] + strips}
    with open(f"{ASSETS}/models/block/street_track_diagonal.json", "w") as out:
        json.dump(diagonal, out, indent=2)
    with open(f"{ASSETS}/blockstates/street_track.json", "w") as out:
        json.dump({"variants": {
            "axis=z": {"model": "reikotrains:block/street_track_straight"},
            "axis=x": {"model": "reikotrains:block/street_track_straight", "y": 90},
            "axis=se": {"model": "reikotrains:block/street_track_diagonal"},
            "axis=ne": {"model": "reikotrains:block/street_track_diagonal", "y": 90},
        }}, out, indent=2)
    with open(f"{ASSETS}/models/item/street_track.json", "w") as out:
        json.dump({"parent": "reikotrains:block/street_track_straight"}, out, indent=2)


def water_tower_textures():
    """
    The water tower: grey-brown weathered staves for its tank, dark iron hoops, dark timber legs, dark shingles on its
    roof, a black iron spout with a canvas hose, and its gauge's marks, lit blue or dark.
    """
    staves = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = shade((122, 98, 72), 1 + 0.07 * noise("tower_staves", x, y))
            if x % 4 == 0:
                c = shade(c, 0.78)  # the joints between staves
            staves.putpixel((x, y), c)
    staves.save(f"{ASSETS}/textures/block/water_tower_staves.png")
    shingles = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = shade((70, 52, 46), 1 + 0.08 * noise("tower_roof", x, y))
            if y % 4 == 3 or (x + (y // 4) * 2) % 5 == 0:
                c = shade(c, 0.75)
            shingles.putpixel((x, y), c)
    shingles.save(f"{ASSETS}/textures/block/water_tower_roof.png")
    solid("water_tower_hoop", (52, 52, 56), noise_amount=0.05)
    solid("water_tower_leg", (78, 58, 40), shade((78, 58, 40), 0.75), 0.07)
    solid("water_tower_spout", (36, 36, 40), noise_amount=0.05)
    solid("water_tower_hose", (150, 136, 104), noise_amount=0.07)
    solid("water_tower_mark_on", (70, 150, 230), noise_amount=0.03)
    solid("water_tower_mark_off", (30, 34, 44), noise_amount=0.04)
    solid("water_tower_board", (226, 222, 210), shade((226, 222, 210), 0.7), 0.03)


def water_tower_models():
    """
    The water tower, two blocks (it faces north, its spout towards the track at -z), three blocks tall to look at. The
    bottom block's model: four timber legs with cross braces, reaching up through the top block, and a deck on them.
    The top block's: a round-ish tank of staves bound with iron hoops on the deck, a pointed shingle roof, a gauge board
    on its front with four marks lit as it fills, and its spout, up against the tank or swung down out over the track
    (just above a tender) with its hose hanging.
    """
    tex = {"particle": "reikotrains:block/water_tower_staves", "staves": "reikotrains:block/water_tower_staves",
           "hoop": "reikotrains:block/water_tower_hoop", "leg": "reikotrains:block/water_tower_leg",
           "roof": "reikotrains:block/water_tower_roof", "spout": "reikotrains:block/water_tower_spout",
           "hose": "reikotrains:block/water_tower_hose", "board": "reikotrains:block/water_tower_board",
           "on": "reikotrains:block/water_tower_mark_on", "off": "reikotrains:block/water_tower_mark_off"}
    base = []
    for x0, z0 in ((1, 1), (13, 1), (1, 13), (13, 13)):
        base.append(element([x0, 0, z0], [x0 + 2, 26, z0 + 2], all_faces("#leg", (0, 0, 2, 16))))
    for y in (8, 17):
        for x0, z0, x1, z1 in ((3, 1.5, 13, 2.5), (3, 13.5, 13, 14.5), (1.5, 3, 2.5, 13), (13.5, 3, 14.5, 13)):
            base.append(element([x0, y, z0], [x1, y + 1.5, z1], all_faces("#leg", (0, 0, 10, 2))))
    base.append(element([0.5, 26, 0.5], [15.5, 28, 15.5], all_faces("#leg", (0, 0, 15, 15))))
    with open(f"{ASSETS}/models/block/water_tower_base.json", "w") as out:
        json.dump({"parent": "block/block", "ambientocclusion": False, "textures": tex, "elements": base}, out, indent=2)
    for marks in range(5):
        for filling in (False, True):
            els = []
            # The tank: a wide middle and narrower boxes either way, so it looks round from a distance
            # (The top block's own space, up to 12, is the legs' tops and the deck, drawn by the bottom block.)
            els.append(element([2, 12, 0.5], [14, 28, 15.5], all_faces("#staves", (0, 0, 12, 16))))
            els.append(element([0.5, 12, 2], [15.5, 28, 14], all_faces("#staves", (0, 0, 12, 16))))
            els.append(element([1.25, 12, 1.25], [14.75, 28, 14.75], all_faces("#staves", (0, 0, 12, 16))))
            for y in (14, 25):
                els.append(element([1, y, 1], [15, y + 1, 15], all_faces("#hoop", (0, 0, 14, 1))))
            for lo, hi, y0, y1 in ((1, 15, 28, 29.5), (3.5, 12.5, 29.5, 31), (6, 10, 31, 32)):
                els.append(element([lo, y0, lo], [hi, y1, hi], all_faces("#roof", (lo, lo, hi, hi))))
            # The gauge board on its front, right of the spout, with four marks
            els.append(element([10, 13, 0.25], [13, 27, 0.5], all_faces("#board", (0, 0, 3, 14))))
            for i in range(4):
                lit = i < marks
                y0 = 14 + 3 * i
                els.append(element([10.75, y0, 0], [12.25, y0 + 1.5, 0.25], {"north": ("#on" if lit else "#off", [0, 0, 1.5, 1.5])}))
            # The spout, hinged at the tank's front
            els.append(element([6.5, 16, -0.5], [9.5, 19, 0.5], all_faces("#spout", (0, 0, 3, 3))))
            if filling:
                els.append(element([7, 16.5, -16], [9, 18.5, -0.5], all_faces("#spout", (0, 0, 2, 16))))
                els.append(element([7.25, 14, -15.5], [8.75, 17, -14], all_faces("#hose", (0, 0, 1.5, 3))))
            else:
                els.append(element([7, 18.5, -1.5], [9, 31, 0.5], all_faces("#spout", (0, 0, 2, 12))))
            name = f"water_tower_top_{marks}{'_filling' if filling else ''}"
            with open(f"{ASSETS}/models/block/{name}.json", "w") as out:
                json.dump({"parent": "block/block", "ambientocclusion": False, "textures": tex, "elements": els}, out, indent=2)
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for half in ("lower", "upper"):
            for marks in range(5):
                for filling in ("false", "true"):
                    model = "water_tower_base" if half == "lower" else f"water_tower_top_{marks}{'_filling' if filling == 'true' else ''}"
                    v = {"model": f"reikotrains:block/{model}"}
                    if y:
                        v["y"] = y
                    variants[f"facing={facing},filling={filling},half={half},level={marks}"] = v
    with open(f"{ASSETS}/blockstates/water_tower.json", "w") as out:
        json.dump({"variants": variants}, out, indent=2)


def turntable_textures():
    """
    The turntable: its pit's floor of grey stone setts, black-painted girders with a row of rivets top and bottom,
    timber planks for its deck and walkways, the dark green boards of the little hut at its front end, the hut's
    window, and dark iron for its wheels and pivot.
    """
    setts = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            row = y // 4
            col = (x + (2 if row % 2 else 0)) // 4
            c = shade((132, 128, 120), 1 + 0.08 * noise("setts", row, col) + 0.04 * noise("setts", x, y))
            if y % 4 == 3 or (x + (2 if row % 2 else 0)) % 4 == 3:
                c = shade((92, 88, 82), 1 + 0.05 * noise("mortar", x, y))
            setts.putpixel((x, y), c)
    setts.save(f"{ASSETS}/textures/block/turntable_pit.png")
    girder = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = shade((40, 41, 45), 1 + 0.06 * noise("girder", x, y))
            if y in (1, 14) and x % 4 == 1:
                c = shade((70, 71, 76), 1)  # rivet heads
            if y in (0, 15):
                c = shade(c, 0.8)
            girder.putpixel((x, y), c)
    girder.save(f"{ASSETS}/textures/block/turntable_girder.png")
    planks = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            grain = 0.92 + 0.08 * math.sin(x * 1.3 + noise("plank", y // 4) * 3)
            c = shade((112, 86, 60), grain * (1 + 0.05 * noise("planks", x, y)))
            if y % 4 == 3:
                c = shade(c, 0.7)
            planks.putpixel((x, y), c)
    planks.save(f"{ASSETS}/textures/block/turntable_deck.png")
    hut = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = shade((44, 84, 58), 1 + 0.05 * noise("hut", x, y))
            if x % 4 == 0:
                c = shade(c, 0.78)
            hut.putpixel((x, y), c)
    hut.save(f"{ASSETS}/textures/block/turntable_hut.png")
    window = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15) or x == 7 or x == 8:
                c = shade((226, 220, 200), 1)
            else:
                c = shade((70, 96, 112), 1 + 0.1 * (1 - (x + y) / 30))
            window.putpixel((x, y), c)
    window.save(f"{ASSETS}/textures/block/turntable_window.png")
    solid("turntable_iron", (58, 58, 62), noise_amount=0.06)


def turntable_models():
    """
    The turntable's pit: every block of it a floor of setts, two pixels deep (the bridge, its wheels and the ring rail
    they run on are drawn by the pivot's renderer); the pivot, in the middle, also has the iron post the bridge turns on.
    """
    tex = {"particle": "reikotrains:block/turntable_pit", "floor": "reikotrains:block/turntable_pit",
           "iron": "reikotrains:block/turntable_iron"}
    floor = [element([0, 0, 0], [16, 2, 16], {"up": ("#floor", [0, 0, 16, 16]), "down": ("#floor", [0, 0, 16, 16]),
                                               "north": ("#floor", [0, 14, 16, 16]), "south": ("#floor", [0, 14, 16, 16]),
                                               "east": ("#floor", [0, 14, 16, 16]), "west": ("#floor", [0, 14, 16, 16])})]
    with open(f"{ASSETS}/models/block/turntable_pit.json", "w") as out:
        json.dump({"parent": "block/block", "textures": tex, "elements": floor}, out, indent=2)
    pivot = [element([3, 2, 3], [13, 4, 13], all_faces("#iron", (0, 0, 10, 2))),
             element([5, 4, 5], [11, 15, 11], all_faces("#iron", (0, 0, 6, 11)))]
    with open(f"{ASSETS}/models/block/turntable_pivot.json", "w") as out:
        json.dump({"parent": "block/block", "ambientocclusion": False, "textures": tex, "elements": pivot}, out, indent=2)
    with open(f"{ASSETS}/blockstates/turntable.json", "w") as out:
        json.dump({"multipart": [{"apply": {"model": "reikotrains:block/turntable_pit"}},
                                 {"when": {"x": "7", "z": "7"}, "apply": {"model": "reikotrains:block/turntable_pivot"}}]}, out, indent=2)
    with open(f"{ASSETS}/models/item/turntable.json", "w") as out:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "reikotrains:item/turntable"}}, out, indent=2)


def engine_shed_textures():
    """
    Engine shed doors: oxblood-red vertical boards for the doors, cream for the frames, ledges and braces on them, and
    dark creosoted timber for the posts and lintel of the frame they hang in.
    """
    boards = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = shade((122, 40, 34), 1 + 0.06 * noise("shed_door", x, y) + 0.04 * math.sin(y * 0.9 + noise("board", x // 4) * 3))
            if x % 4 == 3:
                c = shade(c, 0.72)  # the gaps between the boards
            boards.putpixel((x, y), c)
    boards.save(f"{ASSETS}/textures/block/engine_shed_door.png")
    solid("engine_shed_trim", (222, 210, 176), noise_amount=0.04)
    post = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = shade((70, 50, 36), (0.92 + 0.08 * math.sin(x * 1.9 + noise("post", y // 3))) * (1 + 0.05 * noise("post", x, y)))
            if x in (0, 15):
                c = shade(c, 0.75)
            post.putpixel((x, y), c)
    post.save(f"{ASSETS}/textures/block/engine_shed_post.png")
    lintel = post.rotate(90)
    lintel.save(f"{ASSETS}/textures/block/engine_shed_lintel.png")


def engine_shed_models():
    """
    The frame's blocks: the posts dark timber with the grain upright, the lintel the same lying across; the doorway's
    blocks have no model (the doors are drawn by the lintel's renderer).
    """
    for name, side in (("engine_shed_post", "engine_shed_post"), ("engine_shed_lintel", "engine_shed_lintel")):
        with open(f"{ASSETS}/models/block/{name}.json", "w") as out:
            json.dump({"parent": "minecraft:block/cube", "textures": {
                "particle": f"reikotrains:block/{side}", "north": f"reikotrains:block/{side}", "south": f"reikotrains:block/{side}",
                "east": f"reikotrains:block/{side}", "west": f"reikotrains:block/{side}", "up": "reikotrains:block/engine_shed_post",
                "down": "reikotrains:block/engine_shed_post"}}, out, indent=2)
    with open(f"{ASSETS}/models/block/engine_shed_doorway.json", "w") as out:
        json.dump({"textures": {"particle": "reikotrains:block/engine_shed_door"}, "elements": []}, out, indent=2)
    variants = {}
    for facing in ("north", "east", "south", "west"):
        for cell in range(25):
            across, up = cell % 5 - 2, cell // 5
            model = "engine_shed_lintel" if up == 4 else "engine_shed_post" if abs(across) == 2 else "engine_shed_doorway"
            for opened in ("false", "true"):
                variants[f"cell={cell},facing={facing},open={opened}"] = {"model": f"reikotrains:block/{model}"}
    with open(f"{ASSETS}/blockstates/engine_shed.json", "w") as out:
        json.dump({"variants": variants}, out, indent=2)
    with open(f"{ASSETS}/models/item/engine_shed.json", "w") as out:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "reikotrains:item/engine_shed"}}, out, indent=2)


def signal_box_textures():
    """
    The signal box desk: varnished brown wood for the cabinet, a lighter worn top, the levers' colours (red for
    signals, black for switches, blue for locks, yellow for distant signals), steel for the frame and handles, and the
    diagram board: dark green with the line drawn on it in white, a junction, and lamps.
    """
    wood = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = shade((96, 60, 36), (0.92 + 0.08 * math.sin(y * 1.1 + noise("box", x // 4) * 3)) * (1 + 0.05 * noise("box", x, y)))
            if x % 8 == 7 or y in (0, 15):
                c = shade(c, 0.75)
            wood.putpixel((x, y), c)
    wood.save(f"{ASSETS}/textures/block/signal_box_wood.png")
    top = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            top.putpixel((x, y), shade((150, 112, 72), (0.94 + 0.06 * math.sin(x * 0.9 + noise("top", y // 3) * 2)) * (1 + 0.04 * noise("top", x, y))))
    top.save(f"{ASSETS}/textures/block/signal_box_top.png")
    for name, colour in (("red", (178, 38, 30)), ("black", (34, 34, 36)), ("blue", (40, 70, 160)), ("yellow", (220, 176, 40))):
        solid(f"signal_box_{name}", colour, noise_amount=0.05)
    solid("signal_box_steel", (176, 178, 182), noise_amount=0.05)
    board = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = shade((22, 48, 32), 1 + 0.05 * noise("board", x, y))
            if x in (0, 15) or y in (0, 15):
                c = shade((96, 60, 36), 0.9)
            board.putpixel((x, y), c)
    for x in range(2, 14):
        board.putpixel((x, 9), (220, 224, 228, 255))
    for i, (x, y) in enumerate(((7, 8), (8, 7), (9, 6), (10, 5), (11, 5), (12, 5), (13, 5))):
        board.putpixel((x, y), (220, 224, 228, 255))
    for x, y, colour in ((4, 10, (230, 60, 50)), (11, 10, (60, 220, 90)), (12, 4, (240, 190, 40))):
        board.putpixel((x, y), colour + (255,))
    board.save(f"{ASSETS}/textures/block/signal_box_diagram.png")


def signal_box_models():
    """
    The signal box (facing north: its levers towards whoever's at it): a wooden cabinet with a desk top, a lever frame
    across the middle with six levers in it, and the diagram of the line on a board at the back.
    """
    tex = {"particle": "reikotrains:block/signal_box_wood", "wood": "reikotrains:block/signal_box_wood",
           "top": "reikotrains:block/signal_box_top", "steel": "reikotrains:block/signal_box_steel",
           "board": "reikotrains:block/signal_box_diagram"}
    for name in ("red", "black", "blue", "yellow"):
        tex[name] = f"reikotrains:block/signal_box_{name}"
    els = [
        element([1, 0, 2], [15, 9, 13.5], all_faces("#wood", (1, 7, 15, 16))),
        element([0.5, 9, 1.5], [15.5, 10, 14.5], {f: ("#top", [0.5, 1.5, 15.5, 14.5]) for f in ("up", "down")}
                | {f: ("#wood", [0, 0, 15, 1]) for f in ("north", "south", "east", "west")}),
        element([1, 10, 12.5], [15, 15.5, 13.5], {"north": ("#board", [0, 0, 16, 16]), "south": ("#wood", [1, 0, 15, 5.5]),
                                                 "east": ("#wood", [0, 0, 1, 5.5]), "west": ("#wood", [0, 0, 1, 5.5]),
                                                 "up": ("#wood", [1, 0, 15, 1]), "down": ("#wood", [1, 0, 15, 1])}),
        element([2, 10, 6.5], [14, 11, 9], all_faces("#steel", (0, 0, 12, 1))),
    ]
    for i, colour in enumerate(("red", "red", "black", "blue", "yellow", "black")):
        x = 2.6 + i * 1.95
        els.append(element([x, 10.5, 7.4], [x + 0.8, 14.5, 8.2], all_faces("#" + colour, (0, 0, 1, 4))))
        els.append(element([x - 0.15, 14.5, 7.25], [x + 0.95, 15.2, 8.35], all_faces("#steel", (0, 0, 1, 1))))
    with open(f"{ASSETS}/models/block/signal_box.json", "w") as out:
        json.dump({"parent": "block/block", "textures": tex, "elements": els}, out, indent=2)
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v = {"model": "reikotrains:block/signal_box"}
        if y:
            v["y"] = y
        variants[f"facing={facing}"] = v
    with open(f"{ASSETS}/blockstates/signal_box.json", "w") as out:
        json.dump({"variants": variants}, out, indent=2)
    with open(f"{ASSETS}/models/item/signal_box.json", "w") as out:
        json.dump({"parent": "reikotrains:block/signal_box"}, out, indent=2)


def signals_and_stations():
    signal_textures()
    signal_models()
    three_light_signal_models()
    station_models()
    switch_textures()
    switch_models()
    loader_textures()
    loader_models()
    crossing_textures()
    crossing_models()
    charger_textures()
    charger_models()
    sensor_textures()
    sensor_models()
    speed_sign_textures()
    speed_sign_models()
    catenary_textures()
    catenary_models()
    street_textures()
    street_models()
    water_tower_textures()
    water_tower_models()
    turntable_textures()
    turntable_models()
    engine_shed_textures()
    engine_shed_models()
    signal_box_textures()
    signal_box_models()


if __name__ == "__main__":
    main()
    signals_and_stations()
    loader_screen()
