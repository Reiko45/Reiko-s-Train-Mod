# Reiko's Train Mod

Big steam and diesel trains for Minecraft **1.21.1** (NeoForge), on their own track. Lay a railway with curves, ramps and
junctions, signal it, work its switches by hand or with redstone, name its stations, load and unload freight
automatically, and either drive the trains yourself (throttle, brake, whistle, a choice of way at every junction) or
give them a timetable and let them run between the stations on their own.

![A train on the line](docs/screenshots/train.jpg)

![Round the bend](docs/screenshots/on_the_move.jpg)

## What's in it

- **Train Track**: its own railway, separate from minecart rails. Straight track goes down block by block (east-west,
  north-south or diagonal), and curves, ramps, S-bends and long straights are drawn from one track block to another.
  Two ways out of the same end of a block make a junction.
- **Steam Locomotive**: a green tank engine, 2 blocks wide, 3 tall and 6 long. It burns coal (or charcoal, logs,
  blocks of coal, blaze rods, lava...), puffs smoke and chuffs, and its driving wheels and coupling rods turn as it
  goes. Seats a driver and a fireman.
- **Express Locomotive**: a crimson 4-6-0, 8 blocks long, with big driving wheels: a third faster than the tank
  engine and nearly twice as strong. It burns coal from its **Tender**, which holds four times as much.
- **Diesel Locomotive**: a blue road-switcher, 7 blocks long, on two bogies. It runs on **Diesel Fuel**, pulls
  harder than either steam engine, and uses less fuel.
