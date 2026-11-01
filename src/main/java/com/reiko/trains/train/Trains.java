package com.reiko.trains.train;

import com.reiko.trains.config.TrainsConfig;
import com.reiko.trains.item.CouplerItem;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * How train cars behave together: which way a car faces, couplings, and how a coupled car follows the one in front
 * of it. The car classes (which can't share a superclass, since the wagon is a container minecart) hand their
 * overrides to the methods here.
 */
public final class Trains {
    /** Space left between the buffers of two coupled cars, in blocks. */
    public static final double BUFFER_GAP = 0.06;
    /** How far apart (beyond their spacing) two coupled cars can get before the coupling snaps. */
    public static final double SNAP_SLACK = 6.0;
    /** The furthest apart two cars can be and still be coupled with the coupler. */
    public static final double COUPLING_REACH = 6.0;
    /** How hard a coupling pulls a car back to the right distance: blocks per tick, per block out. */
    private static final double STIFFNESS = 0.35;
    private static final double MAX_CORRECTION = 0.3;

    private Trains() {
    }

    // ----------------------------------------------------------------------------------------------------------
    // Facing

    /**
     * The way the car's front points, flat. Minecarts keep their yaw pointing back the way they came (and flip it to
     * stay continuous when they reverse), so the front is the opposite of that.
     */
    public static Vec3 facing(Entity cart) {
        float yaw = cart.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
    }

    /** The yaw that makes a car face {@code front} (a flat direction). */
    public static float yawFacing(Vec3 front) {
        return (float) (Mth.atan2(-front.z, -front.x) * Mth.RAD_TO_DEG);
    }

    /** Speed along the car's facing, in blocks per tick: positive is forwards. */
    public static double forwardSpeed(Entity cart) {
        Vec3 f = facing(cart);
        Vec3 v = cart.getDeltaMovement();
        return v.x * f.x + v.z * f.z;
    }

    // ----------------------------------------------------------------------------------------------------------
    // Couplings

    @Nullable
    public static TrainCar leader(TrainCar car) {
        TrainCarState state = car.trainState();
        TrainCar found = resolve(car, state.leader, state.leaderCache);
        state.leaderCache = found == null ? null : found.cart();
        return found;
    }

    @Nullable
    public static TrainCar follower(TrainCar car) {
        TrainCarState state = car.trainState();
        TrainCar found = resolve(car, state.follower, state.followerCache);
        state.followerCache = found == null ? null : found.cart();
        return found;
    }

    @Nullable
    private static TrainCar resolve(TrainCar car, @Nullable UUID id, @Nullable Entity cached) {
        if (id == null) {
            return null;
        }
        if (cached instanceof TrainCar cachedCar && !cached.isRemoved() && id.equals(cached.getUUID())) {
            return cachedCar;
        }
        if (car.cart().level() instanceof ServerLevel level && level.getEntity(id) instanceof TrainCar other && !other.cart().isRemoved()) {
            return other;
        }
        return null;
    }

    /** Every loaded car of the train this car is in, front to back. */
    public static List<TrainCar> train(TrainCar car) {
        TrainCar head = car;
        for (int guard = 0; guard < 256; guard++) {
            TrainCar next = leader(head);
            if (next == null || next == car) {
                break;
            }
            head = next;
        }
        List<TrainCar> cars = new ArrayList<>();
        for (TrainCar c = head; c != null && cars.size() < 256 && !cars.contains(c); c = follower(c)) {
            cars.add(c);
        }
        return cars;
    }

    /** The distance two coupled cars keep between their middles. */
    public static double spacing(TrainCar a, TrainCar b) {
        return a.halfLength() + b.halfLength() + BUFFER_GAP;
    }

    private static void link(TrainCar front, TrainCar back) {
        front.trainState().follower = back.cart().getUUID();
        front.trainState().followerCache = back.cart();
        back.trainState().leader = front.cart().getUUID();
        back.trainState().leaderCache = front.cart();
    }

    private static void unlink(TrainCar front, TrainCar back) {
        if (back.cart().getUUID().equals(front.trainState().follower)) {
            front.trainState().follower = null;
            front.trainState().followerCache = null;
        }
        if (front.cart().getUUID().equals(back.trainState().leader)) {
            back.trainState().leader = null;
            back.trainState().leaderCache = null;
        }
    }

    /** Turns a train around end for end (as far as its couplings go: each car keeps facing the way it does). */
    private static void reverse(List<TrainCar> cars) {
        for (TrainCar car : cars) {
            TrainCarState s = car.trainState();
            UUID leader = s.leader;
            s.leader = s.follower;
            s.follower = leader;
            Entity cache = s.leaderCache;
            s.leaderCache = s.followerCache;
            s.followerCache = cache;
        }
    }

    public enum CoupleResult {
        COUPLED("coupled"),
        SAME_CAR("same_car"),
        SAME_TRAIN("same_train"),
        TOO_FAR("too_far"),
        NOT_AN_END("not_an_end"),
        TWO_LOCOMOTIVES("two_locomotives"),
        LOCOMOTIVE_FRONT("locomotive_front"),
        TOO_LONG("too_long");

        private final String key;

        CoupleResult(String key) {
            this.key = key;
        }

        public Component message() {
            return Component.translatable("message.reikotrains.couple." + key);
        }
    }

    /**
     * Couples two cars (picked in that order) together. The train with the locomotive stays in front and the other
     * train is hooked on behind it; without a locomotive, the first car's train leads. Either train is turned
     * around if that's what it takes, so long as each car picked is at one end of its train.
     */
    public static CoupleResult couple(TrainCar first, TrainCar second) {
        if (first == second) {
            return CoupleResult.SAME_CAR;
        }
        if (first.cart().level() != second.cart().level() || first.cart().distanceTo(second.cart()) > COUPLING_REACH) {
            return CoupleResult.TOO_FAR;
        }
        List<TrainCar> trainA = train(first);
        List<TrainCar> trainB = train(second);
        if (trainA.contains(second)) {
            return CoupleResult.SAME_TRAIN;
        }
        if (!isEnd(trainA, first) || !isEnd(trainB, second)) {
            return CoupleResult.NOT_AN_END;
        }
        boolean locoA = trainA.stream().anyMatch(TrainCar::isLocomotive);
        boolean locoB = trainB.stream().anyMatch(TrainCar::isLocomotive);
        if (locoA && locoB) {
            return CoupleResult.TWO_LOCOMOTIVES;
        }
        if (trainA.size() + trainB.size() > TrainsConfig.MAX_TRAIN_LENGTH.getAsInt()) {
            return CoupleResult.TOO_LONG;
        }
        boolean secondLeads = locoB && !locoA;
        List<TrainCar> lead = secondLeads ? trainB : trainA;
        List<TrainCar> trail = secondLeads ? trainA : trainB;
        TrainCar leadCar = secondLeads ? second : first;
        TrainCar trailCar = secondLeads ? first : second;

        // The lead train is coupled at its back, so the car picked has to be its last car.
        if (lead.get(lead.size() - 1) != leadCar) {
            if (lead.get(0).isLocomotive()) {
                return CoupleResult.LOCOMOTIVE_FRONT;
            }
            reverse(lead);
        }
        // The other train is coupled at its front: turn it round if the car picked is its last.
        if (trail.get(0) != trailCar) {
            reverse(trail);
        }
        link(leadCar, trailCar);
        return CoupleResult.COUPLED;
    }

    private static boolean isEnd(List<TrainCar> train, TrainCar car) {
        return train.get(0) == car || train.get(train.size() - 1) == car;
    }

    /** Uncouples a car from both its neighbours. Returns how many couplings came apart. */
    public static int uncouple(TrainCar car) {
        int count = 0;
        TrainCar leader = leader(car);
        if (leader != null) {
            unlink(leader, car);
            count++;
        } else if (car.trainState().leader != null) {
            car.trainState().leader = null;
            count++;
        }
        TrainCar follower = follower(car);
        if (follower != null) {
            unlink(car, follower);
            count++;
        } else if (car.trainState().follower != null) {
            car.trainState().follower = null;
            count++;
        }
        return count;
    }

    // ----------------------------------------------------------------------------------------------------------
    // Movement

    /**
     * Server, at the start of a car's tick: pulls (or pushes) the car so it keeps its distance behind the car it's
     * coupled to. It takes on the speed the car in front is going its way, plus a correction for being too close or
     * too far; the rails then steer it.
     */
    public static void follow(TrainCar car) {
        TrainCarState state = car.trainState();
        if (state.leader == null) {
            return;
        }
        TrainCar leader = leader(car);
        if (leader == null) {
            return; // not loaded right now
        }
        AbstractMinecart self = car.cart();
        AbstractMinecart front = leader.cart();
        if (!self.getUUID().equals(leader.trainState().follower)) {
            state.leader = null; // the other end of the coupling has gone
            state.leaderCache = null;
            return;
        }
        Vec3 offset = front.position().subtract(self.position());
        double distance = offset.length();
        double spacing = spacing(car, leader);
        if (distance > spacing + SNAP_SLACK) {
            unlink(leader, car);
            return;
        }
        double flat = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
        if (flat < 1.0E-4) {
            return;
        }
        double dx = offset.x / flat;
        double dz = offset.z / flat;
        Vec3 leaderMotion = front.getDeltaMovement();
        double along = leaderMotion.x * dx + leaderMotion.z * dz;
        double correction = Mth.clamp((distance - spacing) * STIFFNESS, -MAX_CORRECTION, MAX_CORRECTION);
        double speed = Mth.clamp(along + correction, -1.0, 1.0);
        self.setDeltaMovement(dx * speed, self.getDeltaMovement().y, dz * speed);
    }

    /**
     * Moves a car along its rails. Unlike a plain minecart, a train car doesn't go slower with someone in it, so a
     * full coach keeps up with the empty wagon behind it.
     */
    public static void moveOnRail(AbstractMinecart cart) {
        double max = cart.getMaxSpeedWithRail();
        Vec3 motion = cart.getDeltaMovement();
        cart.move(MoverType.SELF, new Vec3(Mth.clamp(motion.x, -max, max), 0.0, Mth.clamp(motion.z, -max, max)));
    }

    /** Train cars roll freely whether or not anyone's aboard (an empty minecart drags to a stop). */
    public static void slowDown(AbstractMinecart cart) {
        Vec3 motion = cart.getDeltaMovement().multiply(0.997, 0.0, 0.997);
        if (cart.isInWater()) {
            motion = motion.scale(0.95);
        }
        cart.setDeltaMovement(motion);
    }

    /** Client, after a car's tick: how far it rolled, for its wheels. */
    public static void rollWheels(TrainCar car) {
        AbstractMinecart cart = car.cart();
        Vec3 f = facing(cart);
        double dx = cart.getX() - cart.xo;
        double dz = cart.getZ() - cart.zo;
        double moved = Math.sqrt(dx * dx + dz * dz);
        if (moved > 4.0) {
            moved = 0.0; // teleported
        }
        car.trainState().roll(dx * f.x + dz * f.z >= 0 ? moved : -moved);
    }

    /** The model is longer and taller than the minecart hitbox, so it mustn't be culled by the hitbox alone. */
    public static AABB cullingBox(TrainCar car) {
        double reach = car.halfLength() + 0.2;
        return car.cart().getBoundingBox().inflate(reach, 0.0, reach).expandTowards(0.0, 1.5, 0.0);
    }

    // ----------------------------------------------------------------------------------------------------------
    // Seats

    /** Where a passenger sits, from the car's position. */
    @Nullable
    public static Vec3 seatPosition(TrainCar car, Entity passenger) {
        TrainCar.Seat[] seats = car.seats();
        int index = car.cart().getPassengers().indexOf(passenger);
        if (index < 0 || index >= seats.length) {
            return null;
        }
        TrainCar.Seat seat = seats[index];
        Vec3 f = facing(car.cart());
        return new Vec3(f.x * seat.forward() - f.z * seat.side(), seat.up(), f.z * seat.forward() + f.x * seat.side());
    }

    public static boolean hasFreeSeat(TrainCar car) {
        return car.cart().getPassengers().size() < car.seats().length;
    }

    public static InteractionResult board(TrainCar car, Player player) {
        if (player.isSecondaryUseActive() || !hasFreeSeat(car)) {
            return InteractionResult.PASS;
        }
        if (car.cart().level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return player.startRiding(car.cart()) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    /** A coupler in hand couples or uncouples the car instead of whatever clicking it would do. */
    public static InteractionResult useCoupler(TrainCar car, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof CouplerItem) {
            return CouplerItem.useOnCar(stack, player, car);
        }
        return InteractionResult.PASS;
    }

    // ----------------------------------------------------------------------------------------------------------
    // Saving and removal

    public static void save(TrainCar car, CompoundTag tag) {
        car.trainState().save(tag);
    }

    public static void load(TrainCar car, CompoundTag tag) {
        car.trainState().load(tag);
    }

    /** A car that's destroyed (not just unloaded) comes off its train. */
    public static void onRemoved(TrainCar car, Entity.RemovalReason reason) {
        if (reason.shouldDestroy() && !car.cart().level().isClientSide) {
            uncouple(car);
        }
    }
}
