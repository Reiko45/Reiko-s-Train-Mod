"""Writes the Railway Handbook (a Patchouli book) into the mod: python3 tools/guidebook/book.py

Book: data/reikotrains/patchouli_books/railway_handbook/book.json
Contents: assets/reikotrains/patchouli_books/railway_handbook/en_us/{categories,entries}/...
Its pictures are cropped from the README's screenshots by tools/guidebook/pictures.py.
"""
import json, os, re, shutil, sys

sys.dont_write_bytecode = True  # importing pictures.py leaves no __pycache__ in tools/

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = REPO + '/src/main/resources'
MOD = 'reikotrains'
BOOK = 'railway_handbook'
OUT = f'{RES}/assets/{MOD}/patchouli_books/{BOOK}/en_us'
LANG = json.load(open(f'{RES}/assets/{MOD}/lang/en_us.json'))
RECIPES = {f[:-5] for f in os.listdir(f'{RES}/data/{MOD}/recipe')}
IMAGES = set()


def wide(title):
    """About how wide a title is, in screenshot pixels; a page is 232."""
    return sum(6 if c in "il.,'!:;|" else 8 if c in ' tIfk' else 11.5 for c in title)


def fits(title):
    return wide(title) <= 228


def name(item):
    return LANG.get(f'item.{MOD}.{item}') or LANG.get(f'block.{MOD}.{item}') or item


def output(recipe):
    """The name of what a recipe makes, as a crafting page titles it; None if it isn't one of ours."""
    result = json.load(open(f'{RES}/data/{MOD}/recipe/{recipe}.json')).get('result', {}).get('id', '')
    return name(result.split(':')[1]) if result.startswith(MOD + ':') else None


def I(item, plural=''):
    """An item's name, highlighted."""
    return f'$(item){name(item)}{plural}$()'


def K(key):
    """A key binding, as the player has it set."""
    return f'$(k:{key if "." in key else "key." + MOD + "." + key})'


def L(entry, text):
    return f'$(l:{entry}){text}$(/l)'


# ---- pages ----

def text(t, title=None):
    p = {'type': 'patchouli:text', 'text': t}
    if title:
        p['title'] = title
    return p


def craft(recipe, recipe2=None, t=None, title=None):
    for r in (recipe, recipe2):
        if r and r not in RECIPES:
            raise SystemExit('no recipe ' + r)
        if r and not title and output(r) and not fits(output(r)):
            raise SystemExit(f'{output(r)}: too wide for a page title')
    p = {'type': 'patchouli:crafting', 'recipe': f'{MOD}:{recipe}'}
    if recipe2:
        if t:
            raise SystemExit(f'{recipe}/{recipe2}: a page with two recipes shows no text')
        p['recipe2'] = f'{MOD}:{recipe2}'
    if t:
        p['text'] = t
    if title:
        p['title'] = title
    return p


def spot(item, t, title=None):
    p = {'type': 'patchouli:spotlight', 'item': f'{MOD}:{item}', 'text': t}
    if title:
        p['title'] = title
    return p


def image(img, t=None, title=None):
    imgs = img if isinstance(img, list) else [img]
    IMAGES.update(imgs)
    p = {'type': 'patchouli:image', 'images': [f'{MOD}:textures/gui/guide/{i}.png' for i in imgs], 'border': True}
    if t:
        p['text'] = t
    if title:
        p['title'] = title
    return p


# The cars' real sizes (length, height in blocks), from their classes: Patchouli scales an entity by its hitbox (the
# middle section only, 1.8 x 2.2), so each is scaled to fit its whole length on the page.
SIZES = {}
for cls, eid in (('SteamLocomotive', 'steam_locomotive'), ('ExpressLocomotive', 'express_locomotive'), ('Tender', 'tender'),
                 ('DieselLocomotive', 'diesel_locomotive'), ('ElectricLocomotive', 'electric_locomotive'),
                 ('BulletTrain', 'bullet_train'), ('BulletCoach', 'bullet_coach'), ('VintageTram', 'vintage_tram'),
                 ('PassengerCoach', 'passenger_coach'), ('FreightWagon', 'freight_wagon'), ('TankWagon', 'tank_wagon'),
                 ('LivestockWagon', 'livestock_wagon'), ('CarTransporter', 'car_transporter'), ('GuardsVan', 'guards_van'),
                 ('TrackLayer', 'track_layer')):
    src = open(f'{REPO}/src/main/java/com/reiko/trains/train/{cls}.java').read()
    half = float(re.search(r'double halfLength\(\) \{\s*return ([\d.]+);', src).group(1))
    height = float(re.search(r'float partHeight\(\) \{\s*return ([\d.]+)F;', src).group(1))
    SIZES[eid] = (half * 2, height)


def entity(eid, t, name_=None):
    length, height = SIZES[eid]
    box_w, box_h = 1.8, 2.2
    size = max(1.0, box_w, box_h)
    default = 100.0 / size * 0.8
    target = 82.0 / max(length, height * 1.25, 1.0)
    offset = height / 2.0 - max(box_h, size) / 2.0
    p = {'type': 'patchouli:entity', 'entity': f'{MOD}:{eid}', 'scale': round(target / default, 3), 'offset': round(offset, 3),
         'rotate': True, 'text': t}
    if name_:
        p['name'] = name_
    return p


# ---- the book ----

CATEGORIES = []   # (id, name, description, icon, sortnum)
ENTRIES = []      # (category, id, name, icon, sortnum, pages, priority)


def category(cid, title, desc, icon, sort):
    CATEGORIES.append((cid, title, desc, icon, sort))


def entry(cat, eid, title, icon, pages, sort=0, priority=False):
    ENTRIES.append((cat, eid, title, icon if ':' in icon else f'{MOD}:{icon}', sort, pages, priority))


category('getting_started', 'Getting Started', 'What the mod is, and laying a railway and running your first train on it.', f'{MOD}:steam_locomotive', 0)
category('track', 'Track', 'Laying track: straights, curves, ramps and junctions, street track, and the track-laying wagon.', f'{MOD}:track', 1)
category('driving', 'Driving', 'Putting cars on the track, coupling them into trains, and driving: the controls and the gauges.', f'{MOD}:coupler', 2)
category('locomotives', 'Locomotives', 'The steam engines, the diesel, the electric and the Bullet Train: what each one runs on.', f'{MOD}:express_locomotive', 3)
category('wagons', 'Coaches and Wagons', 'Coaches for passengers, wagons for goods, fluids, animals and cars, and the guard\'s van.', f'{MOD}:freight_wagon', 4)
category('depots', 'Engine Depots', 'Water towers, charging points, turntables and engine sheds: keeping engines going.', f'{MOD}:water_tower', 5)
category('electric', 'Wires and Trams', 'Electrifying a line with overhead wires, and the trams that run under them in the street.', f'{MOD}:catenary_mast', 6)
category('signals', 'Signalling', 'Signals, the signal box, speed limits, switches, track sensors and level crossings.', f'{MOD}:signal', 7)
category('stations', 'Stations', 'Naming stations, and timetables that run trains by themselves, leaving and picking up wagons.', f'{MOD}:station', 8)
category('freight', 'Freight', 'Wagon loaders: loading and unloading goods, fluids and animals by themselves, and their filters.', f'{MOD}:wagon_loader', 9)
category('more', 'Settings and More', 'Trains on the minimap, JEI, and the server\'s settings.', 'minecraft:compass', 10)

