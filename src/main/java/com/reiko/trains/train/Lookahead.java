package com.reiko.trains.train;

import com.reiko.trains.signal.SignalBlockEntity;
import com.reiko.trains.signal.SpeedSignBlockEntity;
import com.reiko.trains.signal.StationBlockEntity;
import com.reiko.trains.signal.TrackSide;
import com.reiko.trains.track.EdgeRef;
import com.reiko.trains.track.TrackGraph;
import com.reiko.trains.track.TrackPos;
import com.reiko.trains.track.TrackScanner;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * What's on the track ahead of a train, measured from its leading buffer: the next signal (and whether it's clear),
 * the next station, the stop it's heading for, the speed signs, and the end of the line.
 */
final class Lookahead {
    /** How far ahead a train looks, in blocks: at least this, ... */
    static final double RANGE = 80;
    /** ... and further going fast (enough to stop in), up to this. */
    static final double MAX_RANGE = 240;
    /** How far short of a red signal trains stop. */
    static final double SIGNAL_MARGIN = 1.0;

    /** The next signal: how far, and whether it's clear. Negative distance if there's none in range. */
    double signalDistance = -1;
    boolean signalClear = true;
    /** ... whether it's showing yellow (a three-light signal, clear, but the one after it red), and where it is. */
    boolean signalCaution;
    /** The next signal shows double yellow (a four-aspect signal, the one after it yellow). */
    boolean signalAdvance;
    @Nullable
    BlockPos signalPos;
    /** Where the train has to stop for a red signal (infinite if it doesn't). */
    double signalStop = Double.POSITIVE_INFINITY;
    @Nullable
    StationBlockEntity station;
    double stationDistance = -1;
    /** The stop being made for (from {@code target}): found, and how far. */
    boolean targetFound;
    double targetDistance = Double.POSITIVE_INFINITY;
    /** The next switch: how far, and which way it's set (-1 left, 0 straight on, +1 right). Negative distance if none. */
    double pointsDistance = -1;
    int pointsTurn;
    /** The speed signs facing the train, nearest first (from just behind its leading buffer). */
    final List<SpeedSign> speedSigns = new ArrayList<>();
    private double lastBuffer;
    boolean endOfLine;
    double endDistance = Double.POSITIVE_INFINITY;
    /** The last track block before the end of the line, if it's ahead. */
    @Nullable
    BlockPos endNode;
    private BlockPos lastNode;

    /** For a tram ({@link StreetRunning}): where it has to stop short of something on the track ahead (infinite if nothing). */
    double obstacleStop = Double.POSITIVE_INFINITY;
    /** For a tram: what's on the track just ahead of it (within a few blocks), and the way along the track there. */
    final List<Entity> nearObstacles = new ArrayList<>();
    final List<Vec3> nearHeadings = new ArrayList<>();
    final List<Vec3> nearPoints = new ArrayList<>();
    /** ... and for a red traffic light beside the line (Reiko's Car Mod's), facing it. */
    double lightStop = Double.POSITIVE_INFINITY;
    /** How hard it brakes to stop for them all: a tram brakes harder than a train. */
    double brake = Drive.SERVICE_BRAKE;

    /** How far short of something on the track a tram stops, and of a traffic light. */
    static final double OBSTACLE_MARGIN = 1.0;
    static final double LIGHT_MARGIN = 1.5;
    /** How far a tram looks for things on the track: at least this far, more going fast, up to {@link #STREET_RANGE}. */
    static final double STREET_MIN_RANGE = 16;
    static final double STREET_RANGE = 48;
    /** How far to the side of the track (blocks) a traffic light can be and still be for the tram. */
    static final double LIGHT_REACH = 6.5;
    /** Half the width of track a tram keeps clear, and how high. */
    static final double CLEARANCE = 1.05;
    static final double CLEARANCE_HEIGHT = 2.4;
    private static final ResourceLocation TRAFFIC_LIGHT = ResourceLocation.fromNamespaceAndPath("reikocars", "traffic_light");

    private Lookahead() {
    }

