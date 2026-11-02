# Reiko's Train Mod

Big steam trains for Minecraft **1.21.1** (NeoForge), on their own track. Lay a railway with curves, ramps and
junctions, signal it, work its switches by hand or with redstone, name its stations, and either drive the trains
yourself (throttle, brake, whistle, a choice of way at every junction) or give them a timetable and let them run
between the stations on their own.

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
- **Railway Signal**: red while there's a train on the line ahead, green when it's clear. Trains stop for red.
- **Switch Stand**: sets which way trains go at a junction. Flip it by hand, or set and hold it with redstone.
- **Station**: a nameboard beside the track. Trains stop with their front level with it.
- **Timetable**: a list of stations for a train to run between by itself.

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
| Railway Signal (2) | Redstone lamp, iron ingot, iron ingot (a column) |
| Switch Stand | Lever, iron ingot, iron ingot (a column) |
| Station | Any sign, iron ingot, iron ingot (a column) |
| Timetable | Two paper and a clock |

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
| Left / right (A / D) | Hold to take the left or right way at the next junction (otherwise it goes straight on). Junctions with a switch stand go the way the switch is set |
| H | Whistle (rebind under Controls, *Reiko's Trains*) |
| Sneak | Get out |

The gauges, bottom right, show the next signal (and how far), the next switch (which way it's set, and how far), the
next station, the throttle notch (R2, R1, N, 1-4),
the speed, which way you're steering, the brake and the firebox. Top speed is 43 km/h; a heavy train takes longer to get going, and slows (or stalls) going uphill.
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
| `signalsStopDrivers` | `true` | Whether trains people drive stop for red signals by themselves |
| `maxTrainLength` | `16` | The most cars in one train |

The fuel a locomotive takes is the item tag `reikotrains:locomotive_fuel`, so a datapack can change it.

## Signals

Put a **Railway Signal** beside the track (up to three blocks away; leave a block's gap so trains clear it), looking
along the line the way the trains will go. It guards the track ahead that way, up to the next signal facing the
same way (down every branch of any junctions). It shows **red** while any train is on that stretch, and **green**
when it's clear. Its lamps face the trains coming towards it.

- Trains stop just short of a red signal and go on when it turns green: trains on a timetable always, and trains
  you're driving too (the server can turn that off with `signalsStopDrivers`). The cab gauges show the next signal and
  how far it is.
- **Redstone** holds a signal at red: wire it to a lever to stop trains by hand.
- A **comparator** beside a signal reads 15 while there's a train on the stretch ahead.
- Signals face one way, and they also tell trains on a timetable which way the line runs: a loop signalled one way
  round is run that way round.

![A signal turns red behind a train](docs/screenshots/signals.jpg)

## Switches

Put a **Switch Stand** beside a junction, within three blocks of the track block where the line divides. From then
on that junction goes the way the switch is set, for every train going through it towards the fork:

- **Normal** (green target): the straightest way.
- **Thrown** (yellow target, lever over): the way that branches off.

Click the stand to throw it over or put it back. Where three ways leave the same end of a block, clicks go round them:
left, right, straight on.

**Redstone** sets the switch and holds it there; clicking does nothing while it's powered, and when the power goes
off it's let go and goes back to normal. **Sneak-click** the stand to choose what redstone does: throw it (to the
left or the right branch, at a three-way junction) or hold it straight on. A new stand throws it. Trains coming the
other way (out of either branch towards the main line) just run through.

Trains on a timetable set an unheld switch for themselves as they come up to it, to the way to their next station.
A switch held by redstone they have to obey, so redstone can keep them off a branch, or send them round a different way (or into a siding).
Drivers' A / D steering only counts at junctions with no switch. Throwing a switch under a train doesn't split it:
the cars behind follow the engine.

![A switch stand, set normally (left) and thrown](docs/screenshots/switch_stand.jpg)

![A train on a timetable sets the switch for the branch](docs/screenshots/switch_branch.jpg)

![The gauges: the switch 3 m ahead is set to the right](docs/screenshots/switch_gauges.png)

## Stations and timetables

Put a **Station** beside the track the same way. Click it to name it (or name it in an anvil first) and set how long
trains on a timetable wait there (2 to 120 seconds). It gives a **redstone signal** while a train is standing at it:
handy for doors, lamps or a bell.

![A station](docs/screenshots/station.jpg)

To run a train by itself:

1. Click stations with a **Timetable**, in the order you want them called at (up to 16). Its tooltip lists them.
2. Fuel the locomotive, and click any car of the train with the timetable. Off it goes.

A train on a timetable runs to each station in turn and round again: it draws up with its front at the nameboard,
waits, whistles, and sets off for the next. It picks its way at junctions (the way that leads to the next station),
stops for red signals, and backs out of dead ends: at a terminus it just reverses. Passengers see the station names
as they go. Climb into the cab and touch the throttle or brake to take over; sneak-click the train with the timetable
to stop it running. Sneak-click the air with a timetable to clear it.

![Two trains on timetables share a signalled loop](docs/screenshots/timetabled_trains.jpg)

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
recipes. `SignalGameTests` covers signals (red with a train ahead, green when it's gone, redstone, comparators),
drivers stopping at red, stations' redstone, the timetable item, a timetabled train shuttling between two stations,
waiting at a signal, taking a branch to reach its station, handing over to a driver, and two timetabled trains
sharing a signalled loop without ever meeting. `SwitchGameTests` covers switch stands finding their junction,
clicking and redstone (holding it thrown or straight on), three-way junctions, drivers going the way the switch is set whatever they steer, throwing a
switch under a train without splitting it, and timetabled trains setting switches their way or obeying a held one.

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
- **Signals, switches and stations** (`signal/`): each is tied to a track block. A signal works out its section (the
  track ahead up to the next signals) and checks it for trains. A switch stand works one side of a junction block.
  `TrackSide` keeps track of which are at which block, so trains look them up as they go: wherever a train chooses
  its way (driving, looking ahead, or a timetable's route), a switch there decides.
- **Driving** (`train/Lookahead`, `Autopilot`): every tick a train looks 80 blocks down the line for signals,
  stations and the end of the track, and slows to stop in time for a red signal (or its station). A timetabled train's
  `Autopilot` searches the track for the way to its next station.
- **Client** (`client/`): `TrainCarRenderer` draws a car and its swinging bogies, `TrackRenderer` draws curves,
  `TrackPreview` the outline while you lay track, `StationRenderer` the names on the boards, and `TrainHud` the cab
  gauges.

## Ideas for later

- More locomotives (a diesel?), tenders, more wagons
- Loading and unloading wagons at stations automatically
- A departures board

## License

GPL-3.0, see [LICENSE](LICENSE).