# -- Getting started --

entry('getting_started', 'welcome', 'Welcome', 'steam_locomotive', [
    text('$(l)Reiko\'s Train Mod$() adds big trains, two blocks wide and three tall, on their own track: steam engines, a diesel, '
         'an electric, the Bullet Train and trams, with coaches and wagons for everything.$(br2)Signal the line, name its stations, '
         'and drive the trains yourself or let a timetable run them.'),
    image('welcome', 'A tank engine and its coaches at the station.'),
    text('This book has a chapter for each part of the mod. Start with ' + L('getting_started/first_train', 'Your First Train') + ', '
         'then look things up as you need them.$(br2)The mod\'s screens have a little book button in their corner: it opens this '
         'book at their page.'),
    craft('railway_handbook', None, 'Lost this book? Make another from a book and a piece of Train Track.'),
], sort=0, priority=True)

entry('getting_started', 'first_train', 'Your First Train', 'track', [
    text(f'$(li)Craft {I("track")} (sixteen at a time) and lay a line: blocks in a row join up by themselves ('
         + L('track/laying_track', 'Laying Track') + ').'
         f'$(li)Craft a {I("steam_locomotive")} and click the track with it to put it on the line.'
         f'$(li)Click it with coal (or charcoal, logs...) to fill its firebox. It comes with its water tanks full.'),
    text('$(li)Click it empty-handed to climb into the cab, then press ' + K('key.forward') + ' to open the throttle and '
         + K('key.jump') + ' to brake (' + L('driving/controls', 'Controls') + ').'
         f'$(li)Add a {I("passenger_coach")} with the {I("coupler")} (' + L('driving/making_up', 'Making Up a Train') + ').'
         '$(li)Put up a ' + L('stations/stations', 'station') + ' at each end, and give the train a '
         + L('stations/timetables', 'timetable') + '.'),
    craft('track', None, 'Iron ingots in the corners and wooden slabs across the middle make sixteen.'),
    craft('steam_locomotive', None, 'A furnace minecart in copper and iron.'),
], sort=1)

entry('getting_started', 'crafting', 'Finding Recipes', 'minecraft:crafting_table', [
    text('Every recipe is in this book, on the page about what it makes, and everything is in the $(thing)Reiko\'s Trains$() '
         'creative tab.$(br2)With $(thing)JEI$() installed, each of the mod\'s items has an information page too, and there are '
         'pages for what the locomotives run on and what the track-laying wagon fills dips with (' + L('more/jei', 'JEI') + ').'),
], sort=2)

# -- Track --

entry('track', 'laying_track', 'Laying Track', 'track', [
    text('$(l)Straight track$(): place Train Track on the ground and it runs the way you\'re facing, one of eight ways '
         '(diagonals too). Blocks in a line join up by themselves.$(br2)$(l)Curves and ramps$(): hold track, click a track block to '
         'start from, then click where the line should go: on the ground for a new end, or on another track block to join it.'),
    text('A green outline shows the curve before you click, or a red one, with the reason, if it can\'t be built. A new end faces '
         'the way that makes a smooth curve; $(l)sneak$() as you click it and it faces the way you\'re looking instead, for '
         'S-bends.$(br2)Click the start again, or sneak-click the air, to start over.'),
    image('laying_track', 'Laying a curve: the outline shows where it goes.'),
    text('A curve can reach 48 blocks, can\'t turn tighter than a radius of 5 blocks (8 or more looks best with long cars) or more '
         'than 90° at once, and can\'t be steeper than about 1 in 2.$(br2)It costs a piece of track a block of its length, and '
         'breaking either end gives it back.', 'The Rules'),
    text('$(l)Junctions$(): draw a curve off a block that already has track going on from the same end. Trains go straight on '
         'unless steered, or a ' + L('signals/switches', 'switch stand') + ' sets the way.$(br2)Track needs solid ground under '
         'its blocks, but a curve or ramp crosses gaps between them, so a bridge is just a long curve. Leave three blocks between '
         'parallel lines.'),
    image('junction', 'A junction: the branch curves away to the right.'),
    craft('track'),
], sort=0)

entry('track', 'street_track', 'Street Track', 'street_track', [
    text(f'{I("street_track")} is set into the road: its grooved rails are level with the top of the block, so cars and people go '
         'over it. Place it in place of a road block.$(br2)Draw curves between street track blocks the same way as train '
         'track; click the top of a road block for a new end, and it\'s set into it.'),
    text('Street corners can be as tight as a radius of 3 blocks, and are paid for in street track.$(br2)A street runs straight on '
         'into train track: lay train track on the ground in line with the street track\'s end (a block up from it, as the street '
         'track\'s set into the road) and they join up, near enough level.$(br2)It\'s made for ' + L('electric/trams', 'trams')
         + ', but any train runs on it.'),
    image('street_track', 'Street track round a street corner.'),
    image('street_meets_track', 'The street running on into train track.'),
    craft('street_track', None, 'Three Train Track over stone and coal make six.'),
], sort=1)

