#!/usr/bin/env python3
"""
Generates the train models: the Java geometry (client/model/geometry/*Geometry.java) and the matching entity
textures (assets/reikotrains/textures/entity/*.png), from the part lists below. Both come from the same list, so
the texture always lines up with the boxes. Run it from the repository root after changing a model:

    python3 tools/models/train_models.py

Model space is Minecraft's usual entity model space, in pixels (1/16 of a block): -x is the FRONT of the car,
-y is UP, and the rails are at y = 5. Every box gets its own patch of the texture (box UV), painted by a
"material" that knows what that kind of surface looks like.
"""
import math
import os
import zlib
from dataclasses import dataclass, field

import numpy as np
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
JAVA_DIR = os.path.join(ROOT, "src/main/java/com/reiko/trains/client/model/geometry")
TEX_DIR = os.path.join(ROOT, "src/main/resources/assets/reikotrains/textures/entity")

# ------------------------------------------------------------------------------------------------------------------
# Model description
# ------------------------------------------------------------------------------------------------------------------


@dataclass
class Box:
    x: float
    y: float
    z: float
    w: int
    h: int
    d: int
    mat: str
    u: int = 0
    v: int = 0


@dataclass
class Part:
    name: str
    pivot: tuple = (0.0, 0.0, 0.0)
    boxes: list = field(default_factory=list)


def box(x0, y0, z0, x1, y1, z1, mat):
    """A box from one corner to the other, in model space."""
    w, h, d = x1 - x0, y1 - y0, z1 - z0
    assert abs(w - round(w)) < 1e-6 and abs(h - round(h)) < 1e-6 and abs(d - round(d)) < 1e-6, (x0, y0, z0, x1, y1, z1)
    return Box(x0, y0, z0, int(round(w)), int(round(h)), int(round(d)), mat)


def wheel_parts(prefix, xs, radius, center_y, mat, z_out=6):
    """A pair of wheels (left and right) per axle; each is its own part so it can turn about its axle."""
    parts = []
    for i, x in enumerate(xs):
        for side, z in (("l", -z_out + 0.5), ("r", z_out - 0.5)):
            p = Part(f"{prefix}_{side}{i}", (x, center_y, z))
            p.boxes.append(Box(-radius, -radius, -0.5, int(2 * radius), int(2 * radius), 1, mat))
            parts.append(p)
    return parts


def mirrored(x0, y0, z0, x1, y1, z1, mat):
    """The same box on both sides of the car (z and -z)."""
    return [box(x0, y0, z0, x1, y1, z1, mat), box(x0, y0, -z1, x1, y1, -z0, mat)]


def buffers(half):
    """Buffers at both ends; their faces are `half` pixels from the middle."""
    out = []
    for zc in (-5, 5):
        out.append(box(-half, -4, zc - 1, -half + 2, -2, zc + 1, "buffer"))
        out.append(box(half - 2, -4, zc - 1, half, -2, zc + 1, "buffer"))
    return out


