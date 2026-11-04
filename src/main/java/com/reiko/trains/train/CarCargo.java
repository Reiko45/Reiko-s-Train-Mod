package com.reiko.trains.train;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Cars from Reiko's Car Mod on car transporters: driving on, along the train and off. A car comes aboard when its
 * driver keeps going (forwards or in reverse) up against a free end of a standing car transporter, lined up with it;
 * it rolls along to the far end, or up to the cars already there, or on along the train to the next car transporter
 * if this one's empty. Driving off works the same way the other way round: over the end of the train (if there's
 * room on the ground), or on to the next car transporter. A car towing a trailer comes aboard with it (driving
 * forwards), and its trailer can be unhitched and hitched again aboard with the car mod's hitch key. Beside a Car Ramp
 * (a loading dock along the track), cars drive aboard from the ramp sideways, to wherever there's room, and off onto
 * it. Only the car mod's own code ({@link CarModAccess}) touches its classes, so without it installed the wagon just
 * carries nothing.
 */
public final class CarCargo {
    /** Whether Reiko's Car Mod is installed. */
    public static final boolean CAR_MOD = ModList.get() != null && ModList.get().isLoaded("reikocars");
    /** How close a car's nose (or tail) must be to a car transporter's end to go aboard, in blocks. */
    public static final double REACH = 2.5;
    /** The same, for the driver's game to decide it's time (a little closer, as the server checks again). */
    public static final double CLIENT_REACH = 1.8;
    /** How nearly the car must be lined up with the wagon: the cosine of the angle between them. */
    private static final double LINED_UP = 0.82;

    private CarCargo() {
    }

    /** A car transporter, and which end of it (+1 its front, -1 its back). */
    public record Approach(CarTransporter wagon, int end) {
    }

    public static boolean isCar(Entity entity) {
        return CAR_MOD && CarModAccess.isCar(entity);
    }

    /** The car {@code player} is driving, if they're driving one. */
    @Nullable
    public static Entity drivenCar(Player player) {
        Entity vehicle = player.getVehicle();
        return vehicle != null && isCar(vehicle) && vehicle.getControllingPassenger() == player ? vehicle : null;
    }

    public static double length(Entity car) {
        return CarModAccess.length(car);
    }

    public static Vec3 forward(Entity car) {
        return CarModAccess.forward(car);
    }

