# Changelog

Reiko's Train Mod, for Minecraft 1.21.1 (NeoForge).

## 1.1.1

A fix for a problem reported on [issue #1](https://github.com/Reiko45/Reiko-s-Train-Mod/issues/1): a train could split
at a junction, its engine going one way and the cars behind it the other.

- **The cars behind always follow the engine at a junction.** Each car now goes whichever way the car in front of it
  went. Before, the train remembered which way its front had gone at each junction, but it didn't remember that
  through being saved, and coupling or uncoupling cars made it forget too. So a train standing partway over a
  junction when the world was saved (or left behind when you went far enough away) could split when it set off
  again: the engine down the branch, the tender straight on, still coupled across the two lines.
- **A train that's already split comes apart.** Coupled cars that aren't on the same line any more are uncoupled, and
  the ones behind stop where they are, so you can bring them back and couple them up again.
- Two new game tests (221 in all). The network version stays 14.

## 1.1.0

Trains are solid.

- **Locomotives, tenders, coaches, wagons and trams can't be walked through any more.** People, animals and dropped
  things bump into them all along their length, as they would a boat, and you can climb onto a train and stand on
  its roof. Getting in and out works as before; anyone getting out lands clear of the car. A moving train slides out
  from under anyone standing on it, so get in to ride.
- **Cars from Reiko's Car Mod bump into trains and trams** instead of driving through them: on the street, and at a
  level crossing with a train standing on it.
- **A car transporter is solid up to its deck**, so it's what you (and the cars on it) stand on. A car or a bus driven
  up against a transporter's end still goes aboard, though its nose is over the end by then (a bus's sticks out more
  than two blocks past its hitbox).
- The network version changes: clients and servers both need 1.1.0, so everyone's game agrees on what's solid.
- Three new game tests (219 in all), and the client check walks into a coach and stands on its roof in survival.

## 1.0.1

Fixes for [issue #1](https://github.com/Reiko45/Reiko-s-Train-Mod/issues/1): trains broke after leaving the world and
coming back.

- **Trains can be used again after a world's loaded.** A car's hitbox is a row of sections along it, which weren't put
  back round it when it loaded, and the server only takes a player's click or hit within reach of the section clicked.
  So after leaving and coming back, a standing train couldn't be got into, coupled or uncoupled, or broken (even with a
  pickaxe) until it had moved. The sections now go round the car as soon as it loads, and are kept there.
- **Bogies follow the curve again after loading.** A car standing on a curve had its bogies drawn straight, off the
  rails, after the world was loaded again.
- **Leaving the game while riding a train no longer splits it.** Minecraft takes a ridden vehicle away with its rider
  (like a boat) until they come back; a train car taken away left a gap, and after ten seconds the train uncoupled it
  for good. Now whoever's riding gets off beside the train as they leave, and the train stays whole.
- **Couplings aren't given up on while the world's still loading.** A train waits for a coupled car that hasn't loaded
  yet for as long as the world round it is still loading, not just ten seconds.
- **A car left coupled to a car that's gone is let go of**, so it can be used and moved again instead of standing stuck
  on the track (which earlier versions could leave behind).
- **Cars go on the track anywhere along it**: on a curve, not only on the track blocks at its ends, and near the end
  of a line as near where you clicked as they fit, rather than not at all. The same for the low-floor tram.
- The network version is unchanged: 1.0.0 and 1.0.1 can play together, but update both to get every fix.
- Five new game tests (216 in all), and a check that opens a saved world again (`-PrejoinTest`).

## 1.0.0

The first full release: everything up to 0.29.0, ready to play.

- From here on, updates keep worlds working (see Upgrading in the README).
- The mod's description in the in-game mods list now covers all of it: trams, the Bullet Train, overhead wires,
  four-aspect signals and the signal box, turntables and engine sheds, the track-laying wagon and the handbook.
- Xaero's World Map is listed as an optional dependency (trains show on it).
- Nothing else changes from 0.29.0: worlds, clients and servers carry straight on (the network version is still 13).