def steam_locomotive():
    body = Part("body")
    b = body.boxes
    # Running gear and frame
    b.append(box(-16, -3, -4, 16, 2, 4, "frame"))
    b.append(box(-16, -5, -7, 16, -4, 7, "deck"))
    b.append(box(-17, -5, -7, -16, -1, 7, "beam"))
    b.append(box(16, -5, -7, 17, -1, 7, "beam"))
    b += buffers(19)
    # Boiler (two boxes make an octagon), smokebox, chimney, domes
    b.append(box(-12, -14, -5, 5, -6, 5, "boiler"))
    b.append(box(-11.5, -15, -4, 4.5, -5, 4, "boiler"))
    b.append(box(-15, -15, -5, -12, -5, 5, "smokebox"))
    b.append(box(-16, -14, -1, -15, -12, 1, "lamp"))
    b.append(box(-14, -21, -1.5, -11, -15, 1.5, "chimney"))
    b.append(box(-14.5, -22, -2, -10.5, -21, 2, "chimney_cap"))
    b.append(box(-4, -18, -2, 0, -15, 2, "brass"))
    b.append(box(2, -17, -0.5, 3, -15, 0.5, "brass"))
    # Side tanks
    b += mirrored(-9, -11, 5, 5, -5, 7, "tank")
    # Cab: front wall with a big window over the boiler, sides with posts, a low back wall, and a roof
    b.append(box(5, -15, -6, 6, -5, 6, "cab"))
    b.append(box(5, -24, -6, 6, -22, 6, "cab"))
    b += mirrored(5, -22, 4, 6, -15, 6, "cab")
    b += mirrored(5, -12, 6, 16, -5, 7, "cab")
    b += mirrored(5, -24, 6, 7, -12, 7, "cab")
    b += mirrored(14, -24, 6, 16, -12, 7, "cab")
    b.append(box(15, -12, -6, 16, -5, 6, "cab"))
    b.append(box(4, -25, -8, 17, -24, 8, "roof"))
    b.append(box(4, -26, -6, 17, -25, 6, "roof"))
    # Fire door and a coal shelf inside the cab
    b.append(box(6, -10, -3, 7, -5, 3, "firebox"))
    b.append(box(13, -8, -6, 15, -5, -2, "coal"))

    parts = [body]
    parts += wheel_parts("wheel", [-13], 2.5, 2.5, "wheel_small")
    parts += wheel_parts("driver", [-6, 3, 12], 4, 1, "wheel_driver")
    # Coupling rods, one each side, joining the crank pins of the three drivers
    for side, z in (("l", -6.5), ("r", 6.5)):
        p = Part(f"rod_{side}", (3, 1, z))
        p.boxes.append(Box(-9.5, -0.5, -0.5, 19, 1, 1, "rod"))
        parts.append(p)
    return parts


COACH_SEATS = [-15, -5, 5, 15]


def passenger_coach():
    body = Part("body")
    b = body.boxes
    b.append(box(-20, -4, -6, 20, -2, 6, "frame"))
    b += buffers(22)
    # Bogies
    for xc in (-13, 13):
        b += mirrored(xc - 6, 0, 6, xc + 6, 2, 7, "frame")
        b.append(box(xc - 2, -2, -4, xc + 2, 0, 4, "frame"))
    # Floor
    b.append(box(-19, -5, -6, 19, -4, 6, "floor"))
    # Sides: chocolate lower panels, cream window posts and upper band
    b += mirrored(-20, -11, 6, 20, -4, 7, "coach_lower")
    b += mirrored(-20, -24, 6, 20, -19, 7, "coach_upper")
    for x0, x1 in ((-20, -18), (-12, -8), (-2, 2), (8, 12), (18, 20)):
        b += mirrored(x0, -19, 6, x1, -11, 7, "coach_upper")
    # Ends, with doors
    b.append(box(-20, -24, -6, -19, -4, 6, "coach_end"))
    b.append(box(19, -24, -6, 20, -4, 6, "coach_end"))
    # Roof
    b.append(box(-21, -25, -8, 21, -24, 8, "roof"))
    b.append(box(-21, -26, -5, 21, -25, 5, "roof"))
    # Benches, one under each seat, facing forward
    for s in COACH_SEATS:
        b.append(box(s - 2, -6, -5, s + 2, -5, 5, "seat"))
        b.append(box(s + 2, -13, -5, s + 3, -5, 5, "seat"))
    parts = [body]
    parts += wheel_parts("wheel", [-16.5, -9.5, 9.5, 16.5], 2.5, 2.5, "wheel_small")
    return parts


