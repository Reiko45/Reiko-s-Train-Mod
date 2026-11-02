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


def wheel_parts(prefix, xs, radius, center_y, mat, z=7):
    """A pair of wheels (left and right, on the rails) per axle; each is its own part so it can turn on its axle."""
    parts = []
    for i, x in enumerate(xs):
        for side, zc in (("l", -z), ("r", z)):
            p = Part(f"{prefix}_{side}{i}", (x, center_y, zc))
            p.boxes.append(Box(-radius, -radius, -1, int(2 * radius), int(2 * radius), 2, mat))
            parts.append(p)
    return parts


def mirrored(x0, y0, z0, x1, y1, z1, mat):
    """The same box on both sides of the car (z and -z)."""
    return [box(x0, y0, z0, x1, y1, z1, mat), box(x0, y0, -z1, x1, y1, -z0, mat)]


def buffers(half, y0=-14, y1=-10, z=11, length=4):
    """Buffers at both ends; their faces are `half` pixels from the middle."""
    out = []
    for zc in (-z, z):
        out.append(box(-half, y0, zc - 2, -half + length, y1, zc + 2, "buffer"))
        out.append(box(half - length, y0, zc - 2, half, y1, zc + 2, "buffer"))
    return out


def round_body(x0, x1, cy, r, mat, steps=((1.0, 0.6), (0.8, 0.8), (0.6, 1.0)), inset=0.5):
    """A cylinder along x (boiler, smokebox) made of overlapping boxes: wide-and-low to narrow-and-tall."""
    out = []
    for i, (w, h) in enumerate(steps):
        hw = round(r * w)
        hh = round(r * h)
        out.append(box(x0 + inset * i, cy - hh, -hw, x1 - inset * i, cy + hh, hw, mat))
    return out


# All models: 16 px to the block, the rails' tops at y = 0, -y up, -x the front, z across (rails at z = +-7).

def steam_locomotive():
    body = Part("body")
    b = body.boxes
    # Frame, running board, buffer beams, buffers
    b.append(box(-40, -13, -5, 40, -4, 5, "frame"))
    b.append(box(-42, -15, -16, 42, -13, 16, "deck"))
    b.append(box(-44, -16, -16, -42, -7, 16, "beam"))
    b.append(box(42, -16, -16, 44, -7, 16, "beam"))
    b += buffers(48)
    # Cylinders, outside the frame at the front
    b += mirrored(-36, -14, 10, -24, -5, 15, "cylinder")
    # Boiler and smokebox
    b += round_body(-30, 14, -26, 10, "boiler")
    b += round_body(-38, -30, -26, 11, "smokebox")
    b.append(box(-40, -40, -2, -38, -36, 2, "lamp"))
    b += mirrored(-44, -21, 10, -42, -16, 14, "lamp")
    b.append(box(-36, -46, -3, -30, -36, 3, "chimney"))
    b.append(box(-37, -48, -4, -29, -46, 4, "chimney_cap"))
    b.append(box(-12, -42, -4, -4, -36, 4, "dome"))
    b.append(box(-11, -44, -3, -5, -42, 3, "brass"))
    b.append(box(6, -40, -1, 8, -36, 1, "brass"))
    b.append(box(10, -42, -0.5, 11, -36, 0.5, "brass"))
    # Side tanks
    b += mirrored(-24, -32, 10, 12, -15, 15, "tank")
    # Cab: spectacle plate with two windows, sides open above the waist, roof
    b.append(box(14, -30, -15, 15, -15, 15, "cab"))
    b.append(box(14, -46, -15, 15, -42, 15, "cab"))
    b.append(box(14, -42, -3, 15, -30, 3, "cab"))
    b += mirrored(14, -42, 14, 15, -30, 15, "cab")
    b += mirrored(15, -28, 14, 32, -15, 15, "cab")
    b += mirrored(15, -46, 14, 18, -28, 15, "cab")
    b += mirrored(29, -46, 14, 32, -28, 15, "cab")
    b.append(box(31, -46, -14, 32, -30, -4, "cab"))
    b.append(box(31, -46, 4, 32, -30, 14, "cab"))
    b.append(box(13, -48, -16, 33, -46, 16, "roof"))
    b.append(box(13, -49, -12, 33, -48, 12, "roof"))
    # Coal bunker behind the cab, firebox door, and the crew's bench
    b.append(box(32, -30, -15, 42, -15, 15, "bunker"))
    b.append(box(33, -31, -13, 41, -30, 13, "coal"))
    b.append(box(15, -24, -4, 16, -16, 4, "firebox"))
    b.append(box(22, -17, -13, 26, -15, 13, "bench"))

    parts = [body]
    parts += wheel_parts("wheel", [-32, 32], 4, -4, "wheel_small")
    parts += wheel_parts("driver", [-16, 0, 16], 7, -7, "wheel_driver")
    # Coupling rods, one each side, joining the crank pins of the three drivers
    for side, z in (("l", -9), ("r", 9)):
        p = Part(f"rod_{side}", (0, -7, z))
        p.boxes.append(Box(-16.5, -1, -0.5, 33, 2, 1, "rod"))
        parts.append(p)
    return parts