## 0.29.0

The follow-ups: something for nearly every part of the mod.

- **Track-laying wagon**: it follows the ground up and down at no more than 1 in 8 (or, set in its screen, keeps the
  line level); digs through hills three wide, as an open cutting or, under a tall hill, a tunnel (its roof made good
  where sand or gravel would fall in, water beside it walled off); fills dips three wide; and bridges deeper gaps and
  rivers with a deck on piers every four blocks, up to 48 deep, of stone bricks (anything tagged
  `reikotrains:masonry`) or fill. Its bends can be tight or wide (about 10 or 20 blocks across). Off street track it
  lays street track; with masts and wire aboard it wires the line, a mast every sixteen blocks or so. Its screen has
  buttons for the slopes and the bends.
- **Street track** runs straight on into train track laid on the ground in line with it, near enough level, with no
  ramp.
- New: the **Four-Aspect Signal** (a three-light signal, a redstone lamp and yellow dye). Double yellow while the next
  signal is yellow: trains pass it at no more than 80 km/h (new server setting `doubleYellowSpeedKmh`), then the yellow
  at caution speed. A comparator reads 15, 7, 3 or 0; the gauges and the signal box show two yellow lamps. Trains keep
  to caution speed past a yellow (or a double yellow) through saving the world.
- **Signal box**: a Routes button. Click a signal, then the next one along: the switches between are set and held for
  the route (drawn in blue) until a train has been through. Click a turntable on its map to turn it. Track it has seen
  stays on the map, greyed, when its chunks unload. A comparator beside it reads how many trains are on its map.
- New: the **Small Turntable** (like the turntable, with iron ingots for the blocks of iron), in a pit 11 blocks
  across, for tank engines, diesels and trams. Turntable pits are round now, with a curved stone wall round the edge.
- **Timetabled trains turn turntables themselves**: coming up to one with their stop off another of its tracks, they
  turn the bridge to them, drive on, and turn it on to their stop, engine first.
- **Engine sheds** of several roads: doors side by side (four or five blocks apart, facing the same way) make one shed,
  with one store for them all, each set of doors topping up the engines on its own road.
- **Car transporters** take trailers: a pickup or SUV towing a camper or livestock trailer drives on with it, and the
  car mod's hitch key unhitches and hitches it on the deck.
- New: the **Car Ramp** (iron ingots and smooth stone slabs; makes four), a loading dock beside the track: a car on it
  drives aboard a standing car transporter sideways, to the nearest place with room, and off again onto it.
- **Xaero's World Map** (optional): trains show on it, and clicking one says what it's doing: who's driving it or
  that it's on its timetable, how fast it's going, its next stop, and where it is.
- **Trams** nudge an animal or mob that won't get off the track aside after five seconds. The coupler doesn't split a
  low-floor tram: sneak-clicking any section uncouples the whole tram.
- A **tank wagon of water** in a steam train keeps its tank engines and tenders topped up.
- **Overhead wire** won't be strung through solid blocks.
- **JEI**: the fuel page's times follow the server's `fuelUse`, and say when it needs no fuel.
- New: thirteen **advancements**, on a *Reiko's Trains* tab.
- The Railway Handbook has pages and pictures for all of it.
- The mod's network version changes, so clients and servers both need 0.29.0.
- Twenty-six new game tests (211 in all).

## 0.28.0

The Railway Handbook, and JEI pages.

- New, with Patchouli installed: the **Railway Handbook**, the mod's guide book. Eleven chapters, forty entries: every
  part of the mod, every recipe, the locomotives and wagons turning on stands, and pictures from the game. Every
  player is given one the first time they join a world (once; the server setting `giveHandbook` turns that off), and
  another can be crafted from a book and a piece of Train Track.
- The track-laying wagon's, signal box's, wagon loader's, freight wagon's and station's screens have a little book
  button in the corner that opens the handbook at their page.