def freight_wagon():
    body = Part("body")
    b = body.boxes
    b.append(box(-18, -4, -6, 18, -2, 6, "frame"))
    b += buffers(20)
    b += mirrored(-14, 0, 6, -8, 2, 7, "frame")
    b += mirrored(8, 0, 6, 14, 2, 7, "frame")
    b.append(box(-18, -5, -7, 18, -4, 7, "planks"))
    b += mirrored(-18, -13, 6, 18, -5, 7, "planks")
    b.append(box(-18, -13, -6, -17, -5, 6, "planks"))
    b.append(box(17, -13, -6, 18, -5, 6, "planks"))
    parts = [body]
    parts += wheel_parts("wheel", [-11, 11], 2.5, 2.5, "wheel_small")
    for i, (x0, x1) in enumerate(((-16, -6), (-5, 5), (6, 16))):
        p = Part(f"cargo_{i}", (0, 0, 0))
        p.boxes.append(box(x0, -15, -5, x1, -5, 5, "crate"))
        parts.append(p)
    return parts


# ------------------------------------------------------------------------------------------------------------------
# Painting
# ------------------------------------------------------------------------------------------------------------------

GREEN = (40, 96, 58)
BLACK = (34, 34, 38)
RED = (158, 38, 30)
BRASS = (212, 172, 74)
STEEL = (150, 152, 158)
ROOF = (84, 84, 90)
CHOCOLATE = (96, 56, 42)
CREAM = (228, 214, 172)
WOOD = (138, 96, 56)
CRATE = (186, 142, 86)
PLUSH = (150, 54, 48)
LAMP = (255, 238, 170)
LINING = (226, 196, 92)
TRANSPARENT = (0, 0, 0, 0)


def noise(*key):
    h = zlib.crc32(repr(key).encode()) & 0xFFFF
    return (h / 0xFFFF) * 2 - 1


def shade(c, f):
    return tuple(max(0, min(255, int(round(ch * f)))) for ch in c[:3])


def jitter(c, key, amount=0.05):
    return shade(c, 1 + amount * noise(*key))


class Texel:
    """One pixel of one face: where it is on the face, and where that is on the model."""

    def __init__(self, face, i, j, fw, fh, x, y, z, bx):
        self.face, self.i, self.j, self.fw, self.fh = face, i, j, fw, fh
        self.x, self.y, self.z = x, y, z
        self.box = bx

    @property
    def edge(self):
        return self.i == 0 or self.j == 0 or self.i == self.fw - 1 or self.j == self.fh - 1

    @property
    def side(self):  # the long sides of the car
        return self.face in ("north", "south")

    @property
    def end(self):  # front (west) or back (east)
        return self.face in ("west", "east")


def plain(color, edge_factor=0.78, amount=0.05):
    def paint(t):
        c = jitter(color, (t.x, t.y, t.z, t.face), amount)
        return shade(c, edge_factor) if t.edge else c

    return paint


def boiler(t):
    c = jitter(GREEN, (t.x, t.y, t.z, t.face))
    if not t.end and int(math.floor(t.x)) in (-9, -3, 3):
        return jitter(BRASS, (t.x, t.y, t.z))
    if t.edge and t.end:
        return shade(c, 0.8)
    return c


def smokebox(t):
    c = jitter(BLACK, (t.x, t.y, t.z, t.face), 0.08)
    if t.face == "west":
        cy, cz = -10.0, 0.0
        r = math.hypot(t.y - cy, t.z - cz)
        if 3.0 <= r < 4.0:
            return (78, 78, 84)
        if r < 0.8:
            return STEEL
        if abs(t.y - cy) < 0.6 and r < 3.0:
            return (60, 60, 66)
    return c


def lamp(t):
    if t.face == "west" and not t.edge:
        return LAMP
    return jitter(BLACK, (t.x, t.y, t.z))


def chimney(t):
    return jitter(BLACK, (t.x, t.y, t.z, t.face), 0.08)


def chimney_cap(t):
    if t.face == "up" and abs(t.x + 12.5) < 1.2 and abs(t.z) < 1.2:
        return (12, 12, 12)
    return shade(jitter(BLACK, (t.x, t.y, t.z)), 1.1)