def express_locomotive():
    """
    A 4-6-0 express engine, eight blocks long: four small leading wheels, six big driving wheels, a long boiler with
    smoke deflectors either side of the smokebox, and a roomy cab. Its coal is in a separate tender.
    """
    body = Part("body")
    b = body.boxes
    # Frame, running board (above the driving wheels), buffer beams, buffers
    b.append(box(-58, -19, -5, 58, -4, 5, "frame"))
    b.append(box(-58, -21, -16, 58, -19, 16, "deck_express"))
    b.append(box(-60, -24, -16, -58, -10, 16, "beam"))
    b.append(box(58, -22, -14, 60, -10, 14, "beam"))
    b += buffers(64)
    b.append(box(-54, -8, -6, -34, -5, 6, "frame"))  # leading bogie
    # Cylinders, outside the frame between the bogie and the drivers
    b += mirrored(-46, -20, 10, -30, -8, 15, "cylinder_express")
    # Smokebox, boiler and a squared-off firebox
    b += round_body(-56, -46, -32, 11, "smokebox_express")
    b += round_body(-46, 12, -32, 10, "boiler_express")
    b.append(box(12, -42, -12, 26, -21, 12, "firebox_casing"))
    b.append(box(-54, -50, -3, -48, -43, 3, "chimney"))
    b.append(box(-55, -52, -4, -47, -50, 4, "chimney_cap_express"))
    b.append(box(-14, -46, -4, -6, -42, 4, "brass"))
    b.append(box(-13, -47, -3, -7, -46, 3, "brass"))
    b.append(box(16, -45, -2, 20, -42, 2, "brass"))
    b.append(box(24, -47, -0.5, 25, -42, 0.5, "brass"))
    # Smoke deflectors and lamps
    b += mirrored(-58, -40, 15, -44, -21, 16, "deflector")
    b.append(box(-58, -47, -2, -56, -43, 2, "lamp"))
    b += mirrored(-60, -28, 10, -58, -24, 14, "lamp")
    # Cab: front plate with two windows beside the firebox, sides open above the waist, roof
    b.append(box(26, -34, -15, 27, -21, 15, "cab_express"))
    b.append(box(26, -49, -15, 27, -45, 15, "cab_express"))
    b.append(box(26, -45, -3, 27, -34, 3, "cab_express"))
    b += mirrored(26, -45, 14, 27, -34, 15, "cab_express")
    b += mirrored(27, -32, 14, 46, -21, 15, "cab_express")
    b += mirrored(27, -49, 14, 30, -32, 15, "cab_express")
    b += mirrored(43, -49, 14, 46, -32, 15, "cab_express")
    b.append(box(25, -51, -16, 48, -49, 16, "roof"))
    b.append(box(25, -52, -12, 48, -51, 12, "roof"))
    b.append(box(27, -31, -4, 28, -23, 4, "firebox"))
    b.append(box(40, -24, -13, 44, -21, 13, "bench"))

    parts = [body]
    parts += wheel_parts("wheel", [-50, -38], 4.5, -4.5, "wheel_small")
    parts += wheel_parts("driver", [-18, 2, 22], 9, -9, "wheel_express")
    # Coupling rods, one each side, joining the crank pins of the three drivers
    for side, z in (("l", -9), ("r", 9)):
        p = Part(f"rod_{side}", (2, -9, z))
        p.boxes.append(Box(-20.5, -1, -0.5, 41, 2, 1, "rod"))
        parts.append(p)
    return parts


def tender():
    """The express engine's tender: a water tank with a coal space at the front, on six wheels."""
    body = Part("body")
    b = body.boxes
    b.append(box(-40, -15, -5, 40, -4, 5, "frame"))
    b.append(box(-40, -17, -16, 40, -15, 16, "deck_express"))
    b.append(box(-42, -18, -14, -40, -10, 14, "beam"))
    b.append(box(40, -18, -16, 42, -8, 16, "beam"))
    b += buffers(44)
    b += mirrored(-36, -12, 9, 36, -9, 10, "frame")
    for x in (-26, 0, 26):
        b += mirrored(x - 2, -10, 10, x + 2, -6, 11, "axlebox")
    # Tank sides, back, coal plate at the front, coal rails, the water tank's top and its filler
    b += mirrored(-36, -34, 14, 40, -17, 15, "tender_side")
    b.append(box(39, -34, -14, 40, -17, 14, "tender_side"))
    b.append(box(-36, -26, -14, -35, -17, 14, "tender_side"))
    b += mirrored(-36, -37, 14, 30, -34, 15, "frame")
    b.append(box(10, -35, -14, 39, -34, 14, "tender_top"))
    b.append(box(30, -37, -2, 34, -35, 2, "brass"))
    parts = [body]
    # The coal, in three layers: the tender shows as much as it has
    for i, (x0, y0, z, x1, y1) in enumerate(((-35, -24, 13, 10, -17), (-35, -30, 13, 10, -24), (-32, -36, 10, 6, -30))):
        p = Part(f"coal_{i}", (0, 0, 0))
        p.boxes.append(box(x0, y0, -z, x1, y1, z, "coal"))
        parts.append(p)
    parts += wheel_parts("wheel", [-26, 0, 26], 5, -5, "wheel_small")
    return parts