- With JEI installed: an information page for each of the mod's items, a **Locomotive Fuel** page for the steam
  engines, the diesel and the electrics (what they run on, and how long each fuel lasts at full throttle), and a
  **Track-Laying Wagon** page (what it lays and what it fills dips with).
- Patchouli and JEI are optional: without them there's no book, no buttons and no JEI pages, and nothing else changes.
- The mod's network version changes, so clients and servers both need 0.28.0 (the handbook's pages come with the
  client).
- Two new game tests (185 in all).

## 0.27.0

The track-laying wagon.

- New: the **Track-Laying Wagon** (iron ingots, a chain, Train Track either side of an iron pickaxe, and a minecart).
  Load it with track and stone, dirt or gravel, couple it at the front of a train and drive off the end of the line:
  it lays track ahead of itself at a walking pace.
- It goes straight on; steer left or right and it lays a smooth 45° bend that way (one for each press).
- It keeps the line level: it digs a cutting through hills (keeping what it digs) and fills dips up to six blocks
  deep under the track. It stops at water, lava, bedrock and anything with things in it in the way, at a drop too deep
  to fill, and when it runs out of track or fill, saying why. A switch in its screen turns it off.
- The mod's network version changes, so clients and servers both need 0.27.0.
- Seven new game tests (183 in all).

## 0.26.0

The signal box.

- New: the **Signal Box** (three levers over planks and an empty map). Click it for a live map of the railway within
  96 blocks: the track in its real shape, turntables, stations, every train car as it moves, the signals' aspects and
  the way each switch is set. Hover over things to see what they are; drag to move the map, scroll to zoom.
- From the map, click a switch to throw it (not if redstone holds it), or a signal to hold it at red and again to let
  it go. Breaking the box lets go of the signals it held.
- Fixed: the Three-Light Signal dropped nothing when broken. A new game test checks every block has a loot table.
- The mod's network version changes, so clients and servers both need 0.26.0.
- Five new game tests (176 in all).

## 0.25.0

Three-light signals.

- New: the **Three-Light Signal** (a Railway Signal, a redstone lamp and yellow dye). It guards its section like any
  signal, and shows yellow while it's clear but the next signal along the way the switches are set is red.
- Trains passing a yellow signal keep to caution speed (40 km/h, the new server setting `cautionSpeedKmh`) until the
  next signal, slowing in time to pass the yellow at it; they speed up again if the next signal turns green. Trains on
  a timetable always; driven trains too, unless `signalsStopDrivers` is off.
- The cab gauges show a yellow signal ahead, and caution speed as the speed limit. Comparators read 7 off a yellow
  signal.
- Fixed: a signal that worked out its section while the world beyond it was still loading kept the short section for
  twenty seconds, and could let a train past into a train it couldn't see; it looks again soon now.
- The mod's network version changes, so clients and servers both need 0.25.0.
- Four new game tests (171 in all).

## 0.24.0

Turntables and engine sheds.

- New: the **Turntable**. Used on flat ground it digs its own pit, 15 blocks across, with a bridge that lines up with
  straight track laid off the pit's edge in any of eight directions; lined up, it's track, and trains run on and off
  it. Click it to turn it to the next track (sneak-click for the other way round), or turn it with redstone; a
  comparator reads which way it points. Trains standing all on the bridge go round with it.
- Timetabled trains that stop on a turntable, and would otherwise back out to their next station, are turned round
  instead.
- New: **Engine Shed Doors**, a frame five blocks across and five high with a big pair of doors, over a straight
  track. They open for trains coming (and for clicks and redstone) and shut behind them; shut, they keep people out.
  Their store of fuel, water, diesel and MJ (from hoppers, pipes, buckets and BuildCraft engines) tops up the engines
  and tenders standing in the shed, up to 32 blocks in; timetabled trains wait there until they're topped up.
- Fixed a water test that could fail depending on where in the world it ran.
- The mod's network version changes, so clients and servers both need 0.24.0.
- Eleven new game tests (167 in all).

## 0.23.0

Water towers.