entry('track', 'track_layer', 'Track-Laying Wagon', 'track_layer', [
    entity('track_layer', 'A flat wagon that lays track: stacks of it show along its deck.'),
    text('Click it for its store (27 slots; hoppers reach it too). Load it with $(item)Train Track$() and something to fill dips: '
         'stone, cobblestone, dirt or gravel (or deepslate, andesite, tuff, sandstone, netherrack...).$(br2)Couple it at the '
         '$(l)front$() of a train, drive to the end of the line and keep going: it lays track ahead of itself, holding the train to '
         'a walk.'),
    text('It carries the line straight on, diagonals too. $(l)Steer$() left or right (' + K('key.left') + ' / ' + K('key.right')
         + ') and it lays a smooth 45° bend that way, then goes straight on: one bend for each press. Its screen sets the bends '
         '$(l)tight$() (about 10 blocks across) or $(l)wide$() (about 20).$(br2)It $(l)follows the ground$() up and down, at no more '
         'than 1 in 8; or, set to, it keeps the line $(l)level$().'),
    image('track_layer_slope', 'Climbing a rise, at 1 in 8.'),
    text('Hills in the way are dug through, three wide: an open $(l)cutting$() where they\'re low, a $(l)tunnel$() where they\'re tall '
         '(its roof made good where sand or gravel would fall in, and water beside the line walled off). It keeps what it digs.'
         '$(br2)Dips up to six deep are $(l)filled$(), three wide, with stone, dirt or gravel.'),
    image('track_layer_line', 'Over a gully, filled three wide, and round a bend.'),
    text('Deeper gaps, and water, are $(l)bridged$(): a deck under the line and a pier every four blocks down to the bottom (up to '
         '48 deep). Load it with stone bricks or other bricks for them, or it uses fill.$(br2)Off the end of '
         + L('track/street_track', 'street track') + ' it lays street track, with that aboard.'),
    image('track_layer_bridge', 'A bridge on piers, over a pool.'),
    text('With ' + L('electric/overhead_lines', 'masts and overhead wire') + ' aboard, it $(l)wires the line$() too: a mast on its '
         'left every sixteen blocks or so, wire strung from the last, and its cuttings and tunnels dug tall enough for the wire.'
         '$(br2)It stops when it\'s out of track or fill, at water where the track would go, at a gap too deep even for a pier, and '
         'at bedrock, chests or other track right in the way.'),
    image('track_layer_tunnel', 'Into a tall hill in a tunnel, wiring the line.'),
    text('Its screen says why it\'s stopped, and so does a message to the driver.$(br2)The switch in its screen turns it off: then '
         'it\'s an ordinary wagon. The buttons under it set the slopes (following the ground, or level) and the bends (tight or '
         'wide).'),
    image('track_layer_screen', 'Its screen: the store, the switch, what it\'s doing, and how it lays.'),
    craft('track_layer'),
], sort=2)

# -- Driving --

entry('driving', 'making_up', 'Making Up a Train', 'coupler', [
    text('$(li)Click the track (a track block, or along a curve) with a locomotive, coach or wagon to put it there, facing the '
         'way you\'re looking.'
         f'$(li)Take the {I("coupler")}, click one car, then the next car along (up to 5 blocks between buffers): they\'re drawn '
         'together.$(li)Sneak-click a car with the coupler to uncouple it.'),
    text('Near the end of a line, a car goes as near where you clicked as it fits.$(br2)'
         'Cars couple at either end, facing either way, and the locomotives can be anywhere in the train: they pull or push. A '
         'train can have up to 16 cars (a server setting).$(br2)Break a car (hit it a few times) to pick it up again; what it '
         'carries spills out.'),
    craft('coupler', None, 'Iron ingot, chain, iron ingot, in a column.'),
    text('Trains are solid: you can\'t walk through one, and you can stand on its roof (but get in to ride: a moving train slides '
         'out from under anyone standing on it). Cars from Reiko\'s Car Mod bump into trains and trams too.'),
    text('A train can have several locomotives, anywhere in it and facing either way: double-head a heavy train, or put a banking '
         'engine at the back to push.$(br2)One is in charge: the one on a timetable, or else the one being driven. The others work '
         'with it, notched up the same, each adding its pull up to its own top speed, and each burning its own fuel.',
         'More Than One Engine'),
], sort=0)

entry('driving', 'controls', 'Controls', 'steam_locomotive', [
    text('Click a locomotive empty-handed to get in.$(br)'
         '$(li)' + K('key.forward') + ': throttle up a notch$(li)' + K('key.back') + ': throttle down, through neutral into reverse'
         '$(li)' + K('key.jump') + ': brake, while held$(li)' + K('key.left') + ' / ' + K('key.right') + ': hold to take the left or '
         'right way at junctions$(li)' + K('whistle') + ': whistle or horn$(li)' + K('key.sneak') + ': get out'),
    text('With nobody at the controls the handbrake holds the train, and the fire only burns while someone\'s driving.$(br2)'
         'A heavy train takes longer to get going and slows uphill; cars roll downhill on their own. At the end of the line a '
         'train stops dead. If the track under a car is broken, the car comes off and drops as an item.'),
    image('cab', 'The gauges: notch, speed, fuel and water.'),
    text('The gauges show the next signal and how far it is, the next switch and which way it\'s set, the next station, the '
         'throttle notch (R2, R1, N, 1 to 4), the speed and the speed limit, which way you\'re steering, the brake, and the fuel '
         '(and a steam engine\'s water).$(br2)A moving train keeps the world round it loaded.', 'The Gauges'),
], sort=1)

# -- Locomotives --

entry('locomotives', 'steam_locomotive', 'Steam Locomotive', 'steam_locomotive', [
    entity('steam_locomotive', 'A tank engine: 43 km/h flat out, seats for a driver and a fireman.'),
    text('Click it with fuel to fill its firebox: coal, charcoal, logs, blocks of coal, blaze rods, dried kelp blocks or a bucket '
         'of lava. A piece of coal lasts about three minutes at full throttle.$(br2)It needs $(l)water$() too: its side tanks '
         'hold 16 buckets, and it uses them as fast as it burns coal. With none left there\'s no steam, and the gauges say NO WATER.'),
    text('Fill it with buckets of water (or any mod\'s fluid container), pipes, or at a ' + L('depots/water_tower', 'water tower') + '. '
         'It comes from the works with its tanks full.$(br2)Its driving wheels and coupling rods turn as it goes, it chuffs and '
         'puffs smoke, and it whistles.'),
    craft('steam_locomotive'),
], sort=0)

entry('locomotives', 'express', 'Express and Tender', 'express_locomotive', [
    entity('express_locomotive', 'The express: eight blocks long, 58 km/h, nearly twice as strong as the tank engine.'),
    text(f'The express carries no coal or water itself: couple a {I("tender")} to it (at either end) and it uses the tender\'s. A '
         'tender holds 128 coal\'s worth and 64 buckets of water, and its coal heap goes down as it\'s used.$(br2)Click the engine '
         'or the tender with fuel to fill the tender. Without one the express won\'t go: its gauges say NO TENDER.'),
    text('Hoppers and wagon loaders fill tenders too, with fuel only, so a train on a timetable can take on coal at a station with a '
         + L('freight/wagon_loader', 'wagon loader') + '. Click the tender empty-handed to see how full it is.'),
    entity('tender', 'The tender: coal at the front, the water tank behind.'),
    craft('express_locomotive', 'tender'),
], sort=1)

entry('locomotives', 'diesel', 'Diesel Locomotive', 'diesel_locomotive', [
    entity('diesel_locomotive', 'A road-switcher on two bogies: 52 km/h, and it pulls harder than the steam engines.'),
    text('It runs on $(thing)diesel$(): BuildCraft Reborn\'s $(l)Dense Fuel$() or $(l)Mixed Heavy Fuels$(), or other mods\' diesel. Its '
         'tank holds 16 buckets of Dense Fuel: a bucket lasts about five minutes at full throttle, nearly an hour idling.$(br2)Fill it '
         'with buckets or pipes, or at a ' + L('freight/wagon_loader', 'wagon loader') + ' with a tank of diesel beside it.'),
    text('It won\'t take coal, and steam engines won\'t take diesel. Its engine rumbles, rising with the throttle, its exhaust smokes '
         'harder the harder it works, and it sounds a two-tone horn.'),
    craft('diesel_locomotive'),
], sort=2)

