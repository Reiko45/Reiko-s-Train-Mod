package com.reiko.trains.train;

import com.mojang.serialization.Dynamic;
import com.reiko.trains.ReikoTrains;
import com.reiko.trains.config.TrainsConfig;
import com.reiko.trains.freight.LoaderMode;
import com.reiko.trains.freight.WagonLoaderBlockEntity;
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

    enum Phase {
        OFF, RUNNING, DWELLING
    }

    private final SteamLocomotive loco;
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

    Autopilot(SteamLocomotive loco) {
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
            if (loading != null) {
                return Component.translatable("hud.reikotrains.auto." + (loading == LoaderMode.LOAD ? "loading" : "unloading"), stop.name());
            }
            return Component.translatable("hud.reikotrains.auto.at", stop.name(), (timer + 19) / 20);
        }
        if (waitingAtSignal) {
            return Component.translatable("hud.reikotrains.auto.signal");
        }
        if (!loco.hasSteam()) {
            return Component.translatable("hud.reikotrains.auto.no_fire");
        }
        return Component.translatable("hud.reikotrains.auto.next", stop.name());
    }

    private boolean waitingAtSignal;
    /** While it's standing at a station: what a wagon loader's doing with the train, if anything. */
    @Nullable
    private LoaderMode loading;

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

    /** Run by the train each tick before it moves. */
    Drive plan(List<TrainCarEntity> cars, int[] signs, double speed) {
        Level level = loco.level();
        Timetable.Stop stop = current();
        if (stop == null) {
            stop();
            return Drive.NONE;
        }
        if (phase == Phase.DWELLING) {
            // It waits its time at the station, and then for any wagon loaders to finish with it.
            loading = WagonLoaderBlockEntity.busyWith(level, cars);
            if (timer > 0) {
                timer--;
            }
            if (timer <= 0 && loading == null) {
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
        Lookahead look = Lookahead.look(cars, signs, direction, chooser, stop.track());
        loco.showLookahead(look);
        double stopAt = Math.min(look.signalStop, Math.min(look.targetFound ? look.targetDistance : Double.POSITIVE_INFINITY,
                look.endOfLine ? look.endDistance - 0.05 : Double.POSITIVE_INFINITY));
        double limit = Drive.stoppingSpeed(stopAt);
        double target = Math.min(loco.maxSpeed(), limit);
        boolean stopped = Math.abs(speed) < 0.002;
        waitingAtSignal = look.signalStop != Double.POSITIVE_INFINITY && look.signalStop <= look.targetDistance;
        if (look.targetFound && Math.abs(look.targetDistance) < 0.6 && stopped && !waitingAtSignal) {
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

    private void arrive(Level level, List<TrainCarEntity> cars, Timetable.Stop stop) {
        phase = Phase.DWELLING;
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