- Steam engines need water as well as coal: 16 buckets in a tank engine's side tanks, 64 in a tender (the express's
  water is its tender's). They use it as fast as coal, so a full load of each lasts the same; with none there's no
  steam. Engines and tenders come full, and existing ones are filled when they load. A new server setting,
  `steamNeedsWater` (on by default), turns it off.
- New: the **Water Tower**, a two-block wooden tank on legs beside the track. Fill it with buckets or pipes (water
  only, 256 buckets); it fills the tank engines and tenders of a train standing beside it (`waterTowerRateMB`, 500 a
  tick), its spout swinging down over the track. Timetabled trains wait at a station until they're full. Gauge marks,
  comparator output, redstone holds it.
- Buckets of water (and other fluid containers, and pipes) fill tank engines, expresses (into the tender) and tenders.
- The cab gauges show a steam engine's water, and NO WATER when it's dry.
- The mod's network version changes, so clients and servers both need 0.23.0.
- Eight new game tests (156 in all).

## 0.22.0

Trams.

- New: **Street Track**, track set into the road: a whole block of asphalt with grooved rails level with its top, so
  cars and people go over it. It joins up and draws into curves like train track; a new end is set into the road
  block clicked; and curves between street track blocks can be as tight as a radius of 3 blocks.
- New: the **Vintage Tram**, a red and cream single car with a platform at each end: it's driven from whichever end
  the driver gets in at, and its trolley pole trails to the overhead wire and swings round when it changes ends. About
  40 km/h.
- New: the **Low-Floor Tram**, three sections (a driving section at each end and a middle one) that bend round street
  corners, put on the track by one item and taken up as one. About 50 km/h.
