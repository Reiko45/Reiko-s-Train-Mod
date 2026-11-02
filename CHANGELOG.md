# Changelog

Reiko's Train Mod, for Minecraft 1.21.1 (NeoForge).

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