def diesel_locomotive():
    """
    A diesel road-switcher, seven blocks long: a long hood in front, a high cab with windows all round, a short hood
    behind, a walkway with handrails along both sides, and the fuel tank slung between the bogies (drawn separately).
    """
    body = Part("body")
    b = body.boxes
    # Underframe, walkway, end beams, buffers, fuel tank
    b.append(box(-54, -17, -13, 54, -12, 13, "frame"))
    b.append(box(-56, -19, -16, 56, -17, 16, "walkway"))
    b.append(box(-58, -22, -16, -56, -10, 16, "diesel_end"))
    b.append(box(56, -22, -16, 58, -10, 16, "diesel_end"))
    b += buffers(62)
    b.append(box(-22, -12, -12, 22, -5, 12, "fuel_tank"))
    # Long hood, with exhaust stacks and the radiator fan; short hood behind the cab
    b.append(box(-54, -36, -11, 8, -19, 11, "hood"))
    b.append(box(-18, -39, -2, -14, -36, 2, "exhaust"))
    b.append(box(-5, -39, -2, -1, -36, 2, "exhaust"))
    b.append(box(-48, -37, -8, -32, -36, 8, "fan"))
    b.append(box(34, -36, -11, 54, -19, 11, "hood"))
    # Headlights, front and back
    b.append(box(-55, -33, -3, -54, -29, 3, "lamp"))
    b.append(box(54, -33, -3, 55, -29, 3, "lamp_back"))
    # Cab: walls with windows (open, so the crew can see out), roof, horn, control desk
    for x0 in (8, 33):
        b.append(box(x0, -38, -15, x0 + 1, -19, 15, "cab_diesel"))
        b.append(box(x0, -52, -15, x0 + 1, -47, 15, "cab_diesel"))
        for z0, z1 in ((-15, -13), (-3, 3), (13, 15)):
            b.append(box(x0, -47, z0, x0 + 1, -38, z1, "cab_diesel"))
    b += mirrored(9, -36, 14, 33, -19, 15, "cab_diesel")
    b += mirrored(9, -52, 14, 33, -46, 15, "cab_diesel")
    for x0, x1 in ((9, 12), (19, 22), (30, 33)):
        b += mirrored(x0, -46, 14, x1, -36, 15, "cab_diesel")
    b.append(box(7, -54, -16, 35, -52, 16, "roof"))
    b.append(box(7, -55, -12, 35, -54, 12, "roof"))
    b.append(box(15, -58, -2, 21, -55, 2, "horn"))
    b.append(box(9, -27, -13, 13, -19, 13, "desk"))
    # Handrails along the walkways, on stanchions
    b += mirrored(-54, -31, 15, 8, -30, 16, "handrail")
    b += mirrored(34, -31, 15, 54, -30, 16, "handrail")
    for x in (-53, -38, -23, -8, 6, 35, 52):
        b += mirrored(x, -30, 15, x + 1, -19, 16, "handrail")
    return [body]


def electric_locomotive():
    """
    A battery-electric locomotive, seven and a half blocks long: a full-width body with a cab at each end (a yellow
    nose under a wide windscreen), louvred sides, equipment on the roof, battery boxes slung between the bogies and a
    charging socket on each side. It rides on the diesel's bogies.
    """
    body = Part("body")
    b = body.boxes
    # Underframe, end beams, buffers, battery boxes
    b.append(box(-54, -17, -13, 54, -12, 13, "frame"))
    b.append(box(-58, -22, -15, -56, -10, 15, "diesel_end"))
    b.append(box(56, -22, -15, 58, -10, 15, "diesel_end"))
    b += buffers(60)
    b.append(box(-22, -12, -12, 22, -5, 12, "battery_box"))
    # The body, with a nose and a windscreen at each end
    b.append(box(-52, -46, -15, 52, -17, 15, "electric_body"))
    b.append(box(-56, -36, -14, -52, -17, 14, "electric_nose"))
    b.append(box(52, -36, -14, 56, -17, 14, "electric_nose"))
    b.append(box(-54, -44, -14, -52, -36, 14, "electric_screen"))
    b.append(box(52, -44, -14, 54, -36, 14, "electric_screen"))
    # Roof: equipment covers either side of a cooling fan, and a horn over each cab
    b.append(box(-50, -48, -13, 50, -46, 13, "electric_roof"))
    b.append(box(-34, -51, -9, -12, -48, 9, "roof_box"))
    b.append(box(12, -51, -9, 34, -48, 9, "roof_box"))
    b.append(box(-7, -49, -7, 7, -48, 7, "electric_fan"))
    b.append(box(-48, -50, -2, -44, -48, 2, "horn"))
    b.append(box(44, -50, -2, 48, -48, 2, "horn"))
    # A charging socket in the middle of each side, and grab rails by the cab doors
    b += mirrored(-2, -30, 15, 2, -26, 16, "socket")
    for x in (-44, -36, 35, 43):
        b += mirrored(x, -38, 15, x + 1, -22, 16, "handrail")
    return [body]


