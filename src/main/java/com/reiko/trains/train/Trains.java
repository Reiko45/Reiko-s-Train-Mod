package com.reiko.trains.train;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.config.TrainsConfig;
import com.reiko.trains.track.EdgeRef;
import com.reiko.trains.track.TrackEdge;
import com.reiko.trains.track.TrackGraph;
import com.reiko.trains.track.TrackPos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Trains as a whole: which cars make up a train, moving them together along the track, and coupling them.
 *
 * <p>Every car keeps its place in the train by distance along the track: each tick the first car works out the
 * train's speed (the locomotive's pull, gravity on slopes, friction and brakes) and then moves every bogie in the
 * train the same distance along the track. The bogie at the very front (in the way the train's going) chooses the
 * way at junctions, and the rest follow its choices.
 */
public final class Trains {
    /** Space left between the buffers of two coupled cars, in blocks. */
    public static final double BUFFER_GAP = 0.05;
    /** How far apart (buffer to buffer, along the track) two cars can be and still be coupled. */
    public static final double COUPLING_REACH = 5.0;
    /** Gravity along a slope: blocks per tick per tick, per unit of slope. */
    private static final double GRAVITY = 0.008;
    private static final double ROLLING_RESISTANCE = 0.0001;
    private static final double DRAG = 0.0012;
    private static final double BRAKE = 0.015;
    /** No train goes faster than this, even downhill: blocks per tick. */
    private static final double HARD_LIMIT = 1.5;
    public static final TicketType<ChunkPos> TICKET = TicketType.create(ReikoTrains.MODID + ":train", Comparator.comparingLong(ChunkPos::toLong), 60);

    private Trains() {
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Finding the train

    @Nullable
    static TrainCarEntity resolve(TrainCarEntity car, @Nullable UUID id, boolean leader) {
        if (id == null) {
            return null;
        }
        Entity cached = leader ? car.leaderCache : car.followerCache;
        if (cached instanceof TrainCarEntity found && !cached.isRemoved() && id.equals(cached.getUUID())) {
            return found;
        }
        TrainCarEntity found = car.level() instanceof ServerLevel level && level.getEntity(id) instanceof TrainCarEntity other && !other.isRemoved()
                ? other : null;
        if (leader) {
            car.leaderCache = found;
        } else {
            car.followerCache = found;
        }
        return found;
    }

    @Nullable
    public static TrainCarEntity leader(TrainCarEntity car) {
        return resolve(car, car.leader, true);
    }

    @Nullable
    public static TrainCarEntity follower(TrainCarEntity car) {
        return resolve(car, car.follower, false);
    }

    /** The first car of the train this car is in (as far as it's loaded). */
    @Nullable
    public static TrainCarEntity head(TrainCarEntity car) {
        TrainCarEntity head = car;
        for (int guard = 0; guard < 256 && head.leader != null; guard++) {
            TrainCarEntity next = leader(head);
            if (next == null || next == car) {
                return next == null ? null : head;
            }
            head = next;
        }
        return head;
    }

    /** Every loaded car of the train this car is in, front to back. */
    public static List<TrainCarEntity> train(TrainCarEntity car) {
        TrainCarEntity head = head(car);
        List<TrainCarEntity> cars = new ArrayList<>();
        for (TrainCarEntity c = head == null ? car : head; c != null && cars.size() < 256 && !cars.contains(c); c = follower(c)) {
            cars.add(c);
        }
        return cars;
    }

    /** Which way each car faces in its train: +1 the way the train's going when it goes forwards, -1 the other way. */
    static int[] signs(List<TrainCarEntity> cars) {
        int[] signs = new int[cars.size()];
        int sign = 1;
        for (int i = 0; i < cars.size(); i++) {
            if (cars.get(i).flipped) {
                sign = -sign;
            }
            signs[i] = sign;
        }
        return signs;
    }

    /** +1 if {@code car} faces its train's forwards, -1 if not. */
    public static int sign(TrainCarEntity head, TrainCarEntity car) {
        List<TrainCarEntity> cars = train(head);
        int index = cars.indexOf(car);
        return index < 0 ? 1 : signs(cars)[index];
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Moving

    /** Run by the first car of each train, every tick: speed, then moving every car. */
    static void tickTrain(TrainCarEntity head) {
        List<TrainCarEntity> cars = new ArrayList<>();
        for (TrainCarEntity c = head; c != null; ) {
            cars.add(c);
            if (c.follower == null || cars.size() >= 256) {
                break;
            }
            TrainCarEntity next = follower(c);
            if (next == null) {
                // A coupled car isn't loaded: wait for it (a while) rather than leave it behind.
                if (++head.waiting > 200) {
                    ReikoTrains.LOGGER.warn("Train car {} lost the car coupled behind it ({}); uncoupling", c.getUUID(), c.follower);
                    c.follower = null;
                    head.waiting = 0;
                }
                return;
            }
            if (!c.getUUID().equals(next.leader)) {
                c.follower = null; // a one-sided coupling
                break;
            }
            c = next;
        }
        head.waiting = 0;
        for (TrainCarEntity car : cars) {
            if (!car.front.isValid(car.level()) || !car.rear.isValid(car.level())) {
                car.derail();
                return;
            }
        }
        int[] signs = signs(cars);
        head.speed = nextSpeed(head.speed, cars, signs);
        if (Math.abs(head.speed) > 1.0E-6) {
            double wanted = head.speed;
            double moved = move(cars, signs, wanted, head);
            if (moved < Math.abs(wanted) - 1.0E-6) {
                head.speed = 0; // hit the end of the track
            }
        }
        if (head.tickCount % 10 == 0 && (Math.abs(head.speed) > 0.001 || hasDriver(cars))) {
            keepLoaded(cars);
        }
    }

    private static boolean hasDriver(List<TrainCarEntity> cars) {
        for (TrainCarEntity car : cars) {
            if (car instanceof SteamLocomotive loco && loco.driver() != null) {
                return true;
            }
        }
        return false;
    }

    /** The train's speed next tick: the locomotive's pull, gravity, friction and brakes. */
    static double nextSpeed(double speed, List<TrainCarEntity> cars, int[] signs) {
        double mass = 0;
        double gravity = 0;
        SteamLocomotive loco = null;
        int locoSign = 1;
        for (int i = 0; i < cars.size(); i++) {
            TrainCarEntity car = cars.get(i);
            mass += car.mass();
            gravity -= GRAVITY * car.mass() * car.slope() * signs[i];
            if (car instanceof SteamLocomotive l) {
                loco = l;
                locoSign = signs[i];
            }
        }
        double accel = gravity / mass;
        if (loco != null) {
            accel += loco.traction(speed * locoSign, mass) * locoSign;
        }
        double next = speed + accel;
        double resistance = ROLLING_RESISTANCE + Math.abs(next) * DRAG;
        next = Math.abs(next) <= resistance ? 0 : next - Math.signum(next) * resistance;
        if (loco != null && loco.isHeld()) {
            next = Math.abs(next) <= BRAKE ? 0 : next - Math.signum(next) * BRAKE;
        }
        return Math.max(-HARD_LIMIT, Math.min(HARD_LIMIT, next));
    }

    /**
     * Moves every car of a train {@code distance} blocks along the track (positive the way the first car faces).
     * Returns how far it got: less, if the front of the train reached the end of the track.
     */
    static double move(List<TrainCarEntity> cars, int[] signs, double distance, TrainCarEntity head) {
        Level level = head.level();
        int leadIndex = distance > 0 ? 0 : cars.size() - 1;
        TrainCarEntity leadCar = cars.get(leadIndex);
        double leadDelta = distance * signs[leadIndex];
        boolean leadIsFront = leadDelta > 0;
        TrackPos probe = (leadIsFront ? leadCar.front : leadCar.rear).copy();
        int steer = steering(cars);
        TrackPos.Chooser leaderChooser = (node, side, options, heading) -> {
            EdgeRef chosen = TrackGraph.choose(options, heading, steer);
            head.routes.put(routeKey(node.asLong(), side), chosen);
            return chosen;
        };
        TrackPos.Chooser followerChooser = (node, side, options, heading) -> {
            EdgeRef remembered = head.routes.get(routeKey(node.asLong(), side));
            return remembered != null && options.contains(remembered) ? remembered : TrackGraph.choose(options, heading, 0);
        };
        double moved = probe.walk(level, leadDelta, leaderChooser);
        double actual = Math.signum(distance) * moved;
        for (int i = 0; i < cars.size(); i++) {
            TrainCarEntity car = cars.get(i);
            double delta = actual * signs[i];
            if (car == leadCar) {
                if (leadIsFront) {
                    car.front = probe;
                    car.rear.walk(level, delta, followerChooser);
                } else {
                    car.rear = probe;
                    car.front.walk(level, delta, followerChooser);
                }
            } else {
                // Walk the bogie nearer the front of the train's motion first, so junction choices are in order.
                if (delta > 0) {
                    car.front.walk(level, delta, followerChooser);
                    car.rear.walk(level, delta, followerChooser);
                } else {
                    car.rear.walk(level, delta, followerChooser);
                    car.front.walk(level, delta, followerChooser);
                }
            }
            car.updatePose();
        }
        return moved;
    }

    private static long routeKey(long node, int side) {
        return node * 2 + (side > 0 ? 1 : 0);
    }

    private static int steering(List<TrainCarEntity> cars) {
        for (TrainCarEntity car : cars) {
            if (car instanceof SteamLocomotive loco) {
                return loco.getSteer();
            }
        }
        return 0;
    }

    /** Keeps the chunks around a moving train loaded, so it doesn't stop dead at the edge of the loaded world. */
    private static void keepLoaded(List<TrainCarEntity> cars) {
        if (!(cars.getFirst().level() instanceof ServerLevel level)) {
            return;
        }
        for (int i = 0; i < cars.size(); i++) {
            TrainCarEntity car = cars.get(i);
            ChunkPos chunk = car.chunkPosition();
            boolean end = i == 0 || i == cars.size() - 1;
            level.getChunkSource().addRegionTicket(TICKET, chunk, end ? 4 : 2, chunk);
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Coupling
    //
    // A train is a chain of cars, first to last, and the first car is always at the front of the train: "forwards"
    // for the train points out of its first car's free end. Each car's {@code flipped} says whether it faces the
    // other way from the car before it (or, for the first car, from the train's forwards).

    public enum CoupleResult {
        COUPLED("coupled"),
        SAME_CAR("same_car"),
        SAME_TRAIN("same_train"),
        TOO_FAR("too_far"),
        NOT_ON_TRACK("not_on_track"),
        NOT_AN_END("not_an_end"),
        TWO_LOCOMOTIVES("two_locomotives"),
        TOO_LONG("too_long"),
        MOVING("moving");

        private final String key;

        CoupleResult(String key) {
            this.key = key;
        }

        public Component message() {
            return Component.translatable("message.reikotrains.couple." + key);
        }
    }

    /** How far a car's buffers stick out past its bogie. */
    private static double overhang(TrainCarEntity car) {
        return car.halfLength() - car.bogieOffset();
    }

    /**
     * Couples two cars standing on the same line of track, the ends of two trains (or two lone cars). The second
     * car's train is drawn up until the buffers meet. A train can have one locomotive, anywhere in it: it pulls or
     * pushes.
     */
    public static CoupleResult couple(TrainCarEntity first, TrainCarEntity second) {
        if (first == second) {
            return CoupleResult.SAME_CAR;
        }
        if (!first.isOnTrack() || !second.isOnTrack() || first.level() != second.level()) {
            return CoupleResult.NOT_ON_TRACK;
        }
        double reach = overhang(first) + COUPLING_REACH + second.halfLength() + second.bogieOffset() + 1;
        if (first.distanceTo(second) > first.halfLength() + second.halfLength() + COUPLING_REACH + 1) {
            return CoupleResult.TOO_FAR;
        }
        List<TrainCarEntity> trainA = train(first);
        List<TrainCarEntity> trainB = train(second);
        if (trainA.contains(second)) {
            return CoupleResult.SAME_TRAIN;
        }
        if (trainA.stream().anyMatch(TrainCarEntity::isLocomotive) && trainB.stream().anyMatch(TrainCarEntity::isLocomotive)) {
            return CoupleResult.TWO_LOCOMOTIVES;
        }
        if (trainA.size() + trainB.size() > TrainsConfig.MAX_TRAIN_LENGTH.getAsInt()) {
            return CoupleResult.TOO_LONG;
        }
        if (Math.abs(trainA.getFirst().speed) > 0.05 || Math.abs(trainB.getFirst().speed) > 0.05) {
            return CoupleResult.MOVING;
        }
        // Which end of the first car the second is at, and which of the second car's bogies faces it.
        Found ahead = findAlong(first.level(), first.front, 1, second, reach);
        Found behind = findAlong(first.level(), first.rear, -1, second, reach);
        Found found = ahead == null ? behind : behind == null ? ahead : ahead.distance <= behind.distance ? ahead : behind;
        if (found == null) {
            return CoupleResult.NOT_ON_TRACK;
        }
        boolean firstEndFront = found == ahead;
        boolean secondEndFront = found.front;
        if (!isOuterEnd(trainA, first, firstEndFront) || !isOuterEnd(trainB, second, secondEndFront)) {
            return CoupleResult.NOT_AN_END;
        }
        double gap = found.distance - overhang(first) - BUFFER_GAP - overhang(second);
        if (gap > COUPLING_REACH) {
            return CoupleResult.TOO_FAR;
        }

        // Turn the first train round so the first car is its last, and the second so the second car is its first.
        if (trainA.size() > 1 && trainA.getFirst() == first) {
            trainA = reverse(trainA);
        }
        if (trainB.size() > 1 && trainB.getLast() == second) {
            trainB = reverse(trainB);
        }
        int[] signsA = signs(trainA);
        int firstSign = signsA[signsA.length - 1];
        // The first car's back (in the train's terms) has to be the end facing the second car. A lone car can just
        // be turned to suit.
        if ((firstSign < 0) != firstEndFront) {
            first.flipped = !first.flipped;
            firstSign = -firstSign;
        }
        // Draw the second train up, moving the second car towards the first.
        int[] signsB = signs(trainB);
        move(trainB, signsB, gap * (secondEndFront ? 1 : -1) * signsB[0], second);
        first.follower = second.getUUID();
        first.followerCache = second;
        second.leader = first.getUUID();
        second.leaderCache = first;
        int secondSign = secondEndFront ? 1 : -1;
        second.flipped = secondSign != firstSign;
        trainA.getFirst().speed = 0;
        second.speed = 0;
        second.routes.clear();
        return CoupleResult.COUPLED;
    }

    /** Whether this end of the car is free: at the front of the train's first car or the back of its last. */
    private static boolean isOuterEnd(List<TrainCarEntity> train, TrainCarEntity car, boolean front) {
        if (train.size() == 1) {
            return true;
        }
        int[] signs = signs(train);
        int index = train.indexOf(car);
        if (index == 0) {
            return front == (signs[0] > 0);
        }
        if (index == train.size() - 1) {
            return front == (signs[index] < 0);
        }
        return false;
    }

    /**
     * Turns a train's couplings round, end for end: its last car becomes its first. Each car keeps facing the way it
     * does. Returns the cars in their new order.
     */
    private static List<TrainCarEntity> reverse(List<TrainCarEntity> cars) {
        int n = cars.size();
        int[] signs = signs(cars);
        boolean[] oldFlips = new boolean[n];
        for (int i = 0; i < n; i++) {
            oldFlips[i] = cars.get(i).flipped;
        }
        for (int i = 0; i < n; i++) {
            TrainCarEntity car = cars.get(i);
            UUID leader = car.leader;
            car.leader = car.follower;
            car.follower = leader;
            Entity cache = car.leaderCache;
            car.leaderCache = car.followerCache;
            car.followerCache = cache;
            // Its new leader is the car that was behind it; whether they face the same way hasn't changed.
            car.flipped = i < n - 1 && oldFlips[i + 1];
            car.routes.clear();
            car.speed = 0;
        }
        // The new first car: the train's forwards now points out of its free end, the old backwards.
        cars.getLast().flipped = signs[n - 1] > 0;
        return new ArrayList<>(cars).reversed();
    }

    private record Found(double distance, boolean front) {
    }

    /**
     * Walks along the track from {@code start} (the way {@code direction}, +1 forwards and -1 backwards in car terms)
     * looking for one of {@code target}'s bogies, up to {@code maxDistance} blocks. Takes the straightest way at
     * junctions.
     */
    @Nullable
    private static Found findAlong(Level level, TrackPos start, double direction, TrainCarEntity target, double maxDistance) {
        TrackPos pos = start.copy();
        double travelled = 0;
        for (int guard = 0; guard < 32; guard++) {
            EdgeRef edge = pos.edge();
            TrackEdge shape = edge.geometry();
            boolean along = (direction >= 0) == pos.facingAlong();
            double here = pos.distance();
            Found best = null;
            for (boolean isFront : new boolean[]{true, false}) {
                TrackPos bogie = isFront ? target.front : target.rear;
                if (bogie == null || !bogie.edge().sameTrack(edge)) {
                    continue;
                }
                double there = bogie.edge().equals(edge) ? bogie.distance() : shape.length() - bogie.distance();
                double gap = along ? there - here : here - there;
                if (gap >= -1.0E-4 && (best == null || travelled + gap < best.distance)) {
                    best = new Found(travelled + Math.max(0, gap), isFront);
                }
            }
            if (best != null) {
                return best;
            }
            double room = along ? shape.length() - here : here;
            if (travelled + room > maxDistance) {
                return null;
            }
            double step = room + 1.0E-4;
            double moved = pos.walk(level, direction * step, TrackPos.STRAIGHTEST);
            if (moved < step - 1.0E-6) {
                return null;
            }
            travelled += step;
        }
        return null;
    }

    /**
     * Uncouples a car from the cars either side. Each piece of the train keeps going the way it was, at the speed it
     * was.
     */
    public static int uncouple(TrainCarEntity car) {
        List<TrainCarEntity> cars = train(car);
        int[] signs = signs(cars);
        double speed = cars.getFirst().speed;
        int index = cars.indexOf(car);
        int count = 0;
        if (car.leader != null) {
            TrainCarEntity leader = leader(car);
            if (leader != null && car.getUUID().equals(leader.follower)) {
                leader.follower = null;
                leader.followerCache = null;
            }
            car.leader = null;
            car.leaderCache = null;
            if (index >= 0) {
                car.flipped = signs[index] < 0;
                car.speed = speed;
            }
            car.routes.clear();
            count++;
        }
        if (car.follower != null) {
            TrainCarEntity follower = follower(car);
            if (follower != null && car.getUUID().equals(follower.leader)) {
                int followerIndex = cars.indexOf(follower);
                follower.leader = null;
                follower.leaderCache = null;
                follower.flipped = followerIndex >= 0 && signs[followerIndex] < 0;
                follower.speed = speed;
                follower.routes.clear();
            }
            car.follower = null;
            car.followerCache = null;
            count++;
        }
        return count;
    }

    /** Whether a car could go at this spot: no other car is already on the track there. */
    public static boolean clear(Level level, TrainCarEntity car) {
        Vec3 flat = car.flatForward();
        for (TrainCarEntity other : level.getEntitiesOfClass(TrainCarEntity.class, car.getBoundingBox().inflate(car.halfLength() + 4))) {
            if (other == car) {
                continue;
            }
            Vec3 d = other.position().subtract(car.position());
            double along = Math.abs(d.x * flat.x + d.z * flat.z);
            double side = Math.abs(d.x * -flat.z + d.z * flat.x);
            if (side < 1.8 && Math.abs(d.y) < 2.5 && along < car.halfLength() + other.halfLength() - 0.2) {
                return false;
            }
        }
        return true;
    }
}
