package com.reiko.trains.train;

import com.mojang.serialization.Dynamic;
import com.reiko.trains.ReikoTrains;
import com.reiko.trains.config.TrainsConfig;
import com.reiko.trains.freight.LoaderMode;
import com.reiko.trains.freight.WagonLoaderBlockEntity;
import com.reiko.trains.power.ChargingPointBlockEntity;
import com.reiko.trains.signal.StationBlockEntity;
import com.reiko.trains.signal.SwitchBlockEntity;
import com.reiko.trains.signal.TrackSide;
import com.reiko.trains.track.EdgeRef;
import com.reiko.trains.track.TrackGraph;
import com.reiko.trains.track.TrackPos;
import com.reiko.trains.track.TrackScanner;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A locomotive running a timetable on its own: it sets off towards the next station, picks its way at junctions
 * (the way that leads there, or failing that the way that heads towards it), stops for red signals, draws up at the
 * station, waits, and goes on to the next, round and round. It turns back at the end of the line, or when the next
 * station is behind it.
 */
public final class Autopilot {
    /** How far it searches the track for the way to a station, in blocks. */
    private static final double SEARCH = 3000;
    /** At a stop to pick up wagons: how far short of the station they can be waiting, and how far past it, in blocks. */
    static final double WAITING_BEFORE = 32;
    static final double WAITING_BEYOND = 24;
    /** How far short of waiting wagons it stops, to couple on (buffer to buffer). */
    private static final double PICK_UP_SHORT = 0.5;

    enum Phase {
        OFF, RUNNING, DWELLING
    }

    private final Locomotive loco;
    private final List<Timetable.Stop> stops = new ArrayList<>();
    private int index;
    private Phase phase = Phase.OFF;
    private int timer;
    /** Which way it's going: +1 the train's forwards. */
    private int direction = 1;
    private boolean chooseDirection;
    /** The way it chose at each junction, on the way to the current stop. */
    private final Map<Long, EdgeRef> choices = new HashMap<>();
    private int stuck;
    private int searchCooldown;

    Autopilot(Locomotive loco) {
        this.loco = loco;
    }

    public boolean isOn() {
        return phase != Phase.OFF;
    }

    public List<Timetable.Stop> stops() {
        return List.copyOf(stops);
    }

    @Nullable
    public Timetable.Stop current() {
        return stops.isEmpty() ? null : stops.get(index % stops.size());
    }

    /** Starts running these stops from the first. */
    public void start(List<Timetable.Stop> timetable) {
        stops.clear();
        stops.addAll(timetable);
        index = 0;
        phase = stops.isEmpty() ? Phase.OFF : Phase.RUNNING;
        chooseDirection = true;
        choices.clear();
        stuck = 0;
        loco.setThrottle(0);
        loco.setBraking(false);
        loco.updateAutoDisplay();
    }

    public void stop() {
        phase = Phase.OFF;
        choices.clear();
        loco.updateAutoDisplay();
    }

    /** What the driver's display says. */
    Component status() {
        Timetable.Stop stop = current();
        if (phase == Phase.OFF || stop == null) {
            return Component.empty();
        }
        if (phase == Phase.DWELLING) {
            if (loco.carriedBy() != null) {
                return Component.translatable("hud.reikotrains.auto.turning", stop.name());
            }
            if (loading != null) {
                return Component.translatable("hud.reikotrains.auto." + (loading == LoaderMode.LOAD ? "loading" : "unloading"), stop.name());
            }
            if (charging) {
                return Component.translatable("hud.reikotrains.auto.charging", stop.name());
            }
            if (watering) {
                return Component.translatable("hud.reikotrains.auto.watering", stop.name());
            }
            if (servicing) {
                return Component.translatable("hud.reikotrains.auto.servicing", stop.name());
            }
            return Component.translatable("hud.reikotrains.auto.at", stop.name(), (timer + 19) / 20);
        }
        if (waitingAtSignal) {
            return Component.translatable(waitingFor);
        }
        if (!loco.hasPower()) {
            return Component.translatable(loco.noPowerStatus());
        }
        return Component.translatable("hud.reikotrains.auto.next", stop.name());
    }