    /** Whether a train keeps to street rules: it has a tram in it. */
    static boolean streetRunning(List<TrainCarEntity> cars) {
        for (TrainCarEntity car : cars) {
            if (car instanceof StreetRunning) {
                return true;
            }
        }
        return false;
    }

    /** Where a train has to stop for anything at all ahead (a red signal or light, something on the track), or infinity. */
    double stopFor() {
        return Math.min(signalStop, Math.min(lightStop, obstacleStop));
    }

    /** A speed sign the train's coming to: where it is, how far ahead, and its limit (infinite at an end of limit). */
    record SpeedSign(BlockPos pos, double distance, double limit) {
    }

    /** The car at the front of the train going {@code direction} (+1 the train's forwards). */
    static int leadIndex(List<TrainCarEntity> cars, int direction) {
        return direction > 0 ? 0 : cars.size() - 1;
    }

    /** The bogie at the front of the train going {@code direction}. */
    static TrackPos leadBogie(List<TrainCarEntity> cars, int[] signs, int direction) {
        int index = leadIndex(cars, direction);
        TrainCarEntity car = cars.get(index);
        return direction * signs[index] > 0 ? car.front : car.rear;
    }

    /** How far a train going {@code speed} looks ahead: far enough to stop from that speed, and a bit. */
    static double range(double speed) {
        return Math.max(RANGE, Math.min(MAX_RANGE, speed * speed / (2 * Drive.SERVICE_BRAKE) + 24));
    }

    static Lookahead look(List<TrainCarEntity> cars, int[] signs, int direction, TrackPos.Chooser chooser, @Nullable BlockPos target,
            double speed) {
        Lookahead look = new Lookahead();
        int index = leadIndex(cars, direction);
        TrainCarEntity car = cars.get(index);
        Level level = car.level();
        int carDirection = direction * signs[index];
        TrackPos bogie = carDirection > 0 ? car.front : car.rear;
        double overhang = car.halfLength() - car.bogieOffset();
        // Watches the way the train will go at the first switch ahead (the scan visits a track block, then chooses).
        TrackPos.Chooser watching = (node, side, options, heading) -> {
            EdgeRef way = chooser.choose(node, side, options, heading);
            if (look.pointsDistance < 0 && options.size() > 1 && look.lastBuffer > -0.25 && TrackSide.switchAt(level, node, side) != null) {
                look.pointsDistance = Math.max(0, look.lastBuffer);
                look.pointsTurn = turn(options, heading, way);
            }
            return way;
        };
        TrackScanner.End end = TrackScanner.scan(level, bogie, carDirection, range(speed), watching, (node, arrivedSide, distance) -> {
            double buffer = distance - overhang;
            look.lastBuffer = buffer;
            look.lastNode = node;
            if (target != null && node.equals(target)) {
                look.targetFound = true;
                look.targetDistance = buffer;
            }
            if (buffer > -0.25 && look.signalDistance < 0) {
                SignalBlockEntity signal = TrackSide.signal(level, node, -arrivedSide);
                if (signal != null) {
                    look.signalDistance = buffer;
                    look.signalPos = signal.getBlockPos();
                    look.signalClear = signal.isClearFor(cars);
                    look.signalCaution = look.signalClear && signal.showsCaution();
                    look.signalAdvance = look.signalClear && signal.showsAdvance();
                    if (!look.signalClear) {
                        look.signalStop = buffer - SIGNAL_MARGIN;
                    }
                }
            }
            SpeedSignBlockEntity speedSign = TrackSide.speedSign(level, node, -arrivedSide);
            if (speedSign != null) {
                look.speedSigns.add(new SpeedSign(speedSign.getBlockPos(), buffer, speedSign.limit()));
            }
            if (look.station == null && buffer > -2.5) {
                StationBlockEntity station = TrackSide.station(level, node);
                if (station != null) {
                    look.station = station;
                    look.stationDistance = buffer;
                }
            }
            // Nothing beyond the stop being made for, or a red signal, matters yet.
            return !look.targetFound && look.signalStop == Double.POSITIVE_INFINITY;
        });
        if (end.endOfTrack()) {
            look.endOfLine = true;
            look.endDistance = end.distance() - overhang;
            look.endNode = look.lastNode;
        }
        if (streetRunning(cars)) {
            look.brake = Drive.TRAM_BRAKE;
            look.streetRules(cars, bogie, carDirection, overhang, chooser, Math.abs(speed));
        }
        return look;
    }

