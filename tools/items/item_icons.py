#!/usr/bin/env python3
"""
Draws the item icons (16x16 pixel art) and the mod list logo. Run from the repository root:

    python3 tools/items/item_icons.py
"""
import os

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ITEM_DIR = os.path.join(ROOT, "src/main/resources/assets/reikotrains/textures/item")

PALETTE = {
    ".": (0, 0, 0, 0),
    "k": (36, 36, 40, 255),     # black
    "K": (20, 20, 22, 255),     # outline
    "g": (44, 104, 62, 255),    # green
    "G": (30, 74, 44, 255),     # dark green
    "b": (214, 174, 76, 255),   # brass
    "r": (168, 42, 32, 255),    # red
    "R": (118, 28, 22, 255),    # dark red
    "y": (255, 236, 160, 255),  # lamp
    "n": (92, 92, 98, 255),     # roof
    "N": (64, 64, 70, 255),     # dark roof
    "w": (52, 52, 56, 255),     # wheel rim
    "s": (168, 170, 176, 255),  # steel
    "S": (110, 112, 118, 255),  # dark steel
    "l": (120, 170, 200, 255),  # glass
    "c": (232, 218, 176, 255),  # cream
    "C": (196, 180, 140, 255),  # dark cream
    "h": (104, 60, 44, 255),    # chocolate
    "H": (74, 42, 30, 255),     # dark chocolate
    "o": (146, 102, 60, 255),   # wood
    "O": (104, 70, 40, 255),    # dark wood
    "x": (196, 152, 94, 255),   # crate
    "X": (140, 104, 60, 255),   # crate dark
    "r": (200, 40, 30, 255),    # red lamp
    "R": (255, 110, 90, 255),   # red lamp, bright
    "g": (30, 150, 60, 255),    # green lamp
    "G": (110, 240, 140, 255),  # green lamp, bright
    "n": (26, 44, 96, 255),     # navy board
    "W": (240, 236, 220, 255),  # lettering
    "p": (236, 230, 210, 255),  # paper
    "P": (170, 60, 50, 255),    # heading
    "y": (240, 200, 70, 255),   # clock face
    "m": (124, 30, 36, 255),    # crimson
    "M": (86, 20, 26, 255),     # dark crimson
    "a": (60, 60, 66, 255),     # cab roof
    "e": (226, 196, 92, 255),   # lining
}

