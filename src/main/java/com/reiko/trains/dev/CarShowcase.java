package com.reiko.trains.dev;

import com.reiko.cars.car.CarEntity;
import com.reiko.trains.gametest.TestTracks;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.signal.StationBlockEntity;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.train.CarCargo;
import com.reiko.trains.train.CarTransporter;
import com.reiko.trains.train.Locomotive;
import com.reiko.trains.train.Trains;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * DEV ONLY: the car transporter scene in the showcase, with Reiko's Car Mod installed (-PcarJars=...). Kept apart from
 * {@link DevShowcase} so nothing touches the car mod's classes without it.
 */
final class CarShowcase {
    /** The line, along x at this z: a steam engine at x = 56 with two car transporters behind, and Docks at x = 100. */
    static final int LINE = -136;
    private static final Vec3 EAST = new Vec3(1, 0, 0);

    private CarShowcase() {
    }

    static void build(ServerLevel level, int y) {
        TestTracks.line(level, new BlockPos(0, y, LINE), TrackAxis.X, 110);
        TestTracks.station(level, new BlockPos(100, y, LINE - 3), new BlockPos(100, y, LINE), "Docks", 10);
        Locomotive loco = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), new BlockPos(56, y, LINE), EAST);
        CarTransporter front = TestTracks.car(level, ModEntities.CAR_TRANSPORTER.get(), new BlockPos(46, y, LINE), EAST);
        CarTransporter back = TestTracks.car(level, ModEntities.CAR_TRANSPORTER.get(), new BlockPos(33, y, LINE), EAST);
        Trains.couple(loco, front);
        Trains.couple(front, back);
        loco.setFuel(32000);
        // The front transporter's full already: three cars driven on from the west end, nose first.
        for (String type : List.of("sports_car", "hatchback", "city_ev")) {
            CarEntity car = car(level, type, front.position(), -90.0F);
            double length = CarCargo.length(car);
            front.carry(car, front.placeFrom(-1, length), 1, length);
        }
        // A sedan by the back one's west end, to drive on.
        CarEntity sedan = car(level, "sedan", new Vec3(back.getX() - back.halfLength() - 1.75 - 1.5, y + 0.1, LINE + 0.5), -90.0F);
        sedan.setFuel(sedan.tankCapacity());
    }

    static CarEntity car(ServerLevel level, String type, Vec3 at, float yaw) {
        CarEntity car = (CarEntity) BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("reikocars", type)).create(level);
        car.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        level.addFreshEntity(car);
        return car;
    }

    /** The player gets in the sedan, to drive it on. */
    static void boardSedan(ServerPlayer player, int y) {
        ServerLevel level = player.serverLevel();
        Entity sedan = level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(15, y - 2, LINE - 3, 30, y + 4, LINE + 3),
                entity -> entity instanceof CarEntity).getFirst();
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.teleportTo(level, sedan.getX(), sedan.getY(), sedan.getZ(), -90.0F, 10.0F);
        player.startRiding(sedan, true);
    }

    /** The train sets off for Docks. */
    static void depart(ServerPlayer player, int y) {
        ServerLevel level = player.serverLevel();
        Locomotive loco = level.getEntitiesOfClass(Locomotive.class, new net.minecraft.world.phys.AABB(50, y - 2, LINE - 3, 62, y + 4, LINE + 3)).getFirst();
        if (level.getBlockEntity(new BlockPos(100, y, LINE - 3)) instanceof StationBlockEntity docks) {
            loco.autopilot().start(List.of(TestTracks.stop(docks)));
        }
    }
}