def diesel_bogie():
    """A two-axle bogie for the diesel: deeper sideframes with a spring pack, bigger wheels."""
    frame = Part("frame")
    b = frame.boxes
    b += mirrored(-16, -11, 8, 16, -5, 10, "frame")
    b.append(box(-3, -13, -10, 3, -9, 10, "frame"))
    b += mirrored(-14, -10, 10, -8, -4, 11, "axlebox")
    b += mirrored(8, -10, 10, 14, -4, 11, "axlebox")
    b += mirrored(-4, -10, 10, 4, -6, 11, "spring")
    parts = [frame]
    parts += wheel_parts("wheel", [-11, 11], 5, -5, "wheel_small")
    return parts


COACH_BAYS = [-30, -10, 10, 30]


def passenger_coach():
    body = Part("body")
    b = body.boxes
    b.append(box(-50, -16, -12, 50, -12, 12, "frame"))
    b += buffers(52)
    b.append(box(-50, -17, -15, 50, -16, 15, "floor"))
    # Sides: chocolate panels to the waist, cream window posts and band above
    b += mirrored(-50, -27, 14, 50, -17, 15, "coach_lower")
    b += mirrored(-50, -44, 14, 50, -38, 15, "coach_upper")
    for x0, x1 in ((-50, -37), (-23, -17), (-3, 3), (17, 23), (37, 50)):
        b += mirrored(x0, -38, 14, x1, -27, 15, "coach_upper")
    # Ends, with doors
    b.append(box(-50, -44, -14, -49, -17, 14, "coach_end"))
    b.append(box(49, -44, -14, 50, -17, 14, "coach_end"))
    # Roof
    b.append(box(-51, -45, -16, 51, -44, 16, "roof"))
    b.append(box(-51, -46, -13, 51, -45, 13, "roof"))
    b.append(box(-49, -47, -8, 49, -46, 8, "roof"))
    # Benches: two either side of the aisle in each bay, facing forwards
    for c in COACH_BAYS:
        b += mirrored(c - 3, -20, 2, c + 5, -17, 13, "seat")
        b += mirrored(c + 5, -31, 2, c + 7, -17, 13, "seat")
    return [body]


def freight_wagon():
    body = Part("body")
    b = body.boxes
    b.append(box(-38, -16, -12, 38, -12, 12, "frame"))
    b += buffers(40)
    b.append(box(-38, -17, -16, 38, -16, 16, "planks"))
    b += mirrored(-38, -33, 15, 38, -17, 16, "planks")
    b.append(box(-38, -33, -15, -37, -17, 15, "planks"))
    b.append(box(37, -33, -15, 38, -17, 15, "planks"))
    parts = [body]
    for i, (x0, x1) in enumerate(((-35, -19), (-17, -1), (1, 17), (19, 35))):
        p = Part(f"cargo_{i}", (0, 0, 0))
        p.boxes.append(box(x0, -35, -13, x1, -17, 13, "crate"))
        parts.append(p)
    return parts


def tank_wagon():
    """A tank wagon: a silver barrel on saddles, with a filler dome, ladders and a gauge on each side (drawn lit a
    quarter at a time as it fills: the level_ parts)."""
    body = Part("body")
    b = body.boxes
    b.append(box(-42, -16, -12, 42, -12, 12, "frame"))
    b += buffers(44)
    b.append(box(-44, -18, -14, -42, -10, 14, "beam_dark"))
    b.append(box(42, -18, -14, 44, -10, 14, "beam_dark"))
    for x in (-28, 20):
        b.append(box(x, -20, -11, x + 8, -16, 11, "frame"))
    b += round_body(-38, 38, -30, 12, "tank_barrel")
    b += round_body(-40, -38, -30, 10, "tank_end", inset=0)
    b += round_body(38, 40, -30, 10, "tank_end", inset=0)
    b.append(box(-5, -46, -5, 5, -42, 5, "tank_dome"))
    b.append(box(-4, -47, -4, 4, -46, 4, "brass"))
    for z0, z1 in ((12, 13), (-13, -12)):
        b.append(box(-1, -40, z0, 3, -17, z1, "ladder"))
    b += mirrored(-30, -37, 12, -4, -32, 13, "gauge_back")
    parts = [body]
    for i in range(4):
        p = Part(f"level_{i}", (0, 0, 0))
        p.boxes += mirrored(-29 + 6 * i, -36, 13, -24 + 6 * i, -33, 14, "gauge_lit")
        parts.append(p)
    return parts