def tank(t):
    c = jitter(GREEN, (t.x, t.y, t.z, t.face))
    if t.side and t.face == ("south" if t.z > 0 else "north"):
        # lining: a thin yellow line one pixel in from the edge
        ii, jj = t.i, t.j
        if (ii in (1, t.fw - 2) and 1 <= jj <= t.fh - 2) or (jj in (1, t.fh - 2) and 1 <= ii <= t.fw - 2):
            return LINING
    return shade(c, 0.78) if t.edge else c


def cab(t):
    c = jitter(GREEN, (t.x, t.y, t.z, t.face))
    return shade(c, 0.8) if t.edge else c


def firebox(t):
    if t.face == "east" and 1 <= t.i <= t.fw - 2 and 1 <= t.j <= t.fh - 2:
        return (255, 140, 40) if (t.i + t.j) % 2 else (230, 90, 30)
    return jitter(BLACK, (t.x, t.y, t.z))


def coal(t):
    v = noise(t.x, t.y, t.z, t.face)
    return (22, 22, 24) if v < 0.2 else (48, 48, 52)


def buffer(t):
    if t.end:
        return STEEL if not t.edge else shade(STEEL, 0.8)
    return jitter(BLACK, (t.x, t.y, t.z))


def deck(t):
    if t.face in ("down", "up"):
        return jitter((52, 52, 56), (t.x, t.z))
    return jitter(RED, (t.x, t.y, t.z))


def wheel(rim, body, hub, spokes):
    def paint(t):
        if not t.side:
            return TRANSPARENT
        r_out = t.fw / 2
        dx, dy = t.x, t.y  # wheel parts are centred on their axle
        r = math.hypot(dx, dy)
        if r > r_out:
            return TRANSPARENT
        if r > r_out - 1:
            return rim
        if r < 1.0:
            return hub
        if spokes:
            if dx < -1.4 and abs(dy) < 2.2 and r > 1.4:  # counterweight
                return shade(body, 0.75)
            if abs(dx - 2) < 0.8 and abs(dy) < 0.8:  # crank pin
                return BRASS
            a = math.degrees(math.atan2(dy, dx)) % 45
            if min(a, 45 - a) < 13:
                return body
            return TRANSPARENT
        return body

    return paint


def rod(t):
    return jitter(STEEL, (t.x, t.y, t.z), 0.06)


def coach_lower(t):
    c = jitter(CHOCOLATE, (t.x, t.y, t.z, t.face))
    if t.side and t.j == 0:
        return LINING
    if t.side and int(math.floor(t.x)) % 10 == 0:
        return shade(c, 0.82)
    return shade(c, 0.85) if (t.edge and not t.side) else c


def coach_upper(t):
    c = jitter(CREAM, (t.x, t.y, t.z, t.face), 0.03)
    return shade(c, 0.88) if t.edge else c


def coach_end(t):
    upper = t.y < -11
    c = jitter(CREAM if upper else CHOCOLATE, (t.x, t.y, t.z, t.face), 0.04)
    if t.end and abs(t.z) < 2.5 and t.y > -20:
        if abs(t.z) < 2.0 and -19 < t.y < -14:
            return (40, 52, 64)  # door window
        if abs(t.z) > 2.0 or t.y < -19.5:
            return shade(c, 0.7)  # door frame
        if abs(t.z - 1.4) < 0.6 and abs(t.y + 11) < 0.6:
            return BRASS  # handle
        return shade(c, 0.93)
    return c


def roof(t):
    c = jitter(ROOF, (t.x, t.y, t.z, t.face), 0.04)
    return shade(c, 0.82) if t.edge else c


def seat(t):
    c = jitter(PLUSH, (t.x, t.y, t.z, t.face), 0.06)
    return shade(c, 0.8) if t.edge else c


def floor(t):
    c = jitter(WOOD, (t.x, t.z), 0.08)
    if int(math.floor(t.z)) % 3 == 0:
        return shade(c, 0.8)
    return c