- Both run from the overhead wires, with a small battery for gaps, and ring a bell (a foot gong, an electronic chime).
- Street rules: trams stop short of people, animals, cars, boats and other trains on the track ahead, and (with
  Reiko's Car Mod) at red traffic lights beside the line facing them; on a timetable they wait, ringing their bell.
  They brake harder than trains. Ordinary trains are unchanged.
- The mod's network version changes, so clients and servers both need 0.22.0.
- Eleven new game tests (148 in all).

## 0.21.0

Overhead wires.

- New: **Overhead Line Masts**, **Overhead Wire** and **Substations**, to electrify a line. Put masts beside the
  track (up to three blocks from it, curves too): each one's arm reaches out over the track to hold the contact wire
  four and a half blocks above the rails. Click mast to mast with overhead wire to string the spans (up to 32 blocks
  each, four to a mast), drawn with the contact wire, a sagging messenger wire and droppers. Sneak-click a mast with
  wire to take its spans down; breaking a mast drops its wire.
- A substation within four blocks of a mast feeds the line BuildCraft power (MJ): each adds room for a million MJ to
  the line's store. Bars show how full it is, a lamp lights while the line is live, and a comparator reads it.
- Electric locomotives and the Bullet Train's driving cars now have pantographs. Under a live wire they raise them,
  run from the line rather than their batteries (so a flat one gets going), and charge at 2,000 MJ a tick. The fuel
  gauge reads **Wire** while they're on it.
- New server settings: `substationCapacityMJ` and `wireChargeRateMJ`.
- The mod's network version changes, so clients and servers both need 0.21.0.
- Ten new game tests (137 in all).

## 0.20.0

Car transporters.

- New wagon: the **Car Transporter**, a long, low flatbed for the cars, vans, trucks and buses of Reiko's Car Mod
  (optional; without it the wagon carries nothing).
- Drive a car up to a free end of a standing car transporter and keep going: it rolls aboard, to the far end or up
  to the cars already there, or on along the train to the next transporter if this one's empty. Sit in it with the
  train stopped and keep going forwards or back to drive off the end of the train, or along to the next transporter.
- Aboard, cars don't drive, burn fuel or wear, and their drivers can stay in for the ride. The deck takes 11½ blocks
  of cars end to end. Cars towing something don't go on.
- The mod's network version changes, so clients and servers both need 0.20.0.
- Six new game tests (127 in all), which need the car mod (run with `-PcarJars=...`; without it they pass straight
  away). The compile-time stand-ins for optional mods moved to `src/stubs/`.

## 0.19.0

The Bullet Train.

- New: the **Bullet Train**, the top-tier train. A set rather than a locomotive: a streamlined driving car at each
  end, noses out, and new **Bullet Train Coaches** between. About 100 km/h at the default settings, white with a blue
  stripe. Drive it from either end; both driving cars pull, and on a timetable it shuttles back and forth without
  turning. Each driving car seats the driver and eight more, a coach twelve, and the windscreen and windows are open,
  so you can see out.
- It runs on batteries (1,500,000 MJ in each driving car), charged at a charging point like the electric; both
  driving cars charge at once. It has its own horn.
- Fast trains look further ahead (as far as they need to stop, up to 240 blocks), so they brake steadily for red
  signals, stations and speed limits instead of all at once; level crossings look far enough out to warn in time
  for the Bullet Train; and fast trains load the chunks a few seconds ahead of them.
- Speed signs now go up to 90 km/h (10 to 90 in tens, then the end of a limit). Existing signs keep their limits.
- Train car items can have a tooltip; the Bullet Train's say how to make up a set.
- Six new game tests (121 in all), and the recipe test now checks every recipe.

## 0.18.0

Trains on Xaero's Minimap.

- With Xaero's Minimap installed (optional), every train in your dimension shows on the minimap, however far away:
  a marker at its leading locomotive, coloured and lettered by the kind of engine, named with its length and, on a
  timetable, where it's going or which station it's at. They're marked in the world too, but stay out of your
  waypoint list. The train you're on isn't shown.
- Trains stay on the map where they last stood when their chunks unload, and come off it when they're broken up.
- New settings: `trainsOnMinimap` for each player (`reikotrains-client.toml`), and `trainsOnMinimaps` for the server.
- The mod's network version changes, so clients and servers both need 0.18.0.
- Six new game tests: 115 in all.

## 0.17.0

Speed limit signs.

- New block: the **Speed Limit Sign**. Put it beside the track like a signal, facing the trains coming up to it, and
  click it to set its limit: 10, 20, 30, 40 or 50 km/h, or the end of a limit.
- Trains going towards a sign's face slow down in time to pass it at its limit, and keep to it until the next sign
  their way (an end-of-limit sign lifts it). Trains going the other way take no notice, and a train that sets off back
  the way it came leaves its limit behind. The limit is saved with the train.
- Trains on a timetable always keep to the limits; trains you're driving do too, unless the new server setting
  `speedLimitsForDrivers` is turned off.
- The cab gauges show the limit beside the speed, in yellow while the train is slowing for a lower one ahead.
- Six new game tests: 109 in all.

## 0.16.0

The livestock wagon.

- New car: the **Livestock Wagon**, a roofed van with slatted sides and six stalls for animals (cows, sheep, pigs,
  horses and the rest; not tamed pets). Lead animals to it and click it to put them in (you get the leads back);
  sneak-click it empty-handed to let them out. Aboard, they come to no harm from walls, falls or crowding.
- Wagon loaders load livestock wagons with the animals standing around them (within four blocks), and unload them
  behind themselves. Spawn eggs in a loader's filter pick which animals. The comparator counts livestock wagons too,
  and timetabled trains wait while animals are loaded or let out.
- Track sensors take spawn eggs too: only trains carrying that animal.
- Five new game tests: 103 in all.

## 0.15.0

Leaving wagons at stations and picking them up.

- A timetable stop can now leave wagons or pick them up. Click a station again just after adding it to a timetable to
  go round: leave a wagon, 2, 3 or 4 wagons, leave all its wagons, pick up waiting wagons, or nothing. The timetable's
  tooltip shows what each stop does.
- Leaving: before it sets off, the train uncouples that many cars from the end it won't be leading with, and leaves
  them standing at the station. So it leaves its back wagons going on through, and the far ones coming back out of a
  dead end (push them in).
- Picking up: wagons with no locomotive standing at the station (up to 32 blocks short of the nameboard or 24 past
  it): the train runs up to them, couples on and takes them along.
- Timetables made before keep working: their stops have nothing to do with wagons. Clicking the last stop again no
  longer just says it's already there.
- Six new game tests: 98 in all.

## 0.14.0

Track sensors.

- New block: the **Track Sensor**. Put it beside the track; it gives off a redstone signal while a train is over the
  track beside it (moving or standing), and a comparator reads how many cars the train has.
- Click it to sense trains going either way, or only left or only right (an arrow on top shows which).
- Click it with an item to sense only some trains: a train car (trains with that kind of car), a timetable (trains
  running it; a blank one, any timetabled train), a filled bucket (trains with a tank wagon of that fluid), or anything
  else (trains carrying it). The item shows on its back. Sneak-click to clear it.
- Seven new game tests, including a sensor at a junction sending goods trains down the branch: 92 in all.

## 0.13.0

The electric locomotive.

- New locomotive: the **Electric Locomotive**, a red box-cab with a cab at each end. It runs on BuildCraft power (MJ)
  from batteries holding 1,000,000 MJ (about twenty minutes at full power), uses next to nothing standing still, and
  is the quickest engine: stronger than the diesel and faster than the express. Its motors hum, rising with its speed,
  and it has its own horn.
- New block: the **Charging Point**. Put it beside the track; feed it from BuildCraft engines or kinesis pipes (or other
  mods' cables, as BuildCraft's config allows). It stores 1,000,000 MJ and charges any electric standing beside it at
  5,000 MJ a tick. Timetabled trains wait while it's charging them. Bars on its back show its store, a lamp on top
  shows it's charging; redstone pauses it and a comparator reads its store.
- New server settings `chargingPointCapacityMJ` and `chargingPointRateMJ`.
- A diesel that runs dry on a timetable now says it's out of fuel (it said the fire was out).
- Six new game tests: 85 in all.

## 0.12.0

Level crossings.

- New block: the **Level Crossing**. Put it beside a road where it crosses the line (within three blocks of the
  track). When a train is coming its two red lamps flash turn about and its bell rings; three seconds later its
  red-and-white arm swings down across the road, and it goes back up once the train has gone by.
- While the arm is down, an unseen barrier a block and a half high stops traffic, so cars from Reiko's Car Mod stop
  and wait. It leaves a gap where something is standing under the arm.
- Click the post to change the arm's length (one to eight blocks). It gives off a redstone signal while it's warning.
- New server setting `crossingWarningSeconds` (default 10): how long before a train arrives crossings start warning.
- Six new game tests: 79 in all.

## 0.11.0

- Switch stands have a marker on the post showing what redstone does to them: yellow, it throws the switch; green, it
  holds it straight on. It lights up while redstone is holding the switch. Stands in existing worlds get theirs when
  they load.
- New game tests: a marker test, and one checking that no way of clicking a wagon loader's filter slot hands out an
  item: 73 in all.

## 0.10.0

Wagon loader filters.

- Click a wagon loader to open its screen: nine filter slots, a loading/unloading button, and a button for "only
  these" or "all but these". Filter slots hold copies (click with an item, or shift-click it in your inventory; click
  empty-handed to clear). An empty filter moves everything.