def guards_van():
    """
    A guard's van on four wheels: a planked body with windows, an open veranda with a railing at the front end, a
    stove pipe on the roof, and red tail lamps on the back.
    """
    body = Part("body")
    b = body.boxes
    b.append(box(-36, -14, -12, 36, -10, 12, "frame"))
    b += buffers(40)
    b.append(box(-38, -16, -15, 38, -14, 15, "deck"))
    b.append(box(-40, -18, -14, -38, -10, 14, "beam_dark"))
    b.append(box(38, -18, -14, 40, -10, 14, "beam_dark"))
    b += mirrored(-26, -10, 10, -14, -6, 11, "axlebox")
    b += mirrored(14, -10, 10, 26, -6, 11, "axlebox")
    # The van: sides with two windows each, a back wall, a front wall with a door onto the veranda
    b += mirrored(-22, -30, 14, 38, -16, 15, "van_planks")
    b += mirrored(-22, -44, 14, 38, -40, 15, "van_planks")
    for x0, x1 in ((-22, -16), (-6, 6), (22, 38)):
        b += mirrored(x0, -40, 14, x1, -30, 15, "van_planks")
    b.append(box(37, -44, -14, 38, -16, 14, "van_end"))
    b.append(box(-22, -44, -14, -21, -16, -4, "van_end"))
    b.append(box(-22, -44, 4, -21, -16, 14, "van_end"))
    b.append(box(-22, -44, -4, -21, -38, 4, "van_end"))
    b.append(box(-24, -46, -16, 40, -44, 16, "roof"))
    b.append(box(-24, -47, -12, 40, -46, 12, "roof"))
    b.append(box(-16, -52, 5, -13, -46, 8, "chimney"))
    b.append(box(-12, -24, -13, -4, -16, -6, "stove"))
    # The veranda's railing, and the tail lamps
    b += mirrored(-38, -29, 14, -22, -28, 15, "handrail")
    b += mirrored(-38, -28, 14, -37, -16, 15, "handrail")
    b += mirrored(-30, -28, 14, -29, -16, 15, "handrail")
    b += mirrored(38, -36, 9, 40, -32, 12, "lamp_back")
    parts = [body]
    parts += wheel_parts("wheel", [-20, 20], 5, -5, "wheel_small")
    return parts


def bogie():
    frame = Part("frame")
    b = frame.boxes
    b += mirrored(-14, -10, 8, 14, -6, 10, "frame")
    b.append(box(-3, -12, -10, 3, -8, 10, "frame"))
    b += mirrored(-11, -9, 10, -7, -5, 11, "axlebox")
    b += mirrored(7, -9, 10, 11, -5, 11, "axlebox")
    parts = [frame]
    parts += wheel_parts("wheel", [-9, 9], 4.5, -4.5, "wheel_small")
    return parts


# ------------------------------------------------------------------------------------------------------------------
# Painting
# ------------------------------------------------------------------------------------------------------------------

GREEN = (40, 96, 58)
CRIMSON = (124, 30, 36)
DIESEL_BLUE = (36, 70, 122)
WARNING_YELLOW = (232, 190, 40)
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


BOILER_BANDS = (-20, -19, -2, -1, 10, 11)


def boiler(t):
    c = jitter(GREEN, (t.x, t.y, t.z, t.face))
    if not t.end and int(math.floor(t.x)) in BOILER_BANDS:
        return jitter(BRASS, (t.x, t.y, t.z))
    if t.edge and t.end:
        return shade(c, 0.8)
    return c


def smokebox(t):
    c = jitter(BLACK, (t.x, t.y, t.z, t.face), 0.08)
    if t.face == "west":
        cy, cz = -26.0, 0.0
        r = math.hypot(t.y - cy, t.z - cz)
        if 6.5 <= r < 7.6:
            return (84, 84, 90)
        if r < 1.3:
            return STEEL
        if abs(t.y - cy) < 0.6 and r < 6.5:
            return (64, 64, 70)
    return c


def lamp(t):
    if t.face == "west" and not t.edge:
        return LAMP
    return jitter(BLACK, (t.x, t.y, t.z))


def chimney(t):
    return jitter(BLACK, (t.x, t.y, t.z, t.face), 0.08)


def chimney_cap(t):
    if t.face == "down" and abs(t.x + 33) < 2.2 and abs(t.z) < 2.2:
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


def wheel(rim, body, hub, spokes, crank=3.0):
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
        if r < 1.4:
            return hub
        if spokes:
            if abs(dx - crank) < 1.0 and abs(dy) < 1.0:  # crank pin
                return BRASS
            if dx < -2.2 and abs(dy) < 3.6 and r < r_out - 1:  # counterweight
                return shade(body, 0.72)
            a = math.degrees(math.atan2(dy, dx)) % 30
            if min(a, 30 - a) < 6:
                return body
            if r > r_out - 2:
                return shade(body, 0.85)
            return TRANSPARENT
        if r > r_out - 2:
            return shade(body, 0.8)
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
    upper = t.y < -27
    c = jitter(CREAM if upper else CHOCOLATE, (t.x, t.y, t.z, t.face), 0.04)
    if t.end and abs(t.z) < 4 and t.y > -39:
        if abs(t.z) < 2.6 and -37 < t.y < -30:
            return (40, 52, 64)  # door window
        if abs(t.z) > 3.0 or t.y < -38:
            return shade(c, 0.7)  # door frame
        if abs(t.z - 2.0) < 0.6 and abs(t.y + 24) < 0.6:
            return BRASS  # handle
        return shade(c, 0.93)
    if t.end and t.y < -27 and abs(abs(t.z) - 9) < 3.5 and -37 < t.y < -29:
        return (40, 52, 64)  # end windows
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
    if t.side and (int(math.floor(t.x)) + 38) % 13 in (0, 1):
        return (54, 54, 58)
    if t.end and int(math.floor(t.z)) in (-8, -7, 7, 8):
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


def cylinder(t):
    if t.end:
        r = math.hypot(t.y + 9.5, t.z - (12.5 if t.z > 0 else -12.5))
        if r < 3.6:
            return shade(STEEL, 0.9 if r > 2.6 else 1.0)
    return jitter((48, 48, 52), (t.x, t.y, t.z, t.face), 0.06)