ICONS = {
    "steam_locomotive": [
        "................",
        ".KKKK...........",
        "..kk.....NNNNNNN",
        "..kk.....nnnnnnn",
        "..kk..bb..gggggg",
        "..kk..bb..gllggg",
        ".kkkGGGGGGgllggg",
        "ykkkgggbgggggggg",
        ".kkkgggbgggggggg",
        ".kkkGGGbGGgggggg",
        ".kkkGGGGGGgggggg",
        "rrrrrrrrrrrrrrrr",
        "s..ww..ww..ww..s",
        "..wrrwwrrwwrrw..",
        ".wwrrwwrrwwrrw..",
        "...ww..ww..ww...",
    ],
    "express_locomotive": [
        "................",
        "..KK............",
        "..kk.....aaaaaa.",
        "..kk..bb.mllmlm.",
        "kkkkmmmmmmllmlm.",
        "kkkkmmmmmmmmmmm.",
        "kkkkmmmmbmmmmmm.",
        "kkkkmmmmmmmmmmm.",
        "eeeeeeeeeeeeeee.",
        "MMMMMMMMMMMMMMM.",
        ".www.wwww.wwww..",
        "wsw.wmmmmwmmmmw.",
        "www.wmsmmwmsmmw.",
        "....wmmmmwmmmmw.",
        ".....wwww.wwww..",
        "................",
    ],
    "tender": [
        "................",
        "................",
        "...kkkk.........",
        "..kkkkkkk.......",
        "mmmmmmmmmmmmmmmm",
        "meeeeeeeeeeeeeem",
        "memmmmmmmmmmmmem",
        "memmmmmmmmmmmmem",
        "memmmmmmmmmmmmem",
        "meeeeeeeeeeeeeem",
        "MMMMMMMMMMMMMMMM",
        ".wwww.wwww.wwww.",
        ".wssw.wssw.wssw.",
        ".wwww.wwww.wwww.",
        "................",
        "................",
    ],
    "passenger_coach": [
        "................",
        "................",
        "................",
        "..NNNNNNNNNNNN..",
        ".nnnnnnnnnnnnnn.",
        ".cccccccccccccc.",
        ".clllcclllcclllc",
        ".clllcclllcclllc",
        ".cccccccccccccc.",
        ".bbbbbbbbbbbbbb.",
        ".hhhhhhhhhhhhhh.",
        ".hHhhhhHhhhhhHh.",
        "skkkkkkkkkkkkkks",
        "..ww.ww..ww.ww..",
        ".wSwwSw..wSwwSw.",
        "..ww.ww..ww.ww..",
    ],
    "freight_wagon": [
        "................",
        "................",
        "................",
        "..XXXXXX.XXXXX..",
        "..XxxxxX.XxxxX..",
        "..XXXXXX.XXXXX..",
        "..XxxxxX.XxxxX..",
        ".OoooooooooooooO",
        ".OoOOOOOOOOOOoOO",
        ".OooooooooooooOO",
        ".OoOOOOOOOOOOoOO",
        ".OooooooooooooOO",
        "skkkkkkkkkkkkkks",
        "..ww........ww..",
        ".wSww......wSww.",
        "..ww........ww..",
    ],
    "track": [
        "...sS......sS...",
        ".OoosSooooosSooO",
        ".OOOsSOOOOOsSOO.",
        "...sS......sS...",
        "...sS......sS...",
        ".OoosSooooosSooO",
        ".OOOsSOOOOOsSOO.",
        "...sS......sS...",
        "...sS......sS...",
        ".OoosSooooosSooO",
        ".OOOsSOOOOOsSOO.",
        "...sS......sS...",
        "...sS......sS...",
        ".OoosSooooosSooO",
        ".OOOsSOOOOOsSOO.",
        "...sS......sS...",
    ],
    "signal": [
        "......KKKK......",
        "......KrrK......",
        ".....KrRRrK.....",
        "......KrrK......",
        "......KKKK......",
        "......KggK......",
        ".....KgGGgK.....",
        "......KggK......",
        "......KKKK......",
        ".......SS.......",
        ".......SS.......",
        ".......SS.......",
        ".......SS.......",
        ".......SS.......",
        ".....SSSSSS.....",
        "................",
    ],
    "station": [
        "................",
        "................",
        "cccccccccccccccc",
        "cnnnnnnnnnnnnnnc",
        "cnWWnWnWWnWWnnnc",
        "cnWnnWnWnnWnnnnc",
        "cnnnnnnnnnnnnnnc",
        "cccccccccccccccc",
        ".SS..........SS.",
        ".SS..........SS.",
        ".SS..........SS.",
        ".SS..........SS.",
        ".SS..........SS.",
        ".SS..........SS.",
        ".SS..........SS.",
        "................",
    ],
    "timetable": [
        "..pppppppppppp..",
        "..pPPPPPPPPPPp..",
        "..pppppppppppp..",
        "..pkkkpkkkkkkp..",
        "..pppppppppppp..",
        "..pkkpppkkkkkp..",
        "..pppppppppppp..",
        "..pkkkkpkkkkpp..",
        "..pppppppppppp..",
        "..pkkkppbbbbpp..",
        "..pppppbyyyybp..",
        "..pkkkpbykyybp..",
        "..pppppbyykkbp..",
        "..pppppbyyyybp..",
        "..ppppppbbbbpp..",
        "................",
    ],
    "switch_stand": [
        "....KKKKKKKK....",
        "....KggggggK....",
        "....KgGGGGgK....",
        "....KgGGGGgK....",
        "....KgGGGGgK....",
        "....KgGGGGgK....",
        "....KggggggK....",
        "....KKKKKKKK....",
        ".......SS.......",
        ".......SS...rr..",
        ".......SS..rr...",
        ".......SS.rr....",
        ".......SSrr.....",
        "......kSSrk.....",
        "....SSSSSSSS....",
        "...SSSSSSSSSS...",
    ],
    "coupler": [
        "................",
        "..........sss...",
        ".........sSSSs..",
        "........sS...Ss.",
        "........sS...Ss.",
        ".........sS.Ss..",
        "........sSsss...",
        ".......sSs......",
        "......sSs.......",
        ".....sSs........",
        "...sssSs........",
        "..sSSSs.........",
        ".sS..Ss.........",
        ".sS..Ss.........",
        "..sSSs..........",
        "...ss...........",
    ],
}


def draw(rows):
    img = Image.new("RGBA", (16, 16))
    for y, row in enumerate(rows):
        assert len(row) == 16, (row, len(row))
        for x, ch in enumerate(row):
            img.putpixel((x, y), PALETTE[ch])
    return img


def main():
    os.makedirs(ITEM_DIR, exist_ok=True)
    for name, rows in ICONS.items():
        draw(rows).save(os.path.join(ITEM_DIR, f"{name}.png"))
    # Mod list logo: the locomotive, big, on a sky-blue tile.
    logo = Image.new("RGBA", (128, 128), (122, 168, 214, 255))
    for y in range(96, 128):
        for x in range(128):
            logo.putpixel((x, y), (92, 140, 72, 255) if y < 104 else (110, 86, 60, 255))
    loco = draw(ICONS["steam_locomotive"]).resize((112, 112), Image.NEAREST)
    logo.alpha_composite(loco, (8, 0))
    logo.save(os.path.join(ROOT, "src/main/resources/logo.png"))
    print("icons:", ", ".join(ICONS))


if __name__ == "__main__":
    main()