    private boolean waitingAtSignal;
    /** Whether it's seen, this stop, whether it's on a turntable to be turned round on. */
    private boolean turnChecked;
    /** What it's waiting for, when it is: a red signal, a red traffic light, or something on the track. */
    private String waitingFor = "hud.reikotrains.auto.signal";
    private int ringing;
    /** While it's standing at a station: what a wagon loader's doing with the train, if anything. */
    @Nullable
    private LoaderMode loading;
    /** While it's standing at a station: whether a charging point's charging it. */
    private boolean charging;
    /** ... and whether a water tower's filling it. */
    private boolean watering;
    /** Whether an engine shed is still topping it up. */
    private boolean servicing;

    /** Picks the way at a junction: the one that leads to the current stop, or heads most towards it. */
    EdgeRef choose(Level level, BlockPos node, int side, List<EdgeRef> options, Vec3 heading, boolean setSwitch) {
        if (options.size() == 1) {
            return options.getFirst();
        }
        SwitchBlockEntity found = TrackSide.switchAt(level, node, side);
        if (found != null && found.isLocked()) {
            return found.way(options, heading); // held by redstone: it has to go the way it's set
        }
        SwitchBlockEntity stand = setSwitch ? found : null;
        long key = node.asLong() * 2 + (side > 0 ? 1 : 0);
        EdgeRef chosen = choices.get(key);
        if (chosen != null && options.contains(chosen)) {
            if (stand != null) {
                stand.setTo(chosen, options, heading, loco.getUUID());
            }
            return chosen;
        }
        Timetable.Stop stop = current();
        if (stop == null) {
            return TrackGraph.choose(options, heading, 0);
        }
        double best = Double.POSITIVE_INFINITY;
        for (EdgeRef option : options) {
            double distance = TrackScanner.distanceVia(level, option, stop.track(), SEARCH);
            if (distance >= 0 && distance < best) {
                best = distance;
                chosen = option;
            }
        }
        if (chosen == null) {
            // Can't see the way there (the track goes out of the loaded world): head towards it.
            Vec3 goal = Vec3.atCenterOf(stop.track());
            for (EdgeRef option : options) {
                Vec3 ahead = option.geometry().point(Math.min(option.geometry().length(), 6));
                double distance = ahead.distanceToSqr(goal);
                if (distance < best) {
                    best = distance;
                    chosen = option;
                }
            }
        }
        choices.put(key, chosen);
        if (stand != null) {
            stand.setTo(chosen, options, heading, loco.getUUID()); // sets the route as it comes up to it
        }
        return chosen;
    }