def bunker(t):
    c = jitter(GREEN, (t.x, t.y, t.z, t.face))
    if t.face in ("north", "south", "east"):
        ii, jj = t.i, t.j
        if (ii in (1, t.fw - 2) and 1 <= jj <= t.fh - 2) or (jj in (1, t.fh - 2) and 1 <= ii <= t.fw - 2):
            return LINING
    return shade(c, 0.78) if t.edge else c


def lined(color):
    """Paintwork with a thin yellow line one pixel in from the edges of its outward faces."""
    def paint(t):
        c = jitter(color, (t.x, t.y, t.z, t.face))
        if t.face in ("north", "south", "east", "west"):
            ii, jj = t.i, t.j
            if t.fw > 4 and t.fh > 4 and ((ii in (1, t.fw - 2) and 1 <= jj <= t.fh - 2) or (jj in (1, t.fh - 2) and 1 <= ii <= t.fw - 2)):
                return LINING
        return shade(c, 0.78) if t.edge else c
    return paint


def banded(color, bands):
    """A boiler: paint, with brass bands round it."""
    def paint(t):
        c = jitter(color, (t.x, t.y, t.z, t.face))
        if not t.end and int(math.floor(t.x)) in bands:
            return jitter(BRASS, (t.x, t.y, t.z))
        if t.edge and t.end:
            return shade(c, 0.8)
        return c
    return paint


def smokebox_door(cy, ring):
    def paint(t):
        c = jitter(BLACK, (t.x, t.y, t.z, t.face), 0.08)
        if t.face == "west":
            r = math.hypot(t.y - cy, t.z)
            if ring - 1.1 <= r < ring:
                return (84, 84, 90)
            if r < 1.3:
                return STEEL
            if abs(t.y - cy) < 0.6 and r < ring - 1.1:
                return (64, 64, 70)
        return c
    return paint


def cylinder_at(cy, cz):
    def paint(t):
        if t.end:
            r = math.hypot(t.y - cy, t.z - (cz if t.z > 0 else -cz))
            if r < 4.6:
                return shade(STEEL, 0.9 if r > 3.4 else 1.0)
        return jitter((48, 48, 52), (t.x, t.y, t.z, t.face), 0.06)
    return paint


def cap_at(cx):
    def paint(t):
        if t.face == "down" and abs(t.x - cx) < 2.2 and abs(t.z) < 2.2:
            return (12, 12, 12)
        return shade(jitter(BLACK, (t.x, t.y, t.z)), 1.1)
    return paint


def deck_with(color):
    def paint(t):
        if t.face in ("down", "up"):
            return jitter((52, 52, 56), (t.x, t.z))
        return jitter(color, (t.x, t.y, t.z))
    return paint


def hood(t):
    """The hoods: blue, with door panels and louvres along the sides and yellow warning panels on the ends."""
    if t.end:
        c = jitter(WARNING_YELLOW, (t.x, t.y, t.z, t.face), 0.04)
        return shade(c, 0.8) if t.edge else c
    c = jitter(DIESEL_BLUE, (t.x, t.y, t.z, t.face))
    if t.side:
        x = int(math.floor(t.x))
        if (x + 54) % 10 == 0:
            return shade(c, 0.7)  # door seams
        if t.j in (3, 5, 7) and (x + 54) % 10 not in (0, 1, 9):
            return shade(c, 0.55)  # louvres
        if t.j == t.fh - 4:
            return (220, 220, 214)  # a white stripe low down
    if t.face == "down" and abs(t.z) < 9.5 and int(math.floor(t.x)) % 2 == 0:
        return shade(c, 0.85)  # roof panels
    return shade(c, 0.8) if t.edge else c


def cab_diesel(t):
    c = jitter(DIESEL_BLUE, (t.x, t.y, t.z, t.face))
    if t.end and t.y > -38 and abs(t.z) < 14:
        return jitter(WARNING_YELLOW, (t.x, t.y, t.z), 0.04)  # yellow on the cab's front and back below the windows
    if t.side and abs(t.y + 22.5) < 0.6:
        return (220, 220, 214)
    return shade(c, 0.8) if t.edge else c


def fan(t):
    if t.face == "down":
        r = math.hypot(t.x + 40, t.z)
        if r < 7.5:
            a = math.degrees(math.atan2(t.z, t.x + 40)) % 45
            return (30, 30, 34) if (a < 18 or r < 1.5) else (70, 72, 78)
    return jitter((60, 62, 68), (t.x, t.y, t.z))


def exhaust(t):
    if t.face == "down" and abs(t.z) < 1.2 and abs(t.x - (t.box.x + 2)) < 1.2:
        return (10, 10, 10)
    return jitter((40, 40, 44), (t.x, t.y, t.z), 0.08)


def lamp_back(t):
    if t.face == "east" and not t.edge:
        return (255, 90, 70)  # a red tail lamp
    return jitter(BLACK, (t.x, t.y, t.z))


def walkway(t):
    if t.face in ("down", "up"):
        c = jitter((70, 72, 78), (t.x, t.z), 0.06)
        return shade(c, 0.85) if (int(math.floor(t.x)) + int(math.floor(t.z))) % 3 == 0 else c  # tread plate
    return jitter((34, 34, 38), (t.x, t.y, t.z))