entry('locomotives', 'electric', 'Electric Locomotive', 'electric_locomotive', [
    entity('electric_locomotive', 'A box-cab electric: 60 km/h, the strongest engine there is.'),
    text('It runs on $(thing)BuildCraft power$() (MJ) from its batteries: a million MJ, about twenty minutes at full power. Standing '
         'still it uses next to nothing. When they\'re flat it stops, and the gauges say FLAT.$(br2)Charge it at a '
         + L('depots/charging_point', 'charging point') + ', or run it under ' + L('electric/overhead_lines', 'overhead wires') + ', '
         'which charge it as it goes.'),
    text('Drive it from the front cab. Its motors hum, the whine climbing as it gathers speed; working hard, the odd spark flies from '
         'under it. It has a two-tone horn, and won\'t take coal or diesel.'),
    craft('electric_locomotive'),
], sort=3)

entry('locomotives', 'bullet_train', 'Bullet Train', 'bullet_train', [
    entity('bullet_train', 'A driving car: about 100 km/h, with seats for eight behind the cab.'),
    text(f'The Bullet Train is a set: a $(l)driving car$() at each end, noses out, with {I("bullet_coach", "es")} between them (as '
         'many as the train length allows). Drive it from either end: climb into the cab at the end you want to lead. On a '
         'timetable it shuttles back and forth without turning.'),
    text('Both driving cars pull and run on batteries (1,500,000 MJ each), charged at a ' + L('depots/charging_point', 'charging point')
         + '; under ' + L('electric/overhead_lines', 'overhead wires') + ' both raise their pantographs and run from the wire.$(br2)'
         'Signals, stations and speed signs look further ahead for it, so it slows in time at full speed.'),
    image('bullet_train', 'The Bullet Train standing at the station.'),
    entity('bullet_coach', 'A coach: twelve seats.'),
    craft('bullet_train', 'bullet_coach'),
], sort=4)

# -- Coaches and wagons --

entry('wagons', 'passenger_coach', 'Passenger Coach', 'passenger_coach', [
    entity('passenger_coach', 'Six and a half blocks long: eight seats in four rows.'),
    text('Click a coach to sit in it: passengers ride along with the train, and see the station names as they go. Sneak to get out.'
         '$(br2)Coaches ride on two bogies that swing under them, so long cars go round curves like the real thing.'),
    craft('passenger_coach'),
], sort=0)

entry('wagons', 'freight_wagon', 'Freight Wagon', 'freight_wagon', [
    entity('freight_wagon', 'Crates pile up in it as it fills.'),
    text('A freight wagon holds 108 stacks, four chests\' worth, all on one screen: click it to open it. Hoppers above or below it fill '
         'and empty it, and so do other mods\' pipes.$(br2)A full wagon is heavier to pull. Breaking it spills what\'s in it.$(br2)For '
         'loading by itself at a station, see ' + L('freight/wagon_loader', 'Wagon Loaders') + '.'),
    craft('freight_wagon'),
], sort=1)

entry('wagons', 'tank_wagon', 'Tank Wagon', 'tank_wagon', [
    entity('tank_wagon', 'The gauge on its side lights a quarter at a time as it fills.'),
    text('It carries 256 buckets of one fluid: water, lava, oil, BuildCraft\'s fuels. Click it with a full bucket (or any mod\'s fluid '
         'container) to pour it in, or with an empty one to draw some off; empty-handed, to see what\'s in it.$(br2)Pipes fill and '
         'empty it, and so does a ' + L('freight/wagon_loader', 'wagon loader') + ' with tanks beside it. If it\'s broken up, what\'s '
         'in it is lost.'),
    text('A tank wagon of $(l)water$() in a steam train keeps its engines topped up as it goes: the tank engines and tenders in the '
         'same train are filled from it, so a long run needs no water tower.'),
    craft('tank_wagon'),
], sort=2)

entry('wagons', 'livestock_wagon', 'Livestock Wagon', 'livestock_wagon', [
    entity('livestock_wagon', 'Six stalls behind slatted sides.'),
    text('It carries six animals: cows, sheep, pigs, chickens, goats, horses, llamas... (not tamed pets).$(br2)$(li)$(l)Lead$() '
         'animals up to it and click it: as many as there\'s room for go in, and you get the leads back.$(li)$(l)Sneak-click$() it '
         'empty-handed to let them all out beside you.$(br2)Aboard, they come to no harm. People can\'t ride in it.'),
    text('A ' + L('freight/wagon_loader', 'wagon loader') + ' works with it too: loading, it puts the animals standing around it '
         'into the train\'s livestock wagons; unloading, it lets them out behind it. So a timetabled train can take the sheep from the '
         'farm to market by itself.'),
    image('livestock_wagon', 'Loaded from the pen behind the wagon loader.'),
    craft('livestock_wagon'),
], sort=3)

entry('wagons', 'car_transporter', 'Car Transporter', 'car_transporter', [
    entity('car_transporter', 'Twelve blocks of deck: three cars, or a bus.'),
    text('It carries the cars, vans, trucks and buses of $(thing)Reiko\'s Car Mod$() (without that mod it carries nothing).'
         '$(br2)$(l)Driving on$(): with the train stopped, drive up to a free end, lined up with it, and keep going. The car rolls '
         'aboard to the far end, or up to the cars already there; a train of them fills from the far end.'),
    text('$(l)Driving off$(): sit in the car with the train stopped and keep going forwards or backwards: off the end of the train '
         'that way, or on to the next transporter.$(br2)Aboard, a car stays put: it doesn\'t drive, burn fuel or wear. It won\'t take '
         'one that doesn\'t fit. Clicking it says how many cars are aboard.'),
    image('cars_by_rail', 'Cars by rail, on the way to the docks.'),
    text('$(l)Trailers$(): a pickup or SUV towing a camper or a livestock trailer drives on with it (forwards: no backing a trailer '
         'on), if there\'s room for both. Aboard, the car mod\'s hitch key (' + K('key.reikocars.hitch') + ') unhitches the trailer, '
         'leaving it on the deck, and hitches it up again.'),
    text(f'$(l)Car Ramps$(): put a row of {I("car_ramp", "s")} beside the track, rising towards it, for a loading dock. A car on '
         'the ramp, lined up with a standing car transporter beside it, drives aboard sideways when you keep driving, to the nearest '
         'place with room; and a car aboard beside a ramp drives off onto it.'),
    image('car_ramp', 'A pickup and its camper aboard, beside a row of ramps.'),
    craft('car_transporter'),
    craft('car_ramp', None, 'Makes four.'),
], sort=4)