    /** Run by the train each tick before it moves (the locomotive is car {@code index}). */
    Drive plan(List<TrainCarEntity> cars, int[] signs, int index, double speed) {
        Level level = loco.level();
        Timetable.Stop stop = current();
        if (stop == null) {
            stop();
            return Drive.NONE;
        }
        if (phase == Phase.DWELLING) {
            // Standing on a turntable, with the next stop back the way it came, it's turned round (rather than run
            // backwards), so a steam engine always goes chimney first. The train stands still while it goes round.
            if (!turnChecked) {
                turnChecked = true;
                turnRound(level, cars, signs, index);
            }
            // It waits its time at the station, and then for any wagon loaders and charging points to finish with it.
            loading = WagonLoaderBlockEntity.busyWith(level, cars);
            charging = ChargingPointBlockEntity.busyWith(level, cars);
            watering = com.reiko.trains.water.WaterTowerBlockEntity.busyWith(level, cars);
            servicing = com.reiko.trains.shed.EngineShedBlockEntity.busyWith(level, cars);
            if (timer > 0) {
                timer--;
            }
            if (timer <= 0 && loading == null && !charging && !watering && !servicing) {
                if (stop.dropsWagons()) {
                    dropWagons(level, cars, signs, stop);
                }
                depart(cars);
            }
            loco.updateAutoDisplay();
            return new Drive(direction, null, 0, true, 0);
        }
        if (chooseDirection) {
            direction = chooseDirection(level, cars, signs, stop);
            chooseDirection = false;
        }
        TrackPos.Chooser chooser = (node, side, options, heading) -> choose(level, node, side, options, heading, true);
        Lookahead look = Lookahead.look(cars, signs, direction, chooser, stop.track(), speed);
        loco.showLookahead(look);
        // Picking up wagons: if they're waiting around the station, it runs up to them instead of to the nameboard.
        Trains.Ahead waiting = stop.picksUpWagons() && look.targetFound ? waitingWagons(level, cars, signs, look.targetDistance) : null;
        double targetStop = waiting != null ? waiting.distance() - PICK_UP_SHORT
                : look.targetFound ? look.targetDistance : Double.POSITIVE_INFINITY;
        double stopAt = Math.min(look.stopFor(), Math.min(targetStop, look.endOfLine ? look.endDistance - 0.05 : Double.POSITIVE_INFINITY));
        double limit = Math.min(Drive.stoppingSpeed(stopAt, look.brake), loco.speedLimit(look, signs[index], speed));
        limit = Math.min(limit, loco.cautionLimit(look, signs[index], speed));
        double target = Math.min(loco.maxSpeed(), limit);
        boolean stopped = Math.abs(speed) < 0.002;
        waitingAtSignal = look.stopFor() != Double.POSITIVE_INFINITY && look.stopFor() <= look.targetDistance;
        waitingFor = !waitingAtSignal ? "hud.reikotrains.auto.signal"
                : look.obstacleStop <= look.stopFor() ? "hud.reikotrains.auto.obstacle"
                : look.lightStop <= look.stopFor() ? "hud.reikotrains.auto.light" : "hud.reikotrains.auto.signal";
        // A tram held up by something on the track rings its bell at it now and then.
        if (stopped && waitingFor.equals("hud.reikotrains.auto.obstacle") && look.obstacleStop < 3) {
            if (++ringing % 100 == 1) {
                loco.whistle();
            }
        } else {
            ringing = 0;
        }
        if (waiting != null && stopped && waiting.distance() <= Trains.COUPLING_REACH && look.signalStop >= waiting.distance()) {
            TrainCarEntity lead = cars.get(Lookahead.leadIndex(cars, direction));
            int before = cars.size();
            if (Trains.couple(lead, waiting.car()) == Trains.CoupleResult.COUPLED) {
                arrive(level, cars, stop);
                int picked = Trains.train(lead).size() - before;
                announce(cars, Component.translatable("message.reikotrains.auto.picked_up", picked, stationName(level, stop)));
                return new Drive(direction, chooser, 0, true, 0);
            }
        }
        if (waiting == null && look.targetFound && Math.abs(look.targetDistance) < 0.6 && stopped && !waitingAtSignal) {
            arrive(level, cars, stop);
            return new Drive(direction, chooser, 0, true, 0);
        }
        // At the end of the line, and the stop isn't here: turn back.
        boolean blocked = look.endOfLine && look.endDistance < 0.6 && stopped;
        if (blocked && ++stuck > 40) {
            stuck = 0;
            direction = -direction;
            choices.clear();
        } else if (!blocked) {
            stuck = 0;
        }
        // Lost: no stop ahead in range, and it hasn't been for a while: look again which way it is.
        if (!look.targetFound && --searchCooldown <= 0) {
            searchCooldown = 200;
            if (stopped) {
                chooseDirection = true;
            }
        }
        loco.updateAutoDisplay();
        return new Drive(direction, chooser, limit, true, target);
    }

    /**
     * Which way to go for the next stop: whichever way finds it nearer along the track, or else heads towards it.
     * Signals show which way the trains run on a line: a way where the first signal faces the other way counts as
     * going the wrong way, and is only taken if there's no other.
     */
    private int chooseDirection(Level level, List<TrainCarEntity> cars, int[] signs, Timetable.Stop stop) {
        double best = Double.POSITIVE_INFINITY;
        int bestDirection = 0;
        boolean[] wrongWay = new boolean[2];
        for (int d = 1; d >= -1; d -= 2) {
            int index = Lookahead.leadIndex(cars, d);
            int carDirection = d * signs[index];
            TrackPos bogie = Lookahead.leadBogie(cars, signs, d);
            boolean wrong = wrongWay(level, bogie, carDirection);
            wrongWay[d > 0 ? 0 : 1] = wrong;
            double distance = TrackScanner.distanceTo(level, bogie, carDirection, stop.track(), SEARCH);
            if (distance >= 0) {
                distance += wrong ? 1.0E6 : 0;
                if (distance < best) {
                    best = distance;
                    bestDirection = d;
                }
            }
        }
        if (bestDirection != 0) {
            return bestDirection;
        }
        if (wrongWay[0] != wrongWay[1]) {
            return wrongWay[0] ? -1 : 1;
        }
        Vec3 forward = cars.getFirst().flatForward().scale(signs[0]);
        Vec3 toStop = Vec3.atCenterOf(stop.track()).subtract(cars.getFirst().position());
        return forward.x * toStop.x + forward.z * toStop.z >= 0 ? 1 : -1;
    }