    /** A point on the track ahead: how far from the leading buffer, where, and which way the track runs there. */
    private record Sample(double buffer, Vec3 at, Vec3 heading) {
    }

    /**
     * A tram's look along the track ahead, as far as it takes to stop and a bit: the first thing on the track in its
     * way (a person, an animal, a car or boat, another train), and the first red traffic light beside the line facing
     * it (or an amber one it can stop for).
     */
    private void streetRules(List<TrainCarEntity> cars, TrackPos bogie, int carDirection, double overhang, TrackPos.Chooser chooser, double speed) {
        Level level = cars.getFirst().level();
        double reach = Math.min(STREET_RANGE, Math.max(STREET_MIN_RANGE, speed * speed / (2 * Drive.TRAM_BRAKE) + 10));
        List<Sample> samples = new ArrayList<>();
        TrackPos probe = bogie.copy();
        // From the leading buffer on, every half block.
        double buffer = -overhang;
        double step = overhang;
        while (buffer <= reach) {
            if (step > 0 && probe.walk(level, carDirection * step, chooser) < step - 1.0E-6) {
                break;
            }
            buffer += step;
            step = 0.5;
            samples.add(new Sample(buffer, probe.position(), probe.facing().scale(carDirection)));
        }
        if (samples.isEmpty()) {
            return;
        }
        AABB around = null;
        for (Sample sample : samples) {
            AABB box = clearance(sample.at());
            around = around == null ? box : around.minmax(box);
        }
        for (Entity entity : level.getEntities((Entity) null, around, e -> inTheWay(e, cars))) {
            AABB body = entity.getBoundingBox();
            for (Sample sample : samples) {
                if (sample.buffer() - OBSTACLE_MARGIN >= obstacleStop) {
                    break;
                }
                if (clearance(sample.at()).intersects(body)) {
                    obstacleStop = Math.max(0, sample.buffer() - OBSTACLE_MARGIN);
                    if (sample.buffer() < NEAR) {
                        nearObstacles.add(entity);
                        nearHeadings.add(sample.heading());
                        nearPoints.add(sample.at());
                    }
                    break;
                }
            }
        }
        lights(level, samples, around.inflate(LIGHT_REACH, 4, LIGHT_REACH), speed);
    }

    /** How close (blocks ahead of the buffers) something on the track has to be to be nudged off it. */
    private static final double NEAR = 4.0;

