package com.reiko.trains.train;

import javax.annotation.Nullable;
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
 * room on the ground), or on to the next car transporter. Only the car mod's own code ({@link CarModAccess}) touches
 * its classes, so without it installed the wagon just carries nothing.
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

    @Nullable
    private static Component driveOn(Entity car, int direction) {
        Approach approach = approach(car, direction, REACH);
        if (approach == null) {
            return null;
        }
        CarTransporter at = approach.wagon();
        int from = approach.end();
        if (!standing(at)) {
            return message("moving");
        }
        if (towing(car)) {
            return message("towing");
        }
        double length = length(car);
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
        if (!at.carry(car, along, facing(car, at), length)) {
            return message("no_room");
        }
        clank(at, 1.3F);
        return Component.translatable("message.reikotrains.car_transporter.loaded", at.cars().size());
    }

    @Nullable
    private static Component driveOff(Player player, Entity car, CarTransporter wagon, int direction) {
        if (!standing(wagon)) {
            return message("moving");
        }
        Vec3 heading = forward(car).scale(direction);
        int towards = heading.dot(wagon.flatForward()) >= 0 ? 1 : -1;
        if (!wagon.clearTowards(car, towards)) {
            return message("in_the_way");
        }
        double length = length(car);
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
        target.carry(car, targetAlong, facing(car, target), length);
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

    /** Whether it's towing a trailer or another car (they ride it). */
    private static boolean towing(Entity car) {
        return car.getPassengers().stream().anyMatch(passenger -> !(passenger instanceof Player));
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
