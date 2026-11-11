package com.reiko.trains.gametest;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.train.CarTransporter;
import com.reiko.trains.train.PassengerCoach;
import com.reiko.trains.train.SteamLocomotive;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Game tests for trains being solid: bumped into, stood on, and got out of. */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class CollisionGameTests {
    private static final String LINE = "line";
    private static final Vec3 EAST = new Vec3(1, 0, 0);

    private CollisionGameTests() {
    }

    private static void straight(GameTestHelper helper, int blocks) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 64, 9);
        TestTracks.line(level, helper.absolutePos(new BlockPos(0, 1, 4)), TrackAxis.X, blocks);
    }

    private static BlockPos onLine(GameTestHelper helper, int x) {
        return helper.absolutePos(new BlockPos(x, 1, 4));
    }

    private static Pig pig(ServerLevel level, double x, double y, double z) {
        Pig pig = EntityType.PIG.create(level);
        pig.setNoAi(true);
        pig.moveTo(x, y, z, 0, 0);
        level.addFreshEntity(pig);
        return pig;
    }

    @GameTest(template = LINE, timeoutTicks = 40)
    public static void animalsAndPeopleBumpIntoTrains(GameTestHelper helper) {
        straight(helper, 40);
        ServerLevel level = helper.getLevel();
        PassengerCoach coach = TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), onLine(helper, 12), EAST);
        SteamLocomotive loco = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), onLine(helper, 28), EAST);
        double side = coach.getZ() - 1.0;
        // Walking at its middle, and at its ends: stopped at its side all along.
        for (double along : new double[]{0, coach.halfLength() - 0.6, -(coach.halfLength() - 0.6)}) {
            Pig pig = pig(level, coach.getX() + along, coach.getY(), coach.getZ() - 3.0);
            pig.move(MoverType.SELF, new Vec3(0, 0, 2.5));
            double reached = pig.getZ() + pig.getBbWidth() / 2;
            helper.assertTrue(reached <= side + 0.01 && reached >= side - 0.05 && pig.horizontalCollision,
                    "a pig walking into a coach " + along + " from its middle should stop at its side, not get to " + (reached - coach.getZ()));
            pig.discard();
        }
        // Into a locomotive's end.
        Pig pig = pig(level, loco.getX() + loco.halfLength() + 2.0, loco.getY(), loco.getZ());
        pig.move(MoverType.SELF, new Vec3(-3.0, 0, 0));
        helper.assertTrue(pig.getX() - pig.getBbWidth() / 2 >= loco.getX() + loco.halfLength() - 0.01,
                "a pig walking into a locomotive's end should stop there, not get to " + (pig.getX() - loco.getX()));
        // Someone getting out isn't put inside it.
        Player rider = helper.makeMockPlayer(GameType.SURVIVAL);
        rider.startRiding(coach, true);
        Vec3 out = coach.getDismountLocationForPassenger(rider);
        AABB standing = rider.getDimensions(rider.getPose()).makeBoundingBox(out);
        helper.assertTrue(level.noCollision(rider, standing), "someone getting out of a coach should land clear of it, not in it at " + out);
        rider.stopRiding();
        helper.succeed();
    }

    @GameTest(template = LINE, timeoutTicks = 100)
    public static void thingsLandOnTopOfTrains(GameTestHelper helper) {
        straight(helper, 40);
        ServerLevel level = helper.getLevel();
        PassengerCoach coach = TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), onLine(helper, 10), EAST);
        CarTransporter wagon = TestTracks.car(level, ModEntities.CAR_TRANSPORTER.get(), onLine(helper, 27), EAST);
        ItemEntity onRoof = drop(level, coach.position().add(0, 5, 0));
        ItemEntity onDeck = drop(level, wagon.position().add(0, 4, 0));
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(Math.abs(onRoof.getY() - (coach.getY() + 3.0)) < 0.1 && onRoof.onGround(),
                    "something dropped on a coach should land on its roof, not at " + (onRoof.getY() - coach.getY()));
            helper.assertTrue(Math.abs(onDeck.getY() - (wagon.getY() + CarTransporter.DECK)) < 0.1 && onDeck.onGround(),
                    "something dropped on a car transporter should land on its deck, not at " + (onDeck.getY() - wagon.getY()));
            helper.succeed();
        });
    }

    private static ItemEntity drop(ServerLevel level, Vec3 at) {
        ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.COAL));
        item.setDeltaMovement(Vec3.ZERO);
        item.setNeverPickUp();
        level.addFreshEntity(item);
        return item;
    }
}