    /**
     * Wagons waiting to be picked up at a station: the nearest car ahead (going the way it's going, by its route) of a
     * train with no locomotive, if it's somewhere around the station (from {@link #WAITING_BEFORE} blocks short of it to
     * {@link #WAITING_BEYOND} past it). The distance is from buffer to buffer.
     */
    @Nullable
    private Trains.Ahead waitingWagons(Level level, List<TrainCarEntity> cars, int[] signs, double stationDistance) {
        List<TrainCarEntity> loose = new ArrayList<>();
        Map<TrainCarEntity, Boolean> hasEngine = new HashMap<>();
        for (TrainCarEntity car : Trains.loadedCars(level)) {
            if (cars.contains(car) || !car.isOnTrack() || car.isRemoved()) {
                continue;
            }
            List<TrainCarEntity> train = Trains.train(car);
            boolean engine = hasEngine.computeIfAbsent(train.getFirst(), head -> train.stream().anyMatch(c -> c instanceof Locomotive));
            if (!engine) {
                loose.add(car);
            }
        }
        if (loose.isEmpty()) {
            return null;
        }
        int index = Lookahead.leadIndex(cars, direction);
        TrainCarEntity lead = cars.get(index);
        double overhang = lead.halfLength() - lead.bogieOffset();
        TrackPos.Chooser route = (node, side, options, heading) -> choose(level, node, side, options, heading, false);
        Trains.Ahead found = Trains.nearestAlong(level, Lookahead.leadBogie(cars, signs, direction), direction * signs[index], loose,
                stationDistance + overhang + WAITING_BEYOND + 8, route);
        if (found == null) {
            return null;
        }
        TrainCarEntity car = found.car();
        double gap = found.distance() - overhang - (car.halfLength() - car.bogieOffset()) - Trains.BUFFER_GAP;
        if (gap < stationDistance - WAITING_BEFORE || gap > stationDistance + WAITING_BEYOND) {
            return null;
        }
        return new Trains.Ahead(car, Math.max(0, gap));
    }

    /**
     * Leaves cars at the station: up to the stop's number of them (or all), from the end of the train it won't be
     * leading with when it sets off for the next stop, stopping at the first locomotive or tender. They stay standing
     * there, a train of their own.
     */
    private void dropWagons(Level level, List<TrainCarEntity> cars, int[] signs, Timetable.Stop stop) {
        Timetable.Stop next = stops.get((index + 1) % stops.size());
        int leave = chooseDirection(level, cars, signs, next);
        int from = leave > 0 ? cars.size() - 1 : 0;
        int step = leave > 0 ? -1 : 1;
        int count = 0;
        for (int i = from; i >= 0 && i < cars.size() && count < stop.wagons(); i += step) {
            TrainCarEntity car = cars.get(i);
            if (car instanceof Locomotive || car instanceof Tender) {
                break;
            }
            count++;
        }
        String name = stationName(level, stop);
        if (count == 0 || count >= cars.size()) {
            announce(cars, Component.translatable("message.reikotrains.auto.nothing_to_leave", name));
            return;
        }
        Trains.split(cars, leave > 0 ? cars.size() - count - 1 : count - 1);
        announce(cars, Component.translatable("message.reikotrains.auto.left_wagons", count, name));
        ReikoTrains.LOGGER.debug("Train {} left {} cars at {}", loco.getUUID(), count, name);
    }

    private static String stationName(Level level, Timetable.Stop stop) {
        return level.isLoaded(stop.station()) && level.getBlockEntity(stop.station()) instanceof StationBlockEntity station
                ? station.name() : stop.name();
    }