entry('wagons', 'guards_van', 'Guard\'s Van', 'guards_van', [
    entity('guards_van', 'Tail lamps on its back end, and a veranda on its front.'),
    text('The guard\'s van goes on the back of the train. It seats two, by the stove (which smokes while someone\'s aboard) and by '
         'the window.$(br2)Its brake works with the train\'s: with a van on, a train\'s brakes are half as strong again (two vans at '
         'most), so a heavy train stops quicker. And a train left with no engine is held by the van\'s handbrake.'),
    craft('guards_van'),
], sort=5)

# -- Engine depots --

entry('depots', 'water_tower', 'Water Tower', 'water_tower', [
    text(f'Put a {I("water_tower")} beside the track (up to three blocks away; its spout faces the track). It holds 256 buckets: '
         'fill it with buckets of water, or pipes.$(br2)Any tank engine or tender in a train standing beside it is filled, its '
         'spout swinging down over the track. A timetabled train at a station there waits until it\'s full.'),
    image('water_tower', 'A tender taking water.'),
    text('The marks on its gauge show how full it is, a comparator reads it, redstone holds the spout up, and clicking it says how '
         'much it holds. An empty bucket draws water from it.$(br2)A server can turn water off (' + L('more/settings', 'settings') + ').'),
    craft('water_tower'),
], sort=0)

entry('depots', 'charging_point', 'Charging Point', 'charging_point', [
    text(f'A {I("charging_point")} charges the electrics and Bullet Trains standing beside it (up to three blocks from the track; its '
         'cable reaches out over it). Feed it from BuildCraft engines or kinesis pipes: it stores a million MJ.$(br2)'
         'It charges a flat electric in ten seconds, and a timetabled train waits until it\'s full.'),
    image('electric_charging', 'Its bars show its store; its lamp lights while it\'s charging.'),
    text('Click it to see how much it holds. Redstone pauses it, and a comparator reads its store.$(br2)On a long line, '
         + L('electric/overhead_lines', 'overhead wires') + ' keep trains charged as they go instead.'),
    craft('charging_point'),
], sort=1)

entry('depots', 'turntable', 'Turntable', 'turntable', [
    text(f'A {I("turntable")} turns locomotives round, or sends them off down whichever track you like. Use it on flat, solid ground: '
         'it digs its own pit, 15 blocks across, centred on the block you click, with its bridge pointing the way you\'re looking. '
         'Breaking any of it gives it back (the pit stays dug).'),
    text('Lay straight track off the pit\'s edge in line with its middle (from 8 blocks out, or 6 on a diagonal). Lined up with track, '
         'the bridge is track.$(br2)$(l)Click$() it to turn the bridge to the next track round, clockwise; sneak-click for the other '
         'way. Redstone at the pit\'s edge turns it too, and a comparator reads which way it points.'),
    image('turntable_turning', 'Turning the express round.'),
    text('A train standing all on the bridge goes round with it. It won\'t turn with a train half on it.$(br2)Put a ' + L('stations/stations', 'station')
         + ' by its far end at the end of a line: a timetabled train that stops there, and would have to back out, is turned round '
         'while it waits instead, so steam engines always go chimney first.'),
    text('$(l)Timetabled trains$() turn it themselves: coming up to it with their stop off another of its tracks, they turn the '
         'bridge to them (once nothing\'s on it), drive on, and turn it again to the track that\'s the shortest way there, engine '
         f'first.$(br2)The {I("small_turntable")} is the same in a pit 11 blocks across: room for a tank engine, a diesel or a tram. '
         'Its track runs off 6 blocks out, or 4 on a diagonal.'),
    image('small_turntable', 'The small turntable, with a tank engine on it.'),
    craft('turntable', 'small_turntable'),
], sort=2)

entry('depots', 'engine_shed', 'Engine Shed Doors', 'engine_shed', [
    text(f'{I("engine_shed")} go on the end of a shed you build round them. Use them on straight track from outside: a frame five '
         'blocks across and five high goes up over the track, the doors facing you.$(br2)They open by themselves as a train comes up, '
         'from either side, and shut once it\'s gone by. Click them, or use redstone, to hold them open.'),
    image('engine_shed', 'A tank engine going into its shed.'),
    text('$(l)Servicing$(): the doors keep a store of fuel (nine stacks), 64 buckets each of water and diesel, and BuildCraft power. '
         'Every engine and tender standing still inside, up to 32 blocks in, is topped up from it. A timetabled train with a station '
         'inside waits until it\'s done.$(br2)Fill the store by hand, or with hoppers and pipes. Clicking the doors says what\'s in it.'),
    text('$(l)More roads$(): put more doors beside them, facing the same way, on a track four blocks over (they share a post) or '
         'five. Doors side by side make one shed of several roads: one store for them all, whichever doors you fill, and each set '
         'tops up the engines on its own road.'),
    image('two_road_shed', 'A two-road shed: one set of doors open.'),
    craft('engine_shed'),
], sort=3)

# -- Wires and trams --

entry('electric', 'overhead_lines', 'Overhead Lines', 'catenary_mast', [
    text(f'$(l)Masts$(): put an {I("catenary_mast")} beside the track, up to three blocks from it. Its arm reaches over the track to '
         'hold the wire there, up to 32 blocks apart (closer on curves).$(br2)$(l)Wire$(): '
         f'hold {I("overhead_wire")}, click a mast, then the next one along, and so on; click the same mast twice to stop. Wire won\'t run '
         'through blocks.'),
    text('A mast holds up to four spans, so lines branch at junctions. Sneak-click a mast with wire to take its spans down.'
         f'$(br2)$(l)Power$(): put a {I("substation")} within four blocks of one of the line\'s masts and feed it like a charging point. '
         'Each substation gives the line room for a million MJ. Its bars show the line\'s store, and its lamp lights while it\'s live.'),
    image('overhead_line', 'An electrified line round a curve.'),
    text('An electric, a Bullet Train or a tram under a live wire raises its pantograph (or trolley pole) and runs from the line, '
         'charging its batteries as it goes; the fuel gauge reads Wire. Past the end of the wire, or under a dead one, it runs on its '
         'batteries again.'),
    image('pantograph', 'Pantograph up against the contact wire.'),
    craft('catenary_mast', 'overhead_wire'),
    craft('substation'),
], sort=0)