- A filled bucket (or other fluid container) in the filter stands for its fluid, for tank wagons. Items and fluids
  are filtered separately, and engines' fuel (a tender's coal, a diesel's diesel) isn't filtered. Hoppers through the
  loader are filtered too.
- Sneak-click a loader to switch between loading and unloading (a plain click now opens its screen).
- Five new game tests: 71 in all.

## 0.9.1

- The tank wagon holds 256 buckets (was 64).
- Wagon loaders pump fluids four times as fast: twenty buckets a second into (or out of) each diesel and tank wagon,
  so a full tank wagon takes about 13 seconds.

## 0.9.0

More wagons.

- Tank Wagon: 64 buckets of any one fluid. Fill and empty it with buckets or any fluid container, by pipe, or with a
  wagon loader beside fluid tanks (five buckets a second; loading fills it, unloading empties it). A gauge on its side
  shows how full it is, and a full one is heavier.
- Guard's Van: a four-wheeled van for the back of the train, with two seats, a stove (it smokes while someone's
  aboard), a veranda and red tail lamps. A train with a van brakes half as hard again (two vans at most), and a train
  with no engine is held by the van's handbrake.
- Wagon loaders' comparators count tank wagons too.
- Five new game tests: 66 in all.

## 0.8.0

The diesel runs on BuildCraft's diesel, like the car mod's diesel vehicles.