def planks(t):
    # planks run lengthways along the sides and across the ends; dark iron straps hold them
    along = t.y if not t.face in ("up", "down") else t.z
    c = jitter(WOOD, (t.x, t.y, t.z, t.face), 0.07)
    if int(math.floor(along)) % 2 == 0:
        c = shade(c, 0.88)
    if t.side and int(math.floor(t.x)) in (-18, -12, -6, 5, 11, 17):
        return (54, 54, 58)
    if t.end and int(math.floor(t.z)) in (-6, 5):
        return (54, 54, 58)
    return c


def crate(t):
    c = jitter(CRATE, (t.x, t.y, t.z, t.face), 0.06)
    if t.edge:
        return shade(c, 0.66)
    u, v = t.i / max(1, t.fw - 1), t.j / max(1, t.fh - 1)
    if abs(u - v) < 1.2 / max(t.fw, t.fh) or abs(u + v - 1) < 1.2 / max(t.fw, t.fh):
        return shade(c, 0.78)
    return c


def frame(t):
    return jitter(BLACK, (t.x, t.y, t.z, t.face), 0.06)


MATERIALS = {
    "frame": frame,
    "deck": deck,
    "beam": plain(RED),
    "buffer": buffer,
    "boiler": boiler,
    "smokebox": smokebox,
    "lamp": lamp,
    "chimney": chimney,
    "chimney_cap": chimney_cap,
    "brass": plain(BRASS, 0.8),
    "tank": tank,
    "cab": cab,
    "roof": roof,
    "firebox": firebox,
    "coal": coal,
    "wheel_small": wheel((44, 44, 48), (70, 70, 76), STEEL, False),
    "wheel_driver": wheel((44, 44, 48), RED, BRASS, True),
    "rod": rod,
    "floor": floor,
    "coach_lower": coach_lower,
    "coach_upper": coach_upper,
    "coach_end": coach_end,
    "seat": seat,
    "planks": planks,
    "crate": crate,
}


def faces(bx):
    """Each face's place on the texture (box UV), and how its pixels map back onto the model."""
    u, v, w, h, d = bx.u, bx.v, bx.w, bx.h, bx.d
    x0, y0, z0 = bx.x, bx.y, bx.z
    x1, y1, z1 = x0 + w, y0 + h, z0 + d
    return [
        # name, texture origin, size, pixel -> model position
        ("down", (u + d, v), (w, d), lambda i, j: (x0 + i + .5, y0, z1 - (j + .5))),  # top of the car (model -y)
        ("up", (u + d + w, v), (w, d), lambda i, j: (x0 + i + .5, y1, z1 - (j + .5))),
        ("west", (u, v + d), (d, h), lambda i, j: (x0, y0 + j + .5, z1 - (i + .5))),  # front
        ("north", (u + d, v + d), (w, h), lambda i, j: (x0 + i + .5, y0 + j + .5, z0)),
        ("east", (u + d + w, v + d), (d, h), lambda i, j: (x1, y0 + j + .5, z0 + i + .5)),  # back
        ("south", (u + 2 * d + w, v + d), (w, h), lambda i, j: (x1 - (i + .5), y0 + j + .5, z1)),
    ]


def pack(parts, width):
    """Shelf-packs every box's texture patch; returns the texture height needed (a power of two)."""
    boxes = [bx for p in parts for bx in p.boxes]
    order = sorted(boxes, key=lambda bx: -(bx.d + bx.h))
    x = y = shelf = 0
    for bx in order:
        bw, bh = 2 * (bx.w + bx.d), bx.d + bx.h
        if x + bw > width:
            x, y, shelf = 0, y + shelf, 0
        bx.u, bx.v = x, y
        x += bw
        shelf = max(shelf, bh)
    height = y + shelf
    p2 = 16
    while p2 < height:
        p2 *= 2
    return p2