    /**
     * A tram that's waited a while for animals or mobs on the track ahead gives them a nudge off it, sideways, as
     * trains do. People, cars and other trains it still waits for.
     */
    void nudgeAnimals() {
        for (int i = 0; i < nearObstacles.size(); i++) {
            Entity entity = nearObstacles.get(i);
            if (!(entity instanceof net.minecraft.world.entity.Mob) || entity instanceof net.minecraft.world.entity.npc.AbstractVillager || !entity.isAlive()) {
                continue;
            }
            Vec3 heading = nearHeadings.get(i);
            double flat = Math.sqrt(heading.x * heading.x + heading.z * heading.z);
            if (flat < 1.0E-6) {
                continue;
            }
            double hx = heading.x / flat;
            double hz = heading.z / flat;
            // Which side of the track it's nearer, from the middle of it: off that way.
            Vec3 at = entity.position().subtract(nearPoints.get(i));
            double side = (entity.getId() & 1) == 0 ? 1 : -1;
            double right = -hz * at.x + hx * at.z;
            if (Math.abs(right) > 0.05) {
                side = Math.signum(right);
            }
            // Shuffled along a little each tick (whether or not it's minded to move), and given a push the same way.
            entity.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(-hz * 0.15 * side, 0, hx * 0.15 * side));
            entity.push(-hz * 0.1 * side, 0.0, hx * 0.1 * side);
            entity.hurtMarked = true;
        }
    }

    private static AABB clearance(Vec3 at) {
        return new AABB(at.x - CLEARANCE, at.y + 0.05, at.z - CLEARANCE, at.x + CLEARANCE, at.y + CLEARANCE_HEIGHT, at.z + CLEARANCE);
    }

    /** What a tram stops for: people and animals, cars and boats, and other trains (not its own, or anyone riding). */
    private static boolean inTheWay(Entity entity, List<TrainCarEntity> cars) {
        if (!entity.isAlive() || entity.isSpectator() || entity.isPassenger()) {
            return false;
        }
        if (entity instanceof PartEntity<?> part) {
            return part.getParent() instanceof TrainCarEntity car && !cars.contains(car);
        }
        if (entity instanceof TrainCarEntity car) {
            return !cars.contains(car);
        }
        return entity instanceof LivingEntity || entity instanceof VehicleEntity;
    }

    /**
     * Traffic lights (Reiko's Car Mod's) beside the track ahead and facing the tram: it stops short of a red one, and of
     * an amber one if it can stop in time. They show on the gauges like a signal.
     */
    private void lights(Level level, List<Sample> samples, AABB area, double speed) {
        BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(TRAFFIC_LIGHT);
        if (type == null) {
            return;
        }
        for (int cx = ((int) Math.floor(area.minX)) >> 4; cx <= ((int) Math.floor(area.maxX)) >> 4; cx++) {
            for (int cz = ((int) Math.floor(area.minZ)) >> 4; cz <= ((int) Math.floor(area.maxZ)) >> 4; cz++) {
                if (!(level.getChunkSource().getChunkNow(cx, cz) instanceof LevelChunk chunk)) {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (entity.getType() == type && area.contains(Vec3.atCenterOf(entity.getBlockPos()))) {
                        light(entity.getBlockPos(), entity.getBlockState(), samples, speed);
                    }
                }
            }
        }
    }

    private void light(BlockPos pos, BlockState state, List<Sample> samples, double speed) {
        Direction facing = null;
        String shows = null;
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals("facing") && state.getValue(property) instanceof Direction direction) {
                facing = direction;
            } else if (property.getName().equals("signal")) {
                shows = valueName(state, property);
            }
        }
        if (facing == null || shows == null || shows.equals("green")) {
            return;
        }
        Vec3 at = Vec3.atCenterOf(pos);
        Sample nearest = null;
        double best = LIGHT_REACH;
        for (Sample sample : samples) {
            double dx = at.x - sample.at().x;
            double dz = at.z - sample.at().z;
            double flat = Math.sqrt(dx * dx + dz * dz);
            double up = at.y - sample.at().y;
            if (flat < best && up > -1.5 && up < 7) {
                best = flat;
                nearest = sample;
            }
        }
        // It has to face the tram coming (its face towards it), and be ahead of it.
        if (nearest == null || facing.getStepX() * nearest.heading().x + facing.getStepZ() * nearest.heading().z > -0.5) {
            return;
        }
        double stop = nearest.buffer() - LIGHT_MARGIN;
        if (stop < -0.3) {
            return;
        }
        if (shows.equals("yellow") && speed > Drive.stoppingSpeed(Math.max(0, stop), Drive.TRAM_BRAKE) + 0.02) {
            return; // too close to stop for it: on through
        }
        lightStop = Math.min(lightStop, Math.max(0, stop));
        if (signalDistance < 0 || nearest.buffer() < signalDistance) {
            signalDistance = Math.max(0, nearest.buffer());
            signalClear = false;
        }
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    /** Which way {@code way} goes, of these: -1 bending left, 0 the straightest, +1 bending right. */
    static int turn(List<EdgeRef> options, Vec3 heading, EdgeRef way) {
        if (way.equals(TrackGraph.choose(options, heading, 0))) {
            return 0;
        }
        return way.equals(TrackGraph.choose(options, heading, 1)) ? 1 : -1;
    }
}