- Now requires BuildCraft Reborn 2.1.0 or newer.
- The diesel locomotive's tank holds 16 buckets of Dense Fuel. It takes BuildCraft's Dense Fuel and Mixed Heavy
  Fuels (and other mods' diesel and biodiesel: fluid tag `reikotrains:diesel`), at BuildCraft's own fuel values, the
  same as Reiko's Car Mod. Fill it from a bucket or any fluid container, by pipe, or from a BuildCraft tank beside a
  wagon loader (five buckets a second; timetabled trains wait until they're full).
- The Diesel Fuel canister is gone (any in a world disappear when it loads).
- Server setting `fallbackDieselEnergyPerBucket` for diesel fluids BuildCraft has no value for.

## 0.7.0

The diesel.

- Diesel Locomotive: a blue road-switcher with yellow warning ends, 7 blocks long on two bogies, with a high cab to
  see over its long hood. It pulls harder than either steam engine (52 km/h flat out) and uses less fuel, especially
  standing still. Two-tone horn, an engine that rumbles louder and higher as the throttle opens, and exhaust that
  smokes harder as it works.
- Diesel Fuel: canisters (an iron nugget and four coal or charcoal) for the diesel's tank, which holds ten. Steam
  engines won't take it and the diesel won't take coal. Hoppers and wagon loaders refuel diesels; a canister also
  burns in a furnace.
- Under the hood: steam and diesel locomotives now share a `Locomotive` base class.
- Five new game tests: 61 in all.

## 0.6.0

A second locomotive, and trains with more than one.

- Express Locomotive: a crimson 4-6-0, 8 blocks long, with six big driving wheels, smoke deflectors and a deeper
  whistle. A third faster than the tank engine (58 km/h), nearly twice as strong, and thirstier.
- Tender: the express burns its coal (128 coal's worth, four times a tank engine's firebox). Fill it by hand, through
  the engine, with hoppers, or with a wagon loader (fuel only); its coal heap shows how full it is. Without a tender
  the express won't go, and its gauges say so.
- Trains can have more than one locomotive, anywhere and facing either way. The one on a timetable, or the one being
  driven, is in charge; the others work with it and add their pull.
- Engines on a timetable smoke while they're under way.
- Six new game tests: 56 in all.

## 0.5.0

Freight loading.

- Wagon Loader: put it beside the track with chests (or barrels, hoppers, other mods' storage) next to it. When a
  train stands with a car alongside it, it fills the train's freight wagons from them, front to back, or empties the
  wagons into them; click it to switch. Ten stacks a second (server setting `loaderSpeed`). Redstone pauses it; a
  comparator reads how full the train is; hoppers and pipes can go through it into or out of the train. Its lamp
  lights while it works.
- Trains on a timetable wait at a station until the loaders beside them have finished, and the cab says so.
- Freight wagons now offer their inventory to other mods' pipes and hoppers (NeoForge item handler).
- Eight new game tests: 50 in all.

## 0.4.1

- Redstone can now hold a switch straight on, as well as thrown: sneak-click the stand to choose which (at a
  three-way junction: thrown left, thrown right or straight on). Powered, the switch is set that way and held; when
  the power goes off it goes back to normal. Switches from 0.4.0 keep throwing with redstone.
- At a three-way junction, clicking the stand goes round its ways: left, right, straight on.
- Four new game tests: 42 in all.

## 0.4.0

Switches.

- Switch Stand: put it beside a junction and that junction goes the way it's set: normally the straightest way,
  thrown the branch (sneak-click it to pick left or right at a three-way junction). Click it to throw it, or work it
  with redstone like a door; while redstone holds it, it can't be moved. Its target shows green set normally and
  yellow thrown.
- Trains on a timetable set an unheld switch their way as they come up to it, and obey one held by redstone.
- Drivers' left/right steering only counts at junctions without a switch.
- The cab gauges show the next switch: an arrow for the way it's set (green straight on, yellow for a turn) and how
  far. On a timetable, an AUTO bar takes the throttle's place.
- Eight new game tests: 38 in all.

## 0.3.0

Signals, stations and timetables.

- Railway Signal: guards the line ahead (to the next signal, down every branch) and shows red while a train's on it.
  Trains stop short of red signals, including trains people are driving (server setting `signalsStopDrivers`).
  Redstone holds a signal at red; a comparator reads 15 while the line ahead is occupied. Signals also show timetabled
  trains which way a line runs.
- Station: a nameboard showing its name, set from its screen (or an anvil); trains stop with their front level with
  it. It gives a redstone signal while a train stands at it.
- Timetable: click stations to list them, then click a train to run it between them by itself. It picks its way at
  junctions, waits at stations (each station sets how long), obeys signals, reverses at termini, and tells its
  passengers where they are. A driver takes over by touching the throttle or brake.
- The cab gauges show the next signal (red or green, and how far) and the next station, or the timetable's progress.
- Ten new game tests: 30 in all.

## 0.2.0

Bigger trains on their own track.

- Train Track: the mod's own railway. Straight track goes down block by block in eight directions and joins up by
  itself. Curves, ramps, S-bends and long straights (up to 48 blocks) are drawn by clicking a track block and then
  where the line should go, with a preview of the curve first. A second way out of a block makes a junction. Curves
  need a radius of 5 or more and cost a piece of track per block, refunded when they're broken.
- The trains are now about real-life size: 2 blocks wide and 3 tall. The locomotive is 6 blocks long, the coach 6½
  (eight seats), the wagon 5. Coaches and wagons ride on swinging bogies, so they bend through curves.
- Trains move as one, with proper weight: heavier trains pick up speed slower, slopes pull them back (or downhill,
  along), and loose cars roll down ramps. Top speed 43 km/h (configurable). Trains stop at the end of the line, and
  a car whose track is broken comes off and drops.
- Drivers pick the way at junctions with left and right; the rest of the train always follows the same way.
- The locomotive can be anywhere in a train, pulling or pushing, and cars couple at either end, facing either way.
  Coupling draws the cars together until their buffers meet.
- Freight Wagon: 108 slots (four chests' worth) on one wide screen; a full wagon is heavier.
- Moving trains keep the chunks around them loaded, nudge people and animals off the line, and turn your view with
  them as they go round bends. The speedometer shows the train's real speed.
- 0.1.0 trains (which ran on minecart rails) turn back into items when they load: put them on the new track.

## 0.1.0

The first version.

- Steam Locomotive: a tank engine that burns coal and the rest of the `reikotrains:locomotive_fuel` tag, driven with a
  notched throttle (two reverse notches, four forward), a brake, a whistle and cab gauges. Smoke, chuffs, and wheels
  and coupling rods that turn. Seats a driver and a fireman.
- Passenger Coach (four seats) and Freight Wagon (a chest's worth, works with hoppers, shows its load).
- Coupler: couples cars into trains of up to 16, the locomotive always in front, and uncouples them.
- Trains run on vanilla rails, round corners and up slopes, and hold together while reversing and braking.
- Server settings for fuel, fuel use, acceleration and train length.
- Game tests for driving, fuel, braking, reversing, couplings, saving, loads and corners.