entry('electric', 'trams', 'Trams', 'vintage_tram', [
    entity('vintage_tram', 'The vintage tram: 40 km/h, nine seats, a controller at each end.'),
    text('Two trams, made for ' + L('track/street_track', 'street track') + ' and ' + L('electric/overhead_lines', 'overhead wires')
         + '. Both run from the wire, with a small battery for junctions and gaps (or charge at a charging point), and ring a bell.'
         '$(br2)The $(l)Vintage Tram$() runs both ways: get in as the driver at the end you want to lead. Its trolley pole trails behind '
         'it to the wire.'),
    text('The $(l)Low-Floor Tram$() is three sections that bend round corners, about fourteen blocks in all: click the track with it '
         'to put all three on together, and breaking any of them takes the whole tram up. Drive it from either end: about 50 km/h, '
         'fourteen seats.'),
    image('low_floor_tram', 'The low-floor tram round a street corner.'),
    text('A tram stops a block or so short of anything on the track ahead, people, animals, cars or other trains, '
         'and waits for them to clear, ringing its bell; animals that won\'t move are nudged off after a few seconds. With Reiko\'s '
         'Car Mod it stops at red traffic lights beside the line that face it, too. It brakes harder than a train.', 'Street Rules'),
    craft('vintage_tram', 'low_floor_tram'),
], sort=1)

# -- Signalling --

entry('signals', 'signals', 'Railway Signals', 'signal', [
    text(f'Put a {I("signal")} beside the track (up to three blocks away), looking along the line the way trains will go. It guards '
         'the track ahead that way, up to the next signal facing the same way, down every branch.$(br2)It shows $(l)red$() while '
         'there\'s a train on that stretch, and $(l)green$() when it\'s clear.'),
    text('Trains stop short of a red signal and go on when it turns green: trains on a timetable always, and trains you drive too (a '
         'server setting). The gauges show the next signal and how far it is.$(br2)Redstone holds a signal at red, and a comparator '
         'reads 15 while there\'s a train ahead. Signals also tell timetabled trains which way a loop runs.'),
    image('signals', 'Signals round the loop: red behind a train, green ahead.'),
    craft('signal', None, 'Makes two.'),
], sort=0)

entry('signals', 'three_light_signals', 'Three-Light Signals', 'three_light_signal', [
    text(f'A {I("three_light_signal")} guards its section like any signal, and shows $(l)yellow$() while it\'s clear but the next '
         'signal on is red. Trains pass a yellow at $(l)caution speed$() (40 km/h, a server setting) and keep to it until the next '
         'signal, slowing in time; they speed up again if it turns green.$(br2)A comparator reads 15, 7 or 0 for red, yellow or green.'),
    image('three_light_signals', 'Green, yellow and red, with a train beyond the red.'),
    text('Mix them with two-light signals as you like: a three-light signal looks at whatever kind of signal comes next. Put them '
         'where trains need warning: before a station\'s platform signal, or on a fast main line.$(br2)The gauges show a yellow ahead, '
         'and caution speed as the speed limit.'),
    craft('three_light_signal'),
], sort=1)

entry('signals', 'four_aspect_signals', 'Four-Aspect Signals', 'four_aspect_signal', [
    text(f'A {I("four_aspect_signal")} is a three-light signal with a second yellow lamp: it shows $(l)double yellow$() while the '
         'next signal on is yellow, as well as yellow while it\'s red. Trains pass a double yellow at no more than 80 km/h (a server '
         'setting), then the yellow at caution speed, ready to stop.$(br2)For fast lines, where trains need more warning.'),
    image(['four_aspect_signals', 'double_yellow'], 'Double yellow, yellow and red, a train beyond the red; and double yellow close up.'),
    text('The gauges show two yellow lamps ahead. A comparator reads 15, 7, 3 or 0 for red, yellow, double yellow or green.$(br2)Trains '
         'remember the caution they passed, through saving the world too.'),
    craft('four_aspect_signal'),
], sort=2)

entry('signals', 'signal_box', 'Signal Box', 'signal_box', [
    text(f'Put a {I("signal_box")} wherever you like (build the box round it) and click it: its screen is a live map of the railway '
         'within 96 blocks, north up: the track, turntables, stations, every train car as it moves, each signal\'s lamp and the way '
         'each switch is set. Hover over anything to see what it is.'),
    text('$(li)Click a $(l)switch$() on the map to throw it to its next way (not if redstone holds it).$(li)Click a $(l)signal$() to '
         'hold it at red, so trains stop at it, and again to let it go. A held signal has a white ring round its lamp.$(br2)Drag the map '
         'to move it; scroll to zoom. Breaking the box lets go of every signal it held.'),
    image('signal_box', 'The map: the loop and its trains, the main line and its junction.'),
    text('$(l)Routes$(): click Routes, then a signal, then the next signal along: the switches between are set and held the way '
         'the route goes (in blue) until a train has gone through. Click the first signal again to cancel it.$(br2)Click a '
         '$(l)turntable$() on the map to turn it to its next track.'),
    image('signal_box_route', 'A route set round the loop.'),
    text('The box remembers the track it\'s seen: track out of the loaded world is still drawn, greyed. A comparator by the box '
         'reads how many trains are on its map.'),
    craft('signal_box'),
], sort=3)

entry('signals', 'speed_limits', 'Speed Limits', 'speed_sign', [
    text(f'Put a {I("speed_sign")} beside the track (up to three blocks away), its face towards the trains coming. $(l)Click$() it to '
         'change its limit: 10 to 90 km/h, or the end of a limit.$(br2)Trains going towards its face slow in time to pass it at '
         'the limit, and keep to it until the next sign their way. Trains going the other way take no notice.'),
    image('speed_sign', 'A timetabled train slowing for a 20 km/h sign.'),
    text('Timetabled trains always keep to the limits, and driven ones too (a server setting). The gauges show the limit beside the '
         'speed, yellow while slowing for a lower one.$(br2)Use them for sharp curves, junctions, stations trains run through, or a '
         'level crossing in a village.'),
    craft('speed_sign', None, 'Makes four.'),
], sort=4)

entry('signals', 'switches', 'Switches', 'switch_stand', [
    text(f'Put a {I("switch_stand")} within three blocks of the block where a line divides: trains heading for the fork go the '
         'way it\'s set.$(li)$(l)Normal$() (green target): the straightest way.$(li)$(l)Thrown$() (yellow): the branch.$(br2)Click '
         'it to throw it over or back; at a three-way junction clicks go round the ways.'),
    text('$(l)Redstone$() sets it and holds it there. Sneak-click the stand to choose what redstone does: throw it, or hold it straight '
         'on. The marker on its post shows which (yellow, it throws; green, it holds) and lights while redstone holds it.$(br2)Trains '
         'coming out of either branch just run through.'),
    image('switch_branch', 'A timetabled train sets the switch for the branch.'),
    text('Timetabled trains set an unheld switch their way as they come to it; a held one they obey. Drivers\' steering only counts '
         'at junctions with no switch.$(br2)Throwing a switch under a train doesn\'t split it: the cars behind follow the engine.'),
    craft('switch_stand'),
], sort=5)