def paint(parts, width, height):
    img = np.zeros((height, width, 4), dtype=np.uint8)
    for p in parts:
        for bx in p.boxes:
            painter = MATERIALS[bx.mat]
            for name, (fu, fv), (fw, fh), to_model in faces(bx):
                for i in range(fw):
                    for j in range(fh):
                        x, y, z = to_model(i, j)
                        c = painter(Texel(name, i, j, fw, fh, x, y, z, bx))
                        img[fv + j, fu + i] = c if len(c) == 4 else (*c, 255)
    return Image.fromarray(img, "RGBA")


# ------------------------------------------------------------------------------------------------------------------
# Java output
# ------------------------------------------------------------------------------------------------------------------


def f(n):
    s = f"{float(n):.2f}".rstrip("0").rstrip(".")
    return (s if "." in s else s + ".0") + "F"


def java(class_name, parts, width, height, extra=""):
    lines = []
    for p in parts:
        cubes = "".join(
            f"\n                .texOffs({bx.u}, {bx.v}).addBox({f(bx.x)}, {f(bx.y)}, {f(bx.z)}, {f(bx.w)}, {f(bx.h)}, {f(bx.d)})"
            for bx in p.boxes)
        px, py, pz = p.pivot
        lines.append(f'        root.addOrReplaceChild("{p.name}", CubeListBuilder.create(){cubes},\n'
                     f'                PartPose.offset({f(px)}, {f(py)}, {f(pz)}));')
    body = "\n".join(lines)
    return f"""// GENERATED by tools/models/train_models.py from the same part list as its texture. Don't edit by hand:
// change the script and run it again.
package com.reiko.trains.client.model.geometry;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class {class_name} {{
    public static final int TEXTURE_WIDTH = {width};
    public static final int TEXTURE_HEIGHT = {height};
{extra}
    private {class_name}() {{
    }}

    public static LayerDefinition create() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{body}
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }}
}}
"""


def wheel_meta(parts):
    names = [p.name for p in parts if p.name.startswith(("wheel_", "driver_"))]
    radii = [p.boxes[0].w / 2 for p in parts if p.name.startswith(("wheel_", "driver_"))]
    s = "    /** The wheel parts, which turn as the car rolls, and their radii in pixels. */\n"
    s += "    public static final String[] WHEELS = {" + ", ".join(f'"{n}"' for n in names) + "};\n"
    s += "    public static final float[] WHEEL_RADII = {" + ", ".join(f(r) for r in radii) + "};\n"
    return s


def build(name, class_name, parts, width, extra=""):
    height = pack(parts, width)
    img = paint(parts, width, height)
    os.makedirs(TEX_DIR, exist_ok=True)
    os.makedirs(JAVA_DIR, exist_ok=True)
    img.save(os.path.join(TEX_DIR, f"{name}.png"))
    with open(os.path.join(JAVA_DIR, f"{class_name}.java"), "w") as out:
        out.write(java(class_name, parts, width, height, wheel_meta(parts) + extra))
    print(f"{name}: {sum(len(p.boxes) for p in parts)} boxes, texture {width}x{height}")


def main():
    loco = steam_locomotive()
    build("steam_locomotive", "SteamLocomotiveGeometry", loco, 256,
          "    /** The coupling rods: they follow the crank pins, this far (pixels) from the axles. */\n"
          '    public static final String[] RODS = {"rod_l", "rod_r"};\n'
          "    public static final float CRANK_RADIUS = 2.0F;\n"
          "    /** The radius of the driving wheels the rods are pinned to, in pixels. */\n"
          "    public static final float DRIVER_RADIUS = 4.0F;\n")
    build("passenger_coach", "PassengerCoachGeometry", passenger_coach(), 256)
    build("freight_wagon", "FreightWagonGeometry", freight_wagon(), 256,
          "    /** The crates that show how full the wagon is: one, two or all three. */\n"
          '    public static final String[] CARGO = {"cargo_0", "cargo_1", "cargo_2"};\n')


if __name__ == "__main__":
    main()