    /**
     * The car transporter whose free end the car's nose ({@code direction} +1) or tail (-1) is up against, lined up
     * with it and heading in; null if there's none.
     */
    @Nullable
    public static Approach approach(Entity car, int direction, double reach) {
        Vec3 heading = forward(car).scale(direction);
        Vec3 tip = car.position().add(heading.scale(length(car) / 2));
        Approach best = null;
        double bestDistance = reach;
        for (CarTransporter wagon : car.level().getEntitiesOfClass(CarTransporter.class, car.getBoundingBox().inflate(16))) {
            Vec3 along = wagon.flatForward();
            if (Math.abs(heading.dot(along)) < LINED_UP || Math.abs(tip.y - wagon.getY()) > 1.6) {
                continue;
            }
            for (int end : new int[] {1, -1}) {
                if (!car.level().isClientSide && Trains.coupledAt(wagon, end) != null) {
                    continue; // another car's coupled there: no way on
                }
                Vec3 toTip = tip.subtract(wagon.position());
                double out = (toTip.x * along.x + toTip.z * along.z) * end;
                double into = heading.dot(along) * -end;
                Vec3 endPoint = wagon.position().add(along.scale(end * wagon.halfLength()));
                double distance = Math.hypot(tip.x - endPoint.x, tip.z - endPoint.z);
                if (out >= wagon.halfLength() - 1.0 && into >= LINED_UP && distance < bestDistance) {
                    best = new Approach(wagon, end);
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    /** Whether a car transporter's train is standing still. */
    public static boolean standing(CarTransporter wagon) {
        if (wagon.level().isClientSide) {
            return Math.hypot(wagon.getX() - wagon.xo, wagon.getZ() - wagon.zo) < 0.01;
        }
        return Math.abs(wagon.trainSpeed()) < 0.01;
    }

    /**
     * {@code player} is driving forwards ({@code direction} +1) or in reverse (-1) at a car transporter: onto it, or
     * along the train, or off. Returns what to tell them, or null for nothing.
     */
    @Nullable
    public static Component drive(Player player, int direction) {
        Entity car = drivenCar(player);
        if (car == null) {
            return null;
        }
        Component result = car.getVehicle() instanceof CarTransporter wagon ? driveOff(player, car, wagon, direction)
                : car.isPassenger() ? null : driveOn(car, direction);
        return result;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Trailers

    /** The trailer the car's towing, if it's towing one. */
    @Nullable
    public static Entity trailer(Entity car) {
        return CAR_MOD && isCar(car) ? CarModAccess.trailer(car) : null;
    }

    public static boolean isTrailer(Entity entity) {
        return CAR_MOD && CarModAccess.isTrailer(entity);
    }

    /** How far behind the car its trailer reaches (from the car's back bumper to the trailer's), towed straight. */
    public static double tail(Entity car, Entity trailer) {
        Vec3 forward = forward(car);
        Vec3 hitch = CarModAccess.hitchPoint(car).subtract(car.position());
        Vec3 coupler = CarModAccess.coupler(trailer).subtract(trailer.position());
        Vec3 trailerForward = CarModAccess.trailerForward(trailer);
        double hitchBack = -(hitch.x * forward.x + hitch.z * forward.z);
        double couplerForward = coupler.x * trailerForward.x + coupler.z * trailerForward.z;
        return Math.max(0, hitchBack + couplerForward + CarModAccess.trailerLength(trailer) / 2 - length(car) / 2);
    }

    /** How much deck the car takes up, with its trailer if it's towing one. */
    private static double footprint(Entity car) {
        Entity trailer = trailer(car);
        return length(car) + (trailer == null ? 0 : tail(car, trailer));
    }

    /**
     * Where along the deck the car goes, for its footprint (and its trailer's) centred at {@code centre}: the car at
     * the front of it, the way it faces.
     */
    private static double carAlong(Entity car, double centre, int facing) {
        return centre + facing * (footprint(car) / 2 - length(car) / 2);
    }

    /** Puts a car aboard with its footprint centred at {@code centre}, trailer and all. */
    private static boolean load(Entity car, CarTransporter at, double centre) {
        int facing = facing(car, at);
        Entity trailer = trailer(car);
        double tail = trailer == null ? 0 : tail(car, trailer);
        return at.carry(car, carAlong(car, centre, facing), facing, length(car), tail);
    }

    /** A trailer's been unhitched aboard: it's left standing on the deck where it is, and stays aboard. */
    static void leaveStanding(CarTransporter wagon, Entity trailer) {
        Vec3 along = wagon.flatForward();
        Vec3 d = trailer.position().subtract(wagon.position());
        double at = Math.max(-CarTransporter.DECK_HALF, Math.min(CarTransporter.DECK_HALF, d.x * along.x + d.z * along.z));
        Vec3 facing = CarModAccess.trailerForward(trailer);
        wagon.carry(trailer, at, facing.dot(along) >= 0 ? 1 : -1, CarModAccess.trailerLength(trailer));
    }

    /**
     * The car mod's hitch key, pressed by someone driving a car aboard a car transporter, with no trailer on: the
     * trailer standing on the deck right behind it is hitched up (a moment later, once the car mod's had the key).
     */
    public static void hitch(Player player) {
        Entity car = drivenCar(player);
        if (car == null || !(car.getVehicle() instanceof CarTransporter wagon) || trailer(car) != null) {
            return;
        }
        Vec3 hitch = CarModAccess.hitchPoint(car);
        Entity best = null;
        double nearest = HITCH_REACH;
        for (Entity passenger : wagon.getPassengers()) {
            if (isTrailer(passenger) && CarModAccess.canTow(car, passenger, player)) {
                Vec3 coupler = CarModAccess.coupler(passenger);
                double distance = Math.hypot(coupler.x - hitch.x, coupler.z - hitch.z);
                if (distance <= nearest) {
                    nearest = distance;
                    best = passenger;
                }
            }
        }
        if (best != null) {
            wagon.hitchSoon(car, best);
        }
    }

    /** Hitches a trailer standing on the deck to the car in front of it. */
    static void hitchUp(CarTransporter wagon, Entity car, Entity trailer) {
        if (car.getVehicle() != wagon || trailer.getVehicle() != wagon || trailer(car) != null) {
            return;
        }
        trailer.stopRiding();
        if (!trailer.startRiding(car, true)) {
            leaveStanding(wagon, trailer);
            return;
        }
        wagon.setTail(car, tail(car, trailer));
        clank(wagon, 1.1F);
        if (car.getControllingPassenger() instanceof Player driver) {
            driver.displayClientMessage(message("hitched"), true);
        }
    }

    /** How close the trailer's coupler must be to the car's hitch, as the car mod has it. */
    private static final double HITCH_REACH = 1.5;

    @Nullable
    private static Component driveOn(Entity car, int direction) {
        Approach approach = approach(car, direction, REACH);
        if (approach == null) {
            return driveOnFromRamp(car);
        }
        CarTransporter at = approach.wagon();
        int from = approach.end();
        if (!standing(at)) {
            return message("moving");
        }
        if (towing(car) || trailer(car) != null && direction < 0) {
            return message("towing");
        }
        double length = footprint(car);
        Double along = at.placeFrom(from, length);
        // On along the train while the wagons are empty, to the furthest one with room.
        while (at.isEmpty() && Trains.coupledAt(at, -from) instanceof CarTransporter next) {
            int nextFrom = endTowards(next, at);
            Double nextAlong = next.placeFrom(nextFrom, length);
            if (nextAlong == null) {
                break;
            }
            at = next;
            from = nextFrom;
            along = nextAlong;
        }
        if (along == null) {
            return message("no_room");
        }
        CarModAccess.stopDead(car);
        if (!load(car, at, along)) {
            return message("no_room");
        }
        clank(at, 1.3F);
        return Component.translatable("message.reikotrains.car_transporter.loaded", carsOn(at));
    }

    @Nullable
    private static Component driveOff(Player player, Entity car, CarTransporter wagon, int direction) {
        if (!standing(wagon)) {
            return message("moving");
        }
        if (trailer(car) != null && direction < 0) {
            return message("towing"); // no backing a trailer off
        }
        BlockPos ramp = rampBeside(car, wagon);
        if (ramp != null) {
            return driveOntoRamp(player, car, wagon, ramp);
        }
        Vec3 heading = forward(car).scale(direction);
        int towards = heading.dot(wagon.flatForward()) >= 0 ? 1 : -1;
        if (!wagon.clearTowards(car, towards)) {
            return message("in_the_way");
        }
        double length = footprint(car);
        // Along the train that way: over empty car transporters, up to the cars on the next one (or as far as the
        // next kind of car), or off the end.
        CarTransporter at = wagon;
        CarTransporter target = null;
        double targetAlong = 0;
        boolean off = false;
        while (true) {
            TrainCarEntity next = Trains.coupledAt(at, towards);
            if (next == null) {
                off = true;
                break;
            }
            if (!(next instanceof CarTransporter transporter)) {
                break;
            }
            int from = endTowards(transporter, at);
            if (transporter.isEmpty()) {
                at = transporter;
                towards = -from;
                continue;
            }
            Double along = transporter.placeFrom(from, length);
            if (along != null) {
                target = transporter;
                targetAlong = along;
            }
            break;
        }
        if (off) {
            Vec3 out = at.flatForward().scale(towards);
            Vec3 spot = at.position().add(out.scale(at.halfLength() + length / 2 + 0.75));
            AABB room = car.getType().getDimensions().makeBoundingBox(spot);
            if (!car.level().noBlockCollision(car, room)) {
                return message("no_room_off");
            }
            car.stopRiding();
            CarModAccess.stopDead(car);
            car.moveTo(spot.x, spot.y, spot.z, car.getYRot(), 0.0F);
            if (player instanceof ServerPlayer serverPlayer) {
                // The driver's game moves the car it drives, so tell it where the car is now.
                serverPlayer.connection.send(new ClientboundMoveVehiclePacket(car));
            }
            clank(at, 1.0F);
            return message("unloaded");
        }
        if (target == null) {
            if (at == wagon) {
                return message("blocked");
            }
            // Across empty car transporters as far as the train goes: to the far end of the last.
            target = at;
            targetAlong = towards * (CarTransporter.DECK_HALF - length / 2);
        }
        car.stopRiding();
        CarModAccess.stopDead(car);
        load(car, target, targetAlong);
        clank(target, 1.15F);
        return message("moved");
    }

    /** Which end of {@code wagon} is coupled to {@code neighbour}. */
    private static int endTowards(CarTransporter wagon, TrainCarEntity neighbour) {
        return Trains.coupledAt(wagon, 1) == neighbour ? 1 : -1;
    }

    /** Which way along {@code wagon} the car faces: +1 its front, -1 its back. */
    private static int facing(Entity car, CarTransporter wagon) {
        return forward(car).dot(wagon.flatForward()) >= 0 ? 1 : -1;
    }

    /** Whether it's towing something that can't come aboard with it: another car on a tow truck's lift. */
    private static boolean towing(Entity car) {
        return car.getPassengers().stream().anyMatch(passenger -> !(passenger instanceof Player) && !isTrailer(passenger));
    }

    /** How many cars are aboard (not counting trailers left standing). */
    private static int carsOn(CarTransporter wagon) {
        return (int) wagon.cars().stream().filter(CarCargo::isCar).count();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Car Ramps: a loading dock beside the track

    /** How far from the middle of the track a Car Ramp beside it can be, in blocks. */
    public static final double RAMP_REACH = 3.6;

    /** Whether the car's standing on a Car Ramp. */
    public static boolean onRamp(Entity car) {
        Level level = car.level();
        BlockPos at = car.blockPosition();
        return level.getBlockState(at).getBlock() instanceof com.reiko.trains.freight.CarRampBlock
                || level.getBlockState(at.below()).getBlock() instanceof com.reiko.trains.freight.CarRampBlock;
    }

    /**
     * The standing car transporter beside the Car Ramp the car's on, lined up with it (the car facing along the train
     * either way), with room for it: the nearest. Null if there's none.
     */
    @Nullable
    public static CarTransporter besideRamp(Entity car) {
        if (!onRamp(car)) {
            return null;
        }
        Vec3 heading = forward(car);
        CarTransporter best = null;
        double nearest = RAMP_REACH;
        for (CarTransporter wagon : car.level().getEntitiesOfClass(CarTransporter.class, car.getBoundingBox().inflate(10))) {
            Vec3 along = wagon.flatForward();
            if (Math.abs(heading.dot(along)) < LINED_UP || Math.abs(car.getY() - (wagon.getY() + CarTransporter.DECK)) > 1.6) {
                continue;
            }
            Vec3 d = car.position().subtract(wagon.position());
            double lengthways = d.x * along.x + d.z * along.z;
            double sideways = Math.abs(d.x * along.z - d.z * along.x);
            if (sideways > 1.2 && sideways < nearest && Math.abs(lengthways) <= CarTransporter.DECK_HALF + 1.0) {
                best = wagon;
                nearest = sideways;
            }
        }
        return best;
    }

    /** A car on a Car Ramp beside a car transporter: it goes aboard sideways, to the nearest place there's room. */
    @Nullable
    private static Component driveOnFromRamp(Entity car) {
        CarTransporter at = besideRamp(car);
        if (at == null) {
            return null;
        }
        if (!standing(at)) {
            return message("moving");
        }
        if (towing(car)) {
            return message("towing");
        }
        Vec3 along = at.flatForward();
        Vec3 d = car.position().subtract(at.position());
        Double centre = at.placeNear(d.x * along.x + d.z * along.z, footprint(car));
        if (centre == null) {
            return message("no_room");
        }
        CarModAccess.stopDead(car);
        if (!load(car, at, centre)) {
            return message("no_room");
        }
        clank(at, 1.3F);
        return Component.translatable("message.reikotrains.car_transporter.loaded", carsOn(at));
    }

    /** A Car Ramp beside a car aboard (within reach of the track, level with the deck), if there's one: the nearest. */
    @Nullable
    public static BlockPos rampBeside(Entity car, CarTransporter wagon) {
        Level level = car.level();
        Vec3 along = wagon.flatForward();
        Vec3 side = new Vec3(-along.z, 0, along.x);
        BlockPos best = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (int s = -1; s <= 1; s += 2) {
            for (double out = 1.5; out <= RAMP_REACH; out += 0.5) {
                for (int dy = -2; dy <= 0; dy++) {
                    BlockPos pos = BlockPos.containing(car.getX() + side.x * s * out, car.getY() + dy, car.getZ() + side.z * s * out);
                    if (level.getBlockState(pos).getBlock() instanceof com.reiko.trains.freight.CarRampBlock && out < nearest) {
                        best = pos.immutable();
                        nearest = out;
                    }
                }
            }
        }
        return best;
    }

    /** Drives a car aboard off sideways, onto the Car Ramp beside it (its trailer, if it has one, after it). */
    private static Component driveOntoRamp(Player player, Entity car, CarTransporter wagon, BlockPos ramp) {
        Vec3 spot = new Vec3(ramp.getX() + 0.5, ramp.getY() + 1.0, ramp.getZ() + 0.5);
        // Lined up with the train, beside the car's place on it.
        Vec3 along = wagon.flatForward();
        Vec3 d = car.position().subtract(wagon.position());
        double lengthways = d.x * along.x + d.z * along.z;
        Vec3 sideways = spot.subtract(wagon.position());
        double out = sideways.x * -along.z + sideways.z * along.x;
        spot = wagon.position().add(along.scale(lengthways)).add(new Vec3(-along.z, 0, along.x).scale(out));
        spot = new Vec3(spot.x, ramp.getY() + 1.0, spot.z);
        AABB room = car.getType().getDimensions().makeBoundingBox(spot);
        if (!car.level().noBlockCollision(car, room)) {
            return message("no_room_off");
        }
        car.stopRiding();
        CarModAccess.stopDead(car);
        car.moveTo(spot.x, spot.y, spot.z, car.getYRot(), 0.0F);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundMoveVehiclePacket(car));
        }
        clank(wagon, 1.0F);
        return message("onto_ramp");
    }

    private static Component message(String key) {
        return Component.translatable("message.reikotrains.car_transporter." + key);
    }

    private static void clank(Entity at, float pitch) {
        Level level = at.level();
        level.playSound(null, at.getX(), at.getY(), at.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 1.0F, pitch);
        level.playSound(null, at.getX(), at.getY(), at.getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.NEUTRAL, 0.5F, pitch * 0.8F);
    }
}