entry('signals', 'track_sensors', 'Track Sensors', 'track_sensor', [
    text(f'A {I("track_sensor")} beside the track (up to three blocks away, its eye facing the track) gives a redstone signal while a '
         'train is over the track beside it, moving or standing. A comparator reads how many cars the train has.$(br2)$(l)Click$() it '
         'to choose which way trains must be going: either, or only left or right as you stand behind it.'),
    text('$(l)Click it with an item$() to sense only some trains:$(li)a locomotive, coach or wagon: trains with that kind of car'
         '$(li)a timetable: trains running it (a blank one: any timetable)$(li)a spawn egg: trains carrying that animal$(li)a filled '
         'bucket: tank wagons of that fluid$(li)anything else: trains carrying it$(br)Sneak-click to clear it.'),
    image('track_sensor', 'Sensing trains with an electric, going right.'),
    craft('track_sensor', None, 'Makes two.'),
], sort=6)

entry('signals', 'level_crossings', 'Level Crossings', 'level_crossing', [
    text(f'Where a road crosses the line, put a {I("level_crossing")} at the side of the road, within three blocks of the track: its arm '
         'comes down towards you, across the road. Click the post to set how far the arm reaches (one to eight blocks).$(br2)Real '
         'crossings have one each side of the line.'),
    text('When a train is due within ten seconds, the lamps flash and the bell rings; then the arm comes down, and stays down while '
         'any of a train is on the crossing. Under it, an unseen barrier stops cars getting through.$(br2)It gives a redstone signal '
         'while it\'s warning, for your own lamps or bells.'),
    image('crossing_closed', 'The lamps flash and the arms are down.'),
    craft('level_crossing'),
], sort=7)

# -- Stations and timetables --

entry('stations', 'stations', 'Stations', 'station', [
    text(f'Put a {I("station")} beside the track, like a signal. $(l)Click$() it to name it (or name it in an anvil first) and set how '
         'long timetabled trains wait there, 2 to 120 seconds.$(br2)Trains on a timetable stop with their front level with its '
         'nameboard. It gives a redstone signal while a train is standing at it: handy for doors, lamps or a bell.'),
    image('station', 'A station and its nameboard.'),
    craft('station'),
], sort=0)

entry('stations', 'timetables', 'Timetables', 'timetable', [
    text(f'$(li)Click stations with a {I("timetable")}, in the order the train should call at them (up to 16). Its tooltip lists them.'
         '$(li)Fuel the locomotive, and click any car of the train with the timetable. Off it goes.$(br2)It runs to each station in '
         'turn and round again, waiting at each.'),
    text('It picks its way at junctions, stops for red signals, keeps to speed limits, and backs out of dead ends (or turns on a '
         'turntable).$(br2)Climb into the cab and touch the throttle or brake to take over; sneak-click the train with the timetable '
         'to stop it. Sneak-click the air to clear the timetable.'),
    image('timetabled_trains', 'Two timetabled trains share a signalled loop.'),
    craft('timetable', None, 'Two paper and a clock.'),
], sort=1)

entry('stations', 'wagons', 'Leaving Wagons', 'freight_wagon', [
    text('After adding a station to a timetable, click it again with the same timetable to say what the train does with wagons '
         'there. Each click goes on to the next:$(li)$(l)Leave$() a wagon, or 2, 3 or 4, or all of them$(li)$(l)Pick up$() waiting wagons'
         '$(li)Nothing to do with wagons$(br)The tooltip shows what each stop does.'),
    text('Leaving, it uncouples cars from the end it won\'t be leading with when it sets off again, and leaves them standing. Coming '
         'back out of a dead-end siding it leaves the ones at the far end, so push them in with the engine at the back.$(br2)Picking up, '
         'it runs up to wagons standing near the nameboard, couples on, and takes them along.'),
    image('wagons_left', 'The diesel\'s left two wagons at the goods yard.'),
    text('For example: a goods train running Quarry, then Yard (leave 2 wagons) leaves two wagons at the yard every trip; a second '
         'engine running Yard (pick up), then Works (leave all) brings them on to the works.$(br2)Leave wagons on level track: loose '
         'wagons roll on a slope (a guard\'s van\'s handbrake holds them).'),
], sort=2)

# -- Freight --

entry('freight', 'wagon_loader', 'Wagon Loaders', 'wagon_loader', [
    text(f'Put a {I("wagon_loader")} beside the track (two blocks away is best: its chute reaches over) with chests, barrels or other '
         'storage next to it. It works on every freight, tank and livestock wagon of a train standing beside it:'
         '$(li)$(l)Loading$() (green arrow): fills them from its storage, tanks and the animals round it$(li)$(l)Unloading$() (orange): '
         'empties them'),
    text('Sneak-click it to switch between the two. It moves ten stacks a second; two at one train work twice as fast.$(br2)Redstone '
         'pauses it, a comparator reads how full the wagons are, and loading, it tops up tenders\' coal and diesels\' fuel too.$(br2)A '
         'timetabled train waits at a station until its loaders are done.'),
    image('wagon_loader', 'Filling a freight wagon.'),
    craft('wagon_loader'),
], sort=0)

entry('freight', 'filters', 'Loader Filters', 'wagon_loader', [
    text('Click a loader to open its screen: nine $(l)filter$() slots, a button for loading or unloading, and one for what the filter '
         'does: $(l)only these$(), or $(l)all but these$(). To put an item in, click a filter slot holding it, or shift-click it in '
         'your inventory: the filter shows a copy. Click a slot empty-handed to clear it.'),
    text('A filled bucket stands for its fluid, for tank wagons, and a spawn egg for its animal. Items, fluids and animals are '
         'filtered separately, and engines\' fuel isn\'t filtered at all.$(br2)So one station can load iron into one train and coal '
         'into another, or unload just the logs.'),
    image('loader_filter', 'Iron ore, coal and water in the filter.'),
], sort=1)

# -- Settings and more --

entry('more', 'minimap', 'Trains on the Map', 'minecraft:compass', [
    text('With $(thing)Xaero\'s Minimap$() installed, every train in your dimension shows on it, however far away: a marker at its '
         'leading locomotive, lettered by the kind of engine (St, Ex, Di, El, Bu), with how many cars it has and where it\'s going.'
         '$(br2)Each player can hide them in the client settings, and a server can stop sharing them.'),
    image('trains_on_minimap', 'Trains on the minimap, lettered by their engines.'),
    text('With $(thing)Xaero\'s World Map$() they show on the world map too, in their engines\' colours. $(l)Click$() one for what '
         'it\'s doing: who\'s driving it, or whether it\'s running to its timetable; how fast it\'s going, and its next stop.'),
    image('world_map', 'A train on the world map, clicked on.'),
], sort=0)