def diesel_end(t):
    """The end beams: yellow and black chevrons on the outer face."""
    if t.end:
        stripe = int(math.floor((t.y + t.z + 64) / 3)) % 2
        return WARNING_YELLOW if stripe else (30, 30, 32)
    return jitter((34, 34, 38), (t.x, t.y, t.z))


ELECTRIC_RED = (176, 36, 34)
GLASS = (46, 60, 78)


def electric_body(t):
    """
    Red, with a grey cant rail along the top, a white stripe low down and a dark skirt; on the sides, a window and a
    door at each cab and louvred panels in between.
    """
    c = jitter(ELECTRIC_RED, (t.x, t.y, t.z, t.face))
    if t.face in ("down", "up"):
        return jitter((150, 152, 158), (t.x, t.z), 0.04)
    if t.y < -44:
        return jitter((196, 198, 202), (t.x, t.y, t.z, t.face), 0.03)
    if t.end:
        return c
    x = abs(t.x)
    if abs(t.y + 23.5) < 0.6:
        return (232, 232, 226)
    if t.y > -20:
        return jitter((54, 54, 60), (t.x, t.y, t.z, t.face), 0.05)
    if 44 <= x < 50 and -42 <= t.y < -36:
        return shade(GLASS, 1.15) if t.y < -40 else GLASS  # cab side window
    if x in (37.5, 42.5) or (37 <= x < 43 and abs(t.y + 43.5) < 0.6):
        return shade(c, 0.6)  # door seams
    if 38 <= x < 42 and -42 <= t.y < -37:
        return GLASS  # door window
    if 6 <= x < 32 and -42 <= t.y < -31:
        if x in (6.5, 31.5, 18.5) or t.y in (-41.5, -31.5):
            return shade(c, 0.7)  # louvre panel frames
        return shade(c, 0.55) if int(math.floor(t.x)) % 2 == 0 else shade(c, 0.85)  # louvres
    return c


def electric_nose(t):
    """The cab's nose: yellow, with a pair of headlights at the front and tail lamps at the back."""
    if t.face in ("down", "up"):
        return jitter((54, 54, 60), (t.x, t.z), 0.05)
    c = jitter(WARNING_YELLOW, (t.x, t.y, t.z, t.face), 0.04)
    if t.end and abs(abs(t.z) - 9) < 2.1 and -32 <= t.y < -28:
        if abs(abs(t.z) - 9) > 1.1 or t.y < -31.5 or t.y > -28.5:
            return (40, 40, 44)  # lamp rims
        return (255, 255, 246) if t.face == "west" else (255, 70, 50)
    return shade(c, 0.85) if t.edge and t.end else c


def electric_screen(t):
    """A wide windscreen with a pillar down the middle."""
    if not t.end:
        return jitter((54, 54, 60), (t.x, t.y, t.z), 0.05)
    if t.edge or abs(t.z) < 0.6:
        return (34, 34, 38)
    return shade(GLASS, 1.3) if t.j <= 1 else GLASS


def electric_fan(t):
    if t.face == "down":
        r = math.hypot(t.x, t.z)
        if r < 6.5:
            a = math.degrees(math.atan2(t.z, t.x)) % 45
            return (30, 30, 34) if (a < 18 or r < 1.5) else (70, 72, 78)
    return jitter((60, 62, 68), (t.x, t.y, t.z))


def roof_box(t):
    c = jitter((122, 124, 130), (t.x, t.y, t.z, t.face), 0.04)
    if t.face == "down" and int(math.floor(t.x)) % 3 == 0:
        return shade(c, 0.75)  # vents along the top
    return shade(c, 0.8) if t.edge else c


def battery_box(t):
    """Battery boxes under the floor: dark grey, with a yellow and black hazard stripe along the sides."""
    c = jitter((52, 54, 60), (t.x, t.y, t.z, t.face), 0.05)
    if t.side and t.j == 1:
        return WARNING_YELLOW if int(math.floor(t.x)) % 4 < 2 else (30, 30, 32)
    return shade(c, 0.8) if t.edge else c


def socket(t):
    """The charging socket's cover: orange, with the socket's dark face in the middle."""
    if t.side and not t.edge:
        return (30, 30, 34)
    return jitter((236, 150, 30), (t.x, t.y, t.z), 0.04)


def tank_barrel(t):
    """A silver barrel with a red band round its middle, and darker weld seams."""
    c = jitter((178, 182, 188), (t.x, t.y, t.z, t.face), 0.04)
    if abs(t.y + 30) < 1.6 and t.face in ("north", "south"):
        return jitter((170, 40, 34), (t.x, t.y, t.z), 0.04)
    if int(math.floor(t.x)) in (-19, 0, 19):
        return shade(c, 0.85)
    return shade(c, 0.88) if t.edge and t.end else c


def van_planks(t):
    """The van's sides: grey-brown vertical planks."""
    c = jitter((112, 92, 76), (t.x, t.y, t.z, t.face), 0.06)
    if t.side and int(math.floor(t.x)) % 3 == 0:
        return shade(c, 0.82)
    return shade(c, 0.8) if t.edge else c


def van_end(t):
    c = jitter((112, 92, 76), (t.x, t.y, t.z, t.face), 0.06)
    if t.end and int(math.floor(t.z)) % 3 == 0:
        return shade(c, 0.82)
    return shade(c, 0.8) if t.edge else c


