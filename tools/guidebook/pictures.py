"""Crops the README's screenshots (docs/screenshots) into the Railway Handbook's pictures: python3 tools/guidebook/pictures.py
(book.py runs it too, after checking every picture the book shows has a crop here).

Each picture is a 256x256 texture with the picture in its top-left 200x200, as Patchouli's image pages draw it. A crop
that isn't square is fitted in, on a background of its own average colour. The book's pages are written by book.py.
"""
from PIL import Image, ImageStat
import os

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SHOTS = f'{REPO}/docs/screenshots'
OUT = f'{REPO}/src/main/resources/assets/reikotrains/textures/gui/guide'

# picture: (screenshot, crop box x0, y0, x1, y1), or just the screenshot for a square from its middle
CROPS = {
    'welcome': ('train.jpg', 340, 150, 1000, 590),
    'cab': ('water_gauge.png', 0, 0, 488, 336),
    'laying_track': ('laying_track.jpg', 360, 0, 1000, 640),
    'junction': ('junction.jpg', 300, 200, 900, 676),
    'street_track': ('street_track.jpg', 300, 0, 972, 672),
    'track_layer_line': ('track_layer_line.jpg', 100, 0, 940, 524),
    'track_layer_screen': ('track_layer_screen.png', 0, 0, 356, 444),
    'bullet_train': ('bullet_train.jpg', 420, 180, 900, 660),
    'livestock_wagon': ('livestock_wagon.jpg', 300, 120, 860, 676),
    'cars_by_rail': ('cars_by_rail.jpg', 440, 190, 920, 670),
    'water_tower': ('water_tower.jpg', 300, 0, 972, 672),
    'electric_charging': ('electric_charging.jpg', 300, 80, 940, 620),
    'turntable_turning': ('turntable_turning.jpg', 300, 0, 972, 672),
    'engine_shed': ('engine_shed.jpg', 300, 0, 972, 672),
    'overhead_line': ('overhead_line.jpg', 300, 0, 972, 672),
    'pantograph': ('pantograph.jpg', 300, 0, 972, 672),
    'low_floor_tram': ('low_floor_tram.jpg', 300, 0, 972, 672),
    'signals': ('signals.jpg', 380, 200, 860, 676),
    'three_light_signals': ('three_light_signals.jpg', 200, 0, 1080, 480),
    'signal_box': ('signal_box.jpg', 180, 0, 740, 560),
    'speed_sign': ('speed_sign.jpg', 300, 200, 776, 676),
    'switch_branch': ('switch_branch.jpg', 120, 0, 830, 537),
    'track_sensor': ('track_sensor.jpg', 240, 100, 900, 676),
    'crossing_closed': ('crossing_closed.jpg', 300, 200, 860, 676),
    'station': ('station.jpg', 580, 280, 960, 660),
    'timetabled_trains': ('timetabled_trains.jpg', 300, 36, 940, 676),
    'wagons_left': ('wagons_left.jpg', 120, 180, 820, 676),
    'wagon_loader': ('wagon_loader.jpg', 0, 0, 960, 540),
    'loader_filter': ('loader_filter.png', 0, 0, 368, 346),
    'trains_on_minimap': ('minimap.png', 0, 0, 392, 444),
    'street_meets_track': ('street_meets_track.jpg', 380, 200, 1180, 672),
    'track_layer_slope': ('track_layer_slope.jpg', 240, 120, 1040, 720),
    'track_layer_bridge': ('track_layer_bridge.jpg', 300, 60, 1100, 560),
    'track_layer_tunnel': ('track_layer_tunnel.jpg', 420, 0, 1180, 720),
    'car_ramp': ('car_ramp.jpg', 320, 120, 1080, 672),
    'small_turntable': ('small_turntable.jpg', 330, 120, 930, 600),
    'two_road_shed': ('two_road_shed.jpg', 260, 60, 960, 672),
    'four_aspect_signals': ('four_aspect_signals.jpg', 600, 170, 1100, 420),
    'double_yellow': ('double_yellow.jpg', 100, 0, 600, 360),
    'signal_box_route': ('signal_box_route.jpg', 220, 0, 920, 560),
    'world_map': ('world_map.jpg', 150, 150, 640, 470),
}


def picture(shot, box):
    crop = Image.open(f'{SHOTS}/{shot}').convert('RGB').crop(box)
    w, h = crop.size
    scale = 200.0 / max(w, h)
    small = crop.resize((max(1, round(w * scale)), max(1, round(h * scale))), Image.LANCZOS)
    frame = Image.new('RGB', (200, 200), tuple(int(c) for c in ImageStat.Stat(crop).mean))
    frame.paste(small, ((200 - small.width) // 2, (200 - small.height) // 2))
    texture = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
    texture.paste(frame, (0, 0))
    return texture


def main():
    os.makedirs(OUT, exist_ok=True)
    for old in os.listdir(OUT):
        if old[:-4] not in CROPS:
            os.remove(f'{OUT}/{old}')
    for name, (shot, *box) in CROPS.items():
        # 256 colours is plenty for a picture this size, and a fraction of the file
        picture(shot, tuple(box)).quantize(256, method=Image.Quantize.FASTOCTREE).save(f'{OUT}/{name}.png', optimize=True)
    names = sorted(CROPS)
    sheet = Image.new('RGB', (6 * 204, ((len(names) + 5) // 6) * 204), (40, 40, 40))
    for i, name in enumerate(names):
        sheet.paste(Image.open(f'{OUT}/{name}.png').convert('RGB').crop((0, 0, 200, 200)), ((i % 6) * 204 + 2, (i // 6) * 204 + 2))
    os.makedirs(f'{REPO}/build', exist_ok=True)
    sheet.save(f'{REPO}/build/guide-pictures.png')  # all of them at a glance
    print(len(CROPS), 'pictures')


if __name__ == '__main__':
    main()
