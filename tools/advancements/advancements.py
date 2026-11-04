"""Writes the mod's advancements (data/reikotrains/advancement/*.json) and their names in en_us.json.

Every advancement has one criterion, the mod's own trigger reikotrains:train, with the event it waits for (and, for the
ones that count, the least it has to reach). The events are fired by com.reiko.trains.advancement.TrainAdvancements.

Run from the repository root: python3 tools/advancements/advancements.py
"""
import collections
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), '..', '..')
DATA = os.path.join(ROOT, 'src/main/resources/data/reikotrains/advancement')
LANG = os.path.join(ROOT, 'src/main/resources/assets/reikotrains/lang/en_us.json')

# (id, parent, icon, title, description, event, min, frame, experience)
ADVANCEMENTS = [
    ('root', None, 'reikotrains:steam_locomotive', "Reiko's Trains", 'Put a locomotive, coach or wagon on the track', 'place_car', 0, 'task', 0),

    # Driving
    ('full_steam_ahead', 'root', 'reikotrains:express_locomotive', 'Full Steam Ahead', 'Open the throttle of a locomotive', 'drive', 0, 'task', 0),
    ('ninety', 'full_steam_ahead', 'reikotrains:bullet_train', 'The Ninety', 'Drive a train at 90 km/h or more', 'speed', 90, 'challenge', 50),
    ('ding_ding', 'full_steam_ahead', 'reikotrains:vintage_tram', 'Ding Ding', 'Drive a tram', 'tram', 0, 'task', 0),
    ('under_the_wires', 'full_steam_ahead', 'reikotrains:catenary_mast', 'Under the Wires', 'Drive an electric that draws its power from overhead wire',
     'wire', 0, 'task', 0),

    # Making up trains
    ('coupled_up', 'root', 'reikotrains:coupler', 'Coupled Up', 'Couple two train cars together', 'couple', 0, 'task', 0),
    ('car_carrier', 'coupled_up', 'reikotrains:car_transporter', 'Car Carrier', 'Drive a car aboard a car transporter', 'car_aboard', 0, 'task', 0),

    # Running a railway
    ('right_away', 'root', 'reikotrains:timetable', 'Right Away', 'Set a train going on a timetable', 'timetable', 0, 'task', 0),
    ('signalman', 'right_away', 'reikotrains:signal_box', 'Signalman', 'Set a route from a signal box', 'route', 0, 'task', 0),

    # The depot
    ('round_we_go', 'root', 'reikotrains:turntable', 'Round We Go', 'Turn a train on a turntable', 'turntable', 0, 'task', 0),
    ('topped_up', 'round_we_go', 'reikotrains:engine_shed', 'Topped Up', "Have an engine shed top up the engine you're driving", 'shed', 0, 'task', 0),

    # Building the line
    ('navvy', 'root', 'reikotrains:track_layer', 'Navvy', 'Lay 64 pieces of track with a track-laying wagon, driving it', 'lay', 64, 'task', 0),
    ('over_the_river', 'navvy', 'minecraft:stone_bricks', 'Over the River', 'Have a track-laying wagon build a bridge', 'bridge', 0, 'goal', 0),
]


def advancement(entry):
    aid, parent, icon, title, description, event, least, frame, experience = entry
    conditions = {'event': event}
    if least:
        conditions['min'] = least
    display = {
        'icon': {'id': icon},
        'title': {'translate': f'advancements.reikotrains.{aid}.title'},
        'description': {'translate': f'advancements.reikotrains.{aid}.description'},
        'frame': frame,
        'show_toast': parent is not None,
        'announce_to_chat': parent is not None,
        'hidden': False,
    }
    if parent is None:
        display['background'] = 'reikotrains:textures/block/track_sleeper.png'
    out = {}
    if parent is not None:
        out['parent'] = f'reikotrains:{parent}'
    out['display'] = display
    out['criteria'] = {event: {'trigger': 'reikotrains:train', 'conditions': conditions}}
    out['requirements'] = [[event]]
    if experience:
        out['rewards'] = {'experience': experience}
    return out


def main():
    os.makedirs(DATA, exist_ok=True)
    for name in os.listdir(DATA):
        if name.endswith('.json'):
            os.remove(os.path.join(DATA, name))
    for entry in ADVANCEMENTS:
        with open(os.path.join(DATA, entry[0] + '.json'), 'w') as out:
            json.dump(advancement(entry), out, indent=2)
            out.write('\n')
    lang = json.load(open(LANG), object_pairs_hook=collections.OrderedDict)
    for key in [k for k in lang if k.startswith('advancements.reikotrains.')]:
        del lang[key]
    for aid, _, _, title, description, *_ in ADVANCEMENTS:
        lang[f'advancements.reikotrains.{aid}.title'] = title
        lang[f'advancements.reikotrains.{aid}.description'] = description
    with open(LANG, 'w') as out:
        json.dump(lang, out, indent=2, ensure_ascii=False)
        out.write('\n')
    print(f'{len(ADVANCEMENTS)} advancements written')


if __name__ == '__main__':
    main()
