# Changelog

Reiko's Train Mod, for Minecraft 1.21.1 (NeoForge).

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