entry('more', 'jei', 'JEI', 'minecraft:crafting_table', [
    text('With $(thing)JEI$() installed, each of the mod\'s items has an $(l)information$() page saying what it does.$(br2)There\'s '
         'also a $(l)Locomotive Fuel$() page for each locomotive (what it runs on, and how long it lasts as the server has it set) '
         'and a $(l)Track-Laying Wagon$() page (what it lays and fills dips with): look up an engine\'s uses, or a fuel\'s.'),
], sort=1)

entry('more', 'advancements', 'Advancements', 'minecraft:experience_bottle', [
    text('The mod has its own advancements, under $(l)Reiko\'s Trains$(): putting a train on the track, opening the throttle, a tram, '
         'an electric under the wires, 90 km/h, coupling up, a car aboard a car transporter, a timetable, a route from a signal box, '
         'a turntable, an engine shed, and laying track with the track-laying wagon (a bridge, too).'),
], sort=2)

entry('more', 'settings', 'Server Settings', 'minecraft:command_block', [
    text('A server can change these in its world\'s $(thing)serverconfig/reikotrains-server.toml$():$(li)needsFuel, fuelUse'
         '$(li)steamNeedsWater, waterTowerRateMB$(li)maxSpeed, acceleration$(li)signalsStopDrivers, cautionSpeedKmh, doubleYellowSpeedKmh'
         '$(li)speedLimitsForDrivers$(li)maxTrainLength (16)'),
    text('$(li)loaderSpeed$(li)chargingPointCapacityMJ, chargingPointRateMJ$(li)substationCapacityMJ, wireChargeRateMJ'
         '$(li)crossingWarningSeconds$(li)trainsOnMinimaps$(li)giveHandbook: give new players this book$(br2)Datapacks can change '
         'the tags reikotrains:locomotive_fuel, reikotrains:trackbed, reikotrains:masonry and reikotrains:diesel.'),
], sort=3)


# ---- writing it out ----

FITS = {'patchouli:image': 4, 'patchouli:entity': 3, 'patchouli:crafting': 4, 'patchouli:spotlight': 9, 'patchouli:text': 16}


def lines(t, width=30):
    """About how many lines text takes on a page."""
    t = re.sub(r'\$\(k:[^)]+\)', 'Space', t)
    t = re.sub(r'\$\(br2\)', '\n\n', t)
    t = re.sub(r'\$\((br|li)\)', '\n  ', t)
    t = re.sub(r'\$\([^)]*\)', '', t)
    n = 0
    for para in t.split('\n'):
        line = 0
        n += 1
        for word in para.split(' '):
            if line and line + 1 + len(word) > width:
                n += 1
                line = len(word)
            else:
                line += (1 if line else 0) + len(word)
    return n


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write('\n')


def main():
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    write(f'{RES}/data/{MOD}/patchouli_books/{BOOK}/book.json', {
        'name': 'Railway Handbook',
        'landing_text': 'Everything about $(l)Reiko\'s Train Mod$(): laying track, building and driving trains, signalling the line, '
                        'and running it all by timetable.$(br2)Open a chapter on the right, or search for anything.',
        'subtitle': 'Reiko\'s Train Mod',
        'use_resource_pack': True,
        'model': f'{MOD}:railway_handbook',
        'creative_tab': f'{MOD}:trains',
        'show_progress': False,
        'text_overflow_mode': 'resize',
        'index_icon': f'{MOD}:steam_locomotive',
        'header_color': '1F4A2E',
        'nameplate_color': 'E2C45C',
    })
    for cid, title, desc, icon, sort in CATEGORIES:
        write(f'{OUT}/categories/{cid}.json', {'name': title, 'description': desc, 'icon': icon, 'sortnum': sort})
    ids = set()
    for cat, eid, title, icon, sort, pages, priority in ENTRIES:
        path = f'{cat}/{eid}'
        assert path not in ids, path
        ids.add(path)
        data = {'name': title, 'icon': icon, 'category': f'{MOD}:{cat}', 'sortnum': sort, 'pages': pages}
        if priority:
            data['priority'] = True
        write(f'{OUT}/entries/{path}.json', data)
    problems = []
    # Titles must fit across the page.
    for cid, title, *_ in CATEGORIES:
        if not fits(title):
            problems.append('category title too wide: ' + title)
    for cat, eid, title, icon, sort, pages, priority in ENTRIES:
        if not fits(title):
            problems.append('entry title too wide: ' + title)
        for i, page in enumerate(pages):
            if not fits(page.get('title', '')):
                problems.append('page title too wide: ' + page['title'])
            room = FITS.get(page['type'], 99) - (2 if page.get('title') else 0)
            if i == 0 and page['type'] == 'patchouli:text':
                room -= 3  # the entry's title and its rule take the top of the first page
            used = lines(page.get('text', ''))
            if used > room:
                problems.append(f'{cat}/{eid} page {i} ({page["type"]}): {used} lines, room for {room}')
    # Every link must go somewhere.
    for cat, eid, title, icon, sort, pages, priority in ENTRIES:
        for page in pages:
            for link in re.findall(r'\$\(l:([^)]+)\)', page.get('text', '')):
                if link not in ids and link not in {c[0] for c in CATEGORIES}:
                    problems.append(f'{eid}: link to nowhere: {link}')
    # Every recipe in the mod should be in the book.
    shown = set()
    for *_, pages, priority in ENTRIES:
        for page in pages:
            for key in ('recipe', 'recipe2'):
                if key in page:
                    shown.add(page[key].split(':')[1])
    for recipe in sorted(RECIPES - shown):
        problems.append('recipe not in the book: ' + recipe)
    # Every picture must have a crop, and its screenshot be there.
    import pictures
    for img in sorted(IMAGES - set(pictures.CROPS)):
        problems.append('no crop for picture: ' + img + ' (add it to pictures.py)')
    for img in sorted(IMAGES & set(pictures.CROPS)):
        if not os.path.exists(f'{pictures.SHOTS}/{pictures.CROPS[img][0]}'):
            problems.append(f'no screenshot for picture {img}: docs/screenshots/{pictures.CROPS[img][0]}')
    if problems:
        raise SystemExit('\n'.join(problems))
    pictures.main()
    # Every spread, for the dev showcase that reads the whole book (./gradlew runClient -Pshowcase -PshowcaseBook).
    os.makedirs(f'{REPO}/run', exist_ok=True)
    with open(f'{REPO}/run/guide-spreads.txt', 'w') as f:
        for cat, eid, title, icon, sort, pages, priority in ENTRIES:
            for page in range(0, len(pages), 2):
                f.write(f'{cat}/{eid} {page}\n')
    with open(f'{REPO}/build/guide-images.txt' if os.path.isdir(f'{REPO}/build') else os.devnull, 'w') as f:
        f.write('\n'.join(sorted(IMAGES)) + '\n')
    print(len(CATEGORIES), 'categories,', len(ENTRIES), 'entries,', sum(len(e[5]) for e in ENTRIES), 'pages')
    print('images:', ' '.join(sorted(IMAGES)))


main()