    /** Whether the first signal along the track this way faces the other way (so this is the wrong way down the line). */
    private boolean wrongWay(Level level, TrackPos from, int direction) {
        boolean[] wrong = {false};
        TrackScanner.scan(level, from, direction, Lookahead.RANGE * 2, (node, side, options, heading) -> choose(level, node, side, options, heading, false),
                (node, arrivedSide, distance) -> {
                    if (TrackSide.signal(level, node, -arrivedSide) != null) {
                        return false;
                    }
                    if (TrackSide.signal(level, node, arrivedSide) != null) {
                        wrong[0] = true;
                        return false;
                    }
                    return true;
                });
        return wrong[0];
    }

    public boolean isDwelling() {
        return phase == Phase.DWELLING;
    }

    /** Which way it's going: +1 the train's forwards (for the tests). */
    public int direction() {
        return direction;
    }

    /** On a turntable, if its locomotive would have to run backwards to the next stop, it's turned round instead. */
    private void turnRound(Level level, List<TrainCarEntity> cars, int[] signs, int locoIndex) {
        if (stops.size() < 1) {
            return;
        }
        com.reiko.trains.turntable.TurntableBlockEntity table = com.reiko.trains.turntable.TurntableBlockEntity.under(level, cars);
        if (table == null) {
            return;
        }
        Timetable.Stop next = stops.get((index + 1) % stops.size());
        int way = chooseDirection(level, cars, signs, next);
        if (way * signs[locoIndex] < 0) {
            table.turnRound();
        }
    }

    private void arrive(Level level, List<TrainCarEntity> cars, Timetable.Stop stop) {
        phase = Phase.DWELLING;
        turnChecked = false;
        int dwell = level.isLoaded(stop.station()) && level.getBlockEntity(stop.station()) instanceof StationBlockEntity station
                ? station.dwellSeconds() : 10;
        timer = dwell * 20;
        String name = level.getBlockEntity(stop.station()) instanceof StationBlockEntity station ? station.name() : stop.name();
        announce(cars, Component.translatable("message.reikotrains.auto.arrived", name));
        ReikoTrains.LOGGER.debug("Train {} arrived at {}", loco.getUUID(), name);
    }

    private void depart(List<TrainCarEntity> cars) {
        index = (index + 1) % stops.size();
        phase = Phase.RUNNING;
        chooseDirection = true;
        choices.clear();
        searchCooldown = 200;
        loco.whistle();
        Timetable.Stop next = current();
        if (next != null) {
            announce(cars, Component.translatable("message.reikotrains.auto.next", next.name()));
        }
    }

    private static void announce(List<TrainCarEntity> cars, Component message) {
        for (TrainCarEntity car : cars) {
            for (Entity passenger : car.getPassengers()) {
                if (passenger instanceof Player player) {
                    player.displayClientMessage(message, true);
                }
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------------------

    void save(CompoundTag tag) {
        if (phase == Phase.OFF) {
            return;
        }
        CompoundTag auto = new CompoundTag();
        Timetable.CODEC.encodeStart(NbtOps.INSTANCE, stops).result().ifPresent(t -> auto.put("Stops", t));
        auto.putInt("Index", index);
        auto.putString("Phase", phase.name());
        auto.putInt("Timer", timer);
        auto.putInt("Direction", direction);
        tag.put("Autopilot", auto);
    }

    void load(CompoundTag tag) {
        stops.clear();
        phase = Phase.OFF;
        if (!tag.contains("Autopilot", Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag auto = tag.getCompound("Autopilot");
        Timetable.CODEC.parse(new Dynamic<>(NbtOps.INSTANCE, auto.get("Stops"))).result().ifPresent(stops::addAll);
        index = auto.getInt("Index");
        try {
            phase = Phase.valueOf(auto.getString("Phase"));
        } catch (IllegalArgumentException e) {
            phase = Phase.RUNNING;
        }
        timer = auto.getInt("Timer");
        direction = auto.getInt("Direction") < 0 ? -1 : 1;
        if (stops.isEmpty()) {
            phase = Phase.OFF;
        }
    }
}