def fuel_tank(t):
    c = jitter((48, 50, 56), (t.x, t.y, t.z, t.face), 0.05)
    if t.side and t.j == 1:
        return shade(c, 1.25)
    return shade(c, 0.8) if t.edge else c


MATERIALS = {
    "frame": frame,
    "deck": deck,
    "cylinder": cylinder,
    "bunker": bunker,
    "dome": plain(GREEN),
    "bench": plain((112, 76, 46)),
    "axlebox": plain((70, 70, 76)),
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
    "wheel_driver": wheel((44, 44, 48), RED, BRASS, True, 3.0),
    "rod": rod,
    "floor": floor,
    "coach_lower": coach_lower,
    "coach_upper": coach_upper,
    "coach_end": coach_end,
    "seat": seat,
    "planks": planks,
    "crate": crate,
    "deck_express": deck_with(CRIMSON),
    "cylinder_express": cylinder_at(-14, 12.5),
    "smokebox_express": smokebox_door(-32, 8.6),
    "boiler_express": banded(CRIMSON, (-34, -33, -12, -11, 8, 9)),
    "firebox_casing": lined(CRIMSON),
    "chimney_cap_express": cap_at(-51),
    "deflector": plain(BLACK, 0.85),
    "cab_express": plain(CRIMSON, 0.8),
    "wheel_express": wheel((44, 44, 48), CRIMSON, BRASS, True, 4.0),
    "tender_side": lined(CRIMSON),
    "tender_top": plain((44, 44, 48)),
    "hood": hood,
    "cab_diesel": cab_diesel,
    "fan": fan,
    "exhaust": exhaust,
    "lamp_back": lamp_back,
    "walkway": walkway,
    "diesel_end": diesel_end,
    "fuel_tank": fuel_tank,
    "horn": plain((150, 152, 158), 0.75),
    "desk": plain((60, 62, 68)),
    "handrail": plain((226, 226, 220), 0.85),
    "spring": plain((90, 92, 98), 0.7),
    "tank_barrel": tank_barrel,
    "tank_end": plain((166, 170, 176), 0.85),
    "tank_dome": plain((150, 154, 160), 0.8),
    "ladder": plain((60, 62, 68), 0.8),
    "gauge_back": plain((30, 30, 34), 0.8),
    "gauge_lit": plain((250, 190, 60), 0.85),
    "beam_dark": plain((40, 40, 44), 0.8),
    "van_planks": van_planks,
    "van_end": van_end,
    "stove": plain((40, 40, 44), 0.75),
    "electric_body": electric_body,
    "electric_nose": electric_nose,
    "electric_screen": electric_screen,
    "electric_roof": plain((150, 152, 158), 0.82, 0.04),
    "electric_fan": electric_fan,
    "roof_box": roof_box,
    "battery_box": battery_box,
    "socket": socket,
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
    build("steam_locomotive", "SteamLocomotiveGeometry", steam_locomotive(), 512,
          "    /** The coupling rods: they follow the crank pins, this far (pixels) from the axles. */\n"
          '    public static final String[] RODS = {"rod_l", "rod_r"};\n'
          "    public static final float CRANK_RADIUS = 3.0F;\n"
          "    /** The radius of the driving wheels the rods are pinned to, in pixels. */\n"
          "    public static final float DRIVER_RADIUS = 7.0F;\n")
    build("passenger_coach", "PassengerCoachGeometry", passenger_coach(), 512)
    build("freight_wagon", "FreightWagonGeometry", freight_wagon(), 512,
          "    /** The crates that show how full the wagon is: one to four. */\n"
          '    public static final String[] CARGO = {"cargo_0", "cargo_1", "cargo_2", "cargo_3"};\n')
    build("bogie", "BogieGeometry", bogie(), 128)
    build("express_locomotive", "ExpressLocomotiveGeometry", express_locomotive(), 512,
          "    /** The coupling rods: they follow the crank pins, this far (pixels) from the axles. */\n"
          '    public static final String[] RODS = {"rod_l", "rod_r"};\n'
          "    public static final float CRANK_RADIUS = 4.0F;\n"
          "    /** The radius of the driving wheels the rods are pinned to, in pixels. */\n"
          "    public static final float DRIVER_RADIUS = 9.0F;\n")
    build("diesel_locomotive", "DieselLocomotiveGeometry", diesel_locomotive(), 512)
    build("diesel_bogie", "DieselBogieGeometry", diesel_bogie(), 128)
    build("electric_locomotive", "ElectricLocomotiveGeometry", electric_locomotive(), 512)
    build("tank_wagon", "TankWagonGeometry", tank_wagon(), 512,
          "    /** The gauge's lit quarters, one to four as it fills. */\n"
          '    public static final String[] LEVEL = {"level_0", "level_1", "level_2", "level_3"};\n')
    build("guards_van", "GuardsVanGeometry", guards_van(), 512)
    build("tender", "TenderGeometry", tender(), 512,
          "    /** The coal, in three layers: as much shows as the tender has. */\n"
          '    public static final String[] CARGO = {"coal_0", "coal_1", "coal_2"};\n')


if __name__ == "__main__":
    main()