- **Passenger Coach**: 6½ blocks long, eight seats in four rows.
- **Freight Wagon**: holds 108 stacks (four chests' worth), all on one screen. Hoppers above or below it fill and empty
  it, crates pile up in it as it fills, and a full one is heavier to pull.
- **Wagon Loader**: fills the freight wagons of a train standing beside it from the chests next to it, or empties
  them into the chests.
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
| Express Locomotive | Copper, gold, copper; iron ingot, steam locomotive, iron ingot; three iron ingots |
| Tender | Iron ingot, cauldron, iron ingot; iron ingot, minecart, iron ingot |
| Diesel Locomotive | Three iron ingots; iron ingot, blast furnace, iron ingot; copper ingot, minecart, copper ingot |
| Diesel Fuel | An iron nugget and four coal or charcoal (shapeless) |
| Passenger Coach | Glass panes on top; planks, wool, planks; iron ingot, minecart, iron ingot |
| Freight Wagon | Planks around a chest (top middle open); iron ingot, minecart, iron ingot |
| Coupler | Iron ingot, chain, iron ingot (a column) |
| Railway Signal (2) | Redstone lamp, iron ingot, iron ingot (a column) |
| Switch Stand | Lever, iron ingot, iron ingot (a column) |
| Station | Any sign, iron ingot, iron ingot (a column) |
| Timetable | Two paper and a clock |
| Wagon Loader | Iron ingot, hopper, iron ingot; iron ingot, chest, iron ingot |

Everything's also in the **Reiko's Trains** creative tab.

### Making up a train

1. Click a track block with a car to put it there, facing along the track the way you're looking. It needs track under
   both its bogies.
2. Take the **Coupler**, click one car, then the car next to it on the same line (up to 5 blocks between their
   buffers). The second car's train is drawn up until the buffers meet. Cars couple at either end, facing either way,
   and the locomotives can be anywhere in the train: they pull or push.
3. To uncouple a car, **sneak-click** it with the coupler.

A train can have up to 16 cars (configurable), and as many locomotives among them as you like (see
[More than one locomotive](#more-than-one-locomotive)).

### Driving

Click the locomotive with fuel to fill the firebox (it takes as much of the stack as fits), then click it with an
empty hand to climb into the cab.

| Key | Does |
| --- | --- |
| Forward (W) | Throttle up a notch (hold to keep going) |
| Back (S) | Throttle down a notch, through neutral into reverse |
| Jump (Space) | Brake, while held |
| Left / right (A / D) | Hold to take the left or right way at the next junction (otherwise it goes straight on). Junctions with a switch stand go the way the switch is set |
| H | Whistle, or the diesel's horn (rebind under Controls, *Reiko's Trains*) |
| Sneak | Get out |

The gauges, bottom right, show the next signal (and how far), the next switch (which way it's set, and how far), the
next station, the throttle notch (R2, R1, N, 1-4),
the speed, which way you're steering, the brake and the firebox (or tender, or fuel tank). Top speed is 43 km/h for the
tank engine, 52 km/h for the diesel and 58 km/h for the express; a heavy train takes longer to get going, and slows (or stalls) going uphill.
Cars roll downhill on their own. At the end of the line a train stops dead. If the track under a car is broken, the
car comes off and drops as an item. With nobody at the controls the handbrake holds the train, and the fire only
burns while someone's driving. A moving train keeps the chunks around it loaded, so it doesn't stop at the edge of
the loaded world, and it nudges people and animals off the line.

![From the cab](docs/screenshots/cab.jpg)

### The express and its tender

The **Express Locomotive** is the big engine: 8 blocks long, with six 1⅛-block driving wheels. It goes a third
faster than the tank engine (58 km/h), pulls nearly twice as hard, and burns coal faster. It carries no coal itself:
couple a **Tender** to it (at either end, usually behind the cab) and it burns the tender's. A tender holds 128 coal's
worth, four times a tank engine's firebox, and the coal heap in it goes down as it's used.

- Click the engine or the tender with fuel to fill the tender. Click the tender empty-handed to see how full it is.
- Hoppers and wagon loaders fill tenders too, with fuel only: a train on a timetable can take on coal at a station
  with a loader (the tender takes fuel first; anything else goes on into the wagons).
- Without a tender the express won't go, and its gauges say NO TENDER.

![The express](docs/screenshots/express.jpg)

![The express under way](docs/screenshots/express_front.jpg)

### The diesel

The **Diesel Locomotive** is a road-switcher: a long hood in front of a high cab, so you can see over it whichever
way you're going, and a short hood behind. It rides on two bogies like the coaches.

- It runs on **Diesel Fuel**, not coal: canisters made from an iron nugget and four coal or charcoal. Its tank holds
  ten. Click it with canisters to fill it; hoppers and wagon loaders can fill it too (they give it diesel only).
  Steam engines won't take diesel, and the diesel won't take coal. A canister also burns in a furnace.
- It pulls harder than either steam engine, so it gets a heavy train going quickest, though the express is faster
  flat out. It's frugal: a canister goes further than the four coal it's made from would in a steam engine, and it
  burns little standing still.
- Its engine rumbles, and the note rises as the throttle opens. Its exhaust smokes harder the harder it works, with
  a black cloud when you open up. It sounds a two-tone horn instead of a whistle (the same key).

![The diesel with a freight train](docs/screenshots/diesel.jpg)

![The diesel from the front](docs/screenshots/diesel_front.jpg)

### More than one locomotive

A train can have several locomotives, anywhere in it and facing either way: double-head a heavy train, or put a
banking engine at the back to push. One is in charge: the one running a timetable, or else the one someone's driving
(the first one, if several are). The others work with it, notched up the same (turned round, if they face the other
way), and every engine with steam adds its pull, each up to its own top speed. They each burn their own fuel. Their
fires stay lit while the engine in charge is driven, so nobody has to ride in them.

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
| `loaderSpeed` | `640` | How many items a wagon loader moves a second (640 fills a wagon in about 11 seconds) |

The fuel steam engines take is the item tag `reikotrains:locomotive_fuel`, and the diesel's is
`reikotrains:diesel_fuel`, so a datapack can change them (a fuel needs a furnace burn time too).

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

## Loading and unloading freight

Put a **Wagon Loader** beside the track (up to three blocks away, and two is best: its chute reaches over to the
wagons), and storage next to it on any side: chests, barrels, hoppers, or other mods' storage. When a train stands
still with a car alongside the loader, the loader works on every freight wagon in the train, front to back:

- **Loading** (green arrow on top, pointing at the track): it fills the wagons from its storage.
- **Unloading** (orange arrow, pointing away): it empties them into its storage.

Click the loader to switch between the two. Its lamp lights while it's moving goods. It moves ten stacks a second
(server setting `loaderSpeed`), so a full wagon takes about 11 seconds; two loaders at one train work twice as fast.

- **Redstone** pauses it.
- A **comparator** beside it reads how full the train's wagons are (0 with no train there).
- Hoppers and pipes can also go through it: into the train while it's loading, out of it while it's unloading.
- A train on a **timetable** waits at a station until the loaders beside it have finished with it: its time at the
  station is the least it stays. The cab shows "loading" or "unloading" meanwhile. A loader counts as finished when
  it's had nothing to move for a second (the wagons are full, or its storage is empty, or full when unloading).

A freight train can then run a round trip by itself: load at the mine, unload at the yard.

![A wagon loader filling a freight wagon](docs/screenshots/wagon_loader.jpg)

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
end), saving, derailing when the track goes, the 108-slot wagon with a hopper, fuel and
recipes. `SignalGameTests` covers signals (red with a train ahead, green when it's gone, redstone, comparators),
drivers stopping at red, stations' redstone, the timetable item, a timetabled train shuttling between two stations,
waiting at a signal, taking a branch to reach its station, handing over to a driver, and two timetabled trains
sharing a signalled loop without ever meeting. `SwitchGameTests` covers switch stands finding their junction,
clicking and redstone (holding it thrown or straight on), three-way junctions, drivers going the way the switch is set whatever they steer, throwing a
switch under a train without splitting it, and timetabled trains setting switches their way or obeying a held one.
`LocomotiveGameTests` covers the diesel (diesel fuel only, steam engines refusing it, pulling harder on less fuel,
running a timetable with a steam engine helping, a wagon loader refuelling it, the fuel's recipe and burn time), the
express needing its tender (and shovelling coal into it), its speed, two engines
pulling together (one at the back facing back), a double-headed train on a timetable, an express on a timetable, and
a wagon loader topping up a tender with fuel only. `LoaderGameTests` covers loading and unloading a wagon, filling every wagon in a train, redstone, the comparator,
hoppers going through the loader, moving trains being left alone, and a timetabled train waiting until it's loaded.

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
- **Freight** (`freight/`): a wagon loader finds the train standing at its track block each couple of ticks and moves
  goods between the storage next to it and the train's wagons through NeoForge item handlers. It keeps a list of
  loaded loaders, so a timetabled train can ask whether any is still busy with it.
- **Driving** (`train/Lookahead`, `Autopilot`): every tick a train looks 80 blocks down the line for signals,
  stations and the end of the track, and slows to stop in time for a red signal (or its station). A timetabled train's
  `Autopilot` searches the track for the way to its next station.
- **Client** (`client/`): `TrainCarRenderer` draws a car and its swinging bogies, `TrackRenderer` draws curves,
  `TrackPreview` the outline while you lay track, `StationRenderer` the names on the boards, and `TrainHud` the cab
  gauges.

## Ideas for later

- More wagons (a tanker, a guard's van)
- Item filters for wagon loaders

## License

GPL-3.0, see [LICENSE](LICENSE).
