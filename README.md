# Reiko's Train Mod

Big steam trains for Minecraft **1.21.1** (NeoForge), on their own track. Lay a railway with curves, ramps and
junctions, fire up a two-block-wide tank engine, couple coaches and freight wagons behind it, and drive the whole
train with a throttle, a brake, a whistle and a choice of way at every junction.

![A train on the line](docs/screenshots/train.jpg)

![Round the bend](docs/screenshots/on_the_move.jpg)

## What's in it

- **Train Track**: its own railway, separate from minecart rails. Straight track goes down block by block (east-west,
  north-south or diagonal), and curves, ramps, S-bends and long straights are drawn from one track block to another.
  Two ways out of the same end of a block make a junction.
- **Steam Locomotive**: a green tank engine, 2 blocks wide, 3 tall and 6 long. It burns coal (or charcoal, logs,
  blocks of coal, blaze rods, lava...), puffs smoke and chuffs, and its driving wheels and coupling rods turn as it
  goes. Seats a driver and a fireman.
- **Passenger Coach**: 6½ blocks long, eight seats in four rows.
- **Freight Wagon**: holds 108 stacks (four chests' worth), all on one screen. Hoppers above or below it fill and empty
  it, crates pile up in it as it fills, and a full one is heavier to pull.
- **Coupler**: joins cars into trains.

The coaches and wagons ride on two bogies that swing under them, so long cars go round curves like the real thing.

![The loop](docs/screenshots/loop.jpg)

## Building a railway

| | |
| --- | --- |
| **Straight track** | Place Train Track on the ground: it runs the way you're facing (one of eight directions). Blocks in a line join up by themselves. |
| **Curves and ramps** | Hold track and click a track block to start from, then click where the line should go: on the ground for a new end (the track there faces to make a smooth curve), or on another track block to join it. A green outline shows the curve before you click (red if it can't be built, with the reason). |
| **S-bends** | Sneak while you click the end, and the track there faces the way you're looking. |
| **Junctions** | Draw a curve off a track block that already has track going on from the same end. |
| **Start over** | Sneak-click the air, or click the starting block again. |

A curve can reach 48 blocks, can't turn tighter than a radius of 5 blocks (8 or more looks best with long cars) or
more than 90° at once, and a ramp can't be steeper than about 1 in 2 at its steepest. Curves cost a piece of track
per block of length, and breaking either end gives it back. Track needs solid ground under its blocks, but a curve
or ramp can cross gaps between them, so bridges are just long curves. Leave three blocks between parallel lines.

![Laying a curve](docs/screenshots/laying_track.jpg)

![A junction](docs/screenshots/junction.jpg)

## Trains

### Crafting

| Item | Recipe |
| --- | --- |
| Train Track (16) | Iron ingots in the four corners, a row of wooden slabs across the middle |
| Steam Locomotive | Furnace minecart in the middle, copper ingots either side, iron ingots along the bottom and one top right (the chimney) |
| Passenger Coach | Glass panes on top; planks, wool, planks; iron ingot, minecart, iron ingot |
| Freight Wagon | Planks around a chest (top middle open); iron ingot, minecart, iron ingot |
| Coupler | Iron ingot, chain, iron ingot (a column) |

Everything's also in the **Reiko's Trains** creative tab.

### Making up a train

1. Click a track block with a car to put it there, facing along the track the way you're looking. It needs track under
   both its bogies.
2. Take the **Coupler**, click one car, then the car next to it on the same line (up to 5 blocks between their
   buffers). The second car's train is drawn up until the buffers meet. Cars couple at either end, facing either way,
   and the locomotive can be anywhere in the train: it pulls or pushes.
3. To uncouple a car, **sneak-click** it with the coupler.

A train has one locomotive and up to 16 cars (configurable).

### Driving

Click the locomotive with fuel to fill the firebox (it takes as much of the stack as fits), then click it with an
empty hand to climb into the cab.

| Key | Does |
| --- | --- |
| Forward (W) | Throttle up a notch (hold to keep going) |
| Back (S) | Throttle down a notch, through neutral into reverse |
| Jump (Space) | Brake, while held |
| Left / right (A / D) | Hold to take the left or right way at the next junction (otherwise it goes straight on) |
| H | Whistle (rebind under Controls, *Reiko's Trains*) |
| Sneak | Get out |

The gauges, bottom right, show the throttle notch (R2, R1, N, 1-4), the speed, which way you're steering, the brake
and the firebox. Top speed is 43 km/h; a heavy train takes longer to get going, and slows (or stalls) going uphill.
Cars roll downhill on their own. At the end of the line a train stops dead. If the track under a car is broken, the
car comes off and drops as an item. With nobody at the controls the handbrake holds the train, and the fire only
burns while someone's driving. A moving train keeps the chunks around it loaded, so it doesn't stop at the edge of
the loaded world, and it nudges people and animals off the line.

![From the cab](docs/screenshots/cab.jpg)

![The freight wagon](docs/screenshots/wagon.jpg)

### Server settings

In `serverconfig/reikotrains-server.toml` in each world:

| Setting | Default | |
| --- | --- | --- |
| `needsFuel` | `true` | Turn off for trains that run on nothing |
| `fuelUse` | `1.0` | How fast fuel burns (1.0: a coal lasts about 3 minutes at full throttle) |
| `maxSpeed` | `0.6` | Top speed in blocks per tick (0.6 is 12 blocks a second) |
| `acceleration` | `0.004` | How quickly a locomotive with two coaches picks up speed |
| `maxTrainLength` | `16` | The most cars in one train |

The fuel a locomotive takes is the item tag `reikotrains:locomotive_fuel`, so a datapack can change it.

## Upgrading from 0.1.0

0.1.0's trains ran on minecart rails. In 0.2.0 they're bigger and need Train Track, so any 0.1.0 train cars in a world
turn back into items (dropped where they stood) the first time they load. Put them back on the new track.

## Building from source

You need Java 21. Then:

```sh
./gradlew build              # the mod jar ends up in build/libs/
./gradlew runClient          # a dev client with the mod
./gradlew runGameTestServer  # runs the game tests and exits
```

The **game tests** (`src/main/java/com/reiko/trains/gametest`) build real track and run trains on it: straight lines
joining up, the curve rules (radius, steepness, direction) and taking curves apart, cars sitting on their bogies,
steaming off, running out of fuel, the brake and handbrake, buffer stops, reversing and pushing, going round a loop of
curves without coming apart, steering at a junction, rolling down and climbing a ramp, coupling (nose to nose, either
end, one locomotive per train), saving, derailing when the track goes, the 108-slot wagon with a hopper, fuel and
recipes.

`./gradlew runClient -Pshowcase -PwindowSize=1280x720` builds a test railway, runs trains round it and saves
screenshots to `run/screenshots/` (that's where the pictures above come from).

### Models, textures and sounds

These are generated by the scripts in `tools/` (they need Python 3 with Pillow and numpy, and ffmpeg for the sounds):

- `tools/models/train_models.py`: the train models (locomotive, coach, wagon, bogie). One part list per model makes
  both the Java geometry (`client/model/geometry/`, don't edit those by hand) and the matching texture.
- `tools/blocks/track_assets.py`: the track's textures and block models, and the wagon's screen.
- `tools/items/item_icons.py`: the item icons and the mod list logo.
- `tools/sounds/train_sounds.py`: the whistle, the chuffs and the coupling clank, synthesised.

### How it works

- **Track** (`track/`): every track block is a node with two sides. Out of each side run its links: the next block in
  line, and any curves drawn from it (kept by the block entities at both ends). Every link is a cubic Bézier curve
  leaving each block along its axis, so straights, arcs, S-bends and ramps are all the same thing. `TrackPos` is a
  place on the track that can walk along it, choosing at junctions.
- **Trains** (`train/`): each car rides on two `TrackPos` bogies and sits between them. The first car of a train works
  out the train's speed each tick (the locomotive's pull, gravity on slopes, friction, brakes) and moves every bogie
  the same distance along the track. The bogie at the very front chooses the way at junctions, and the rest follow its
  choices, so a train never splits at a junction. Cars are made of several hitbox parts along their length, as entity
  boxes can't turn.
- **Client** (`client/`): `TrainCarRenderer` draws a car and its swinging bogies, `TrackRenderer` draws curves,
  `TrackPreview` the outline while you lay track, and `TrainHud` the cab gauges.

## Ideas for later

- Signals, stations and timetables
- More locomotives (a diesel?), tenders, more wagons
- Track switches you can set by hand or with redstone

## License

GPL-3.0, see [LICENSE](LICENSE).
