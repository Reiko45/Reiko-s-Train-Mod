package com.reiko.trains.gametest;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.registry.ModBlocks;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.signal.CrossingArmBlock;
import com.reiko.trains.signal.LevelCrossingBlock;
import com.reiko.trains.signal.LevelCrossingBlockEntity;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.train.PassengerCoach;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Game tests for level crossings. The track runs along x at z = 4; the crossing's post stands three blocks off it at
 * x = 30, its arm reaching west (along the track) over x = 29 down to 26, as if a road ran north-south through there.
 */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class CrossingGameTests {
    private static final String LINE = "line";
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final int POST_X = 30;
    /** Long enough for the lamps' lead and the arm to come all the way down, with a little to spare. */
    private static final int CLOSED = LevelCrossingBlockEntity.LEAD_TICKS + LevelCrossingBlockEntity.ARM_TICKS + 12;

    private CrossingGameTests() {
    }

    private static void line(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 64, 9);
        TestTracks.line(level, at(helper, 0), TrackAxis.X, 64);
    }

    private static BlockPos at(GameTestHelper helper, int x) {
        return helper.absolutePos(new BlockPos(x, 1, 4));
    }

    private static LevelCrossingBlockEntity crossing(GameTestHelper helper, int length) {
        return TestTracks.levelCrossing(helper.getLevel(), helper.absolutePos(new BlockPos(POST_X, 1, 1)), Direction.WEST, length);
    }

    /** The block of road {@code i} out from the post, under its arm. */
    private static BlockPos road(LevelCrossingBlockEntity crossing, int i) {
        return crossing.getBlockPos().west(i);
    }

    private static boolean barrierAt(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getBlockState(pos).is(ModBlocks.CROSSING_ARM.get());
    }

    private static PassengerCoach coach(GameTestHelper helper, int x) {
        return TestTracks.car(helper.getLevel(), ModEntities.PASSENGER_COACH.get(), at(helper, x), EAST);
    }

    @GameTest(template = LINE, timeoutTicks = 300)
    public static void crossingClosesWhileATrainStandsOnIt(GameTestHelper helper) {
        line(helper);
        LevelCrossingBlockEntity crossing = crossing(helper, 4);
        ServerLevel level = helper.getLevel();
        helper.runAfterDelay(5, () -> helper.assertTrue(!crossing.isWarning(), "with no train about, it should be quiet"));
        PassengerCoach[] standing = new PassengerCoach[1];
        helper.runAfterDelay(6, () -> standing[0] = coach(helper, POST_X + 2));
        helper.runAfterDelay(16, () -> {
            helper.assertTrue(crossing.isWarning(), "a coach on the crossing should set the lamps flashing");
            helper.assertTrue(!crossing.isDown() && !barrierAt(helper, road(crossing, 2)), "the arm should wait for the lamps' warning before it comes down");
            helper.assertTrue(level.hasNeighborSignal(crossing.getBlockPos().east()), "it should give off a redstone signal while it warns");
        });
        helper.runAfterDelay(6 + CLOSED, () -> {
            helper.assertTrue(crossing.isDown(), "the arm should be down");
            for (int i = 1; i <= 4; i++) {
                BlockPos pos = road(crossing, i);
                helper.assertTrue(barrierAt(helper, pos), "the road should be barred " + i + " blocks out");
                // A car climbs a whole block: lifted that much, it must still hit the barrier.
                AABB car = new AABB(pos).deflate(0.1).move(0, 1.0, 0);
                helper.assertTrue(!level.noCollision(car), "the barrier should be too tall to drive over, " + i + " blocks out");
            }
            helper.assertTrue(!barrierAt(helper, road(crossing, 5)), "the barrier should stop where the arm does");
            standing[0].discard();
        });
        helper.runAfterDelay(6 + CLOSED + 10, () -> {
            helper.assertTrue(!crossing.isDown(), "with the train gone the arm should go up");
            helper.assertTrue(!barrierAt(helper, road(crossing, 2)), "and the road should be open again");
            helper.assertTrue(crossing.isWarning(), "the lamps should keep flashing while the arm goes up");
        });
        helper.runAfterDelay(6 + CLOSED + 10 + LevelCrossingBlockEntity.ARM_TICKS + 4, () -> {
            helper.assertTrue(!crossing.isWarning(), "with the arm up, the lamps should go out");
            helper.assertTrue(!level.hasNeighborSignal(crossing.getBlockPos().east()), "and the redstone signal should stop");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 400)
    public static void crossingWarnsAheadOfAnApproachingTrain(GameTestHelper helper) {
        line(helper);
        LevelCrossingBlockEntity crossing = crossing(helper, 4);
        PassengerCoach coach = coach(helper, 6);
        double nodeX = crossing.node().getX() + 0.5;
        // How far off the coach's front was when the lamps came on, and whether the arm came down.
        double[] warnedAt = {-1};
        boolean[] closed = {false};
        helper.onEachTick(() -> {
            if (warnedAt[0] < 0 && crossing.isWarning()) {
                warnedAt[0] = nodeX - (coach.getX() + coach.halfLength());
            }
            closed[0] |= crossing.isDown();
        });
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(!crossing.isWarning(), "a train standing well short of the crossing shouldn't set it off");
            coach.shove(0.3);
        });
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(warnedAt[0] > LevelCrossingBlockEntity.ON_CROSSING,
                    "the lamps should have come on before the train got near: they came on " + warnedAt[0] + " blocks off");
            helper.assertTrue(closed[0], "the arm should have come down for it");
            helper.assertTrue(coach.getX() - coach.halfLength() > nodeX + LevelCrossingBlockEntity.ON_CROSSING,
                    "the coach should be well past by now, at " + (coach.getX() - nodeX));
            helper.assertTrue(!crossing.isDown() && !crossing.isWarning(), "with the train gone by, the crossing should be open and quiet");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 100)
    public static void crossingIgnoresATrainGoingAway(GameTestHelper helper) {
        line(helper);
        LevelCrossingBlockEntity crossing = crossing(helper, 4);
        PassengerCoach coach = coach(helper, POST_X + 14);
        coach.shove(0.3);
        boolean[] warned = {false};
        helper.onEachTick(() -> warned[0] |= crossing.isWarning());
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(!warned[0], "a train heading away from the crossing shouldn't set it off");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 300)
    public static void barrierLeavesRoomForSomethingUnderTheArm(GameTestHelper helper) {
        line(helper);
        LevelCrossingBlockEntity crossing = crossing(helper, 4);
        ServerLevel level = helper.getLevel();
        ArmorStand stand = EntityType.ARMOR_STAND.create(level);
        BlockPos caught = road(crossing, 3);
        stand.moveTo(caught.getX() + 0.5, caught.getY(), caught.getZ() + 0.5);
        level.addFreshEntity(stand);
        coach(helper, POST_X);
        helper.runAfterDelay(CLOSED, () -> {
            helper.assertTrue(crossing.isDown(), "the arm should be down");
            helper.assertTrue(!barrierAt(helper, caught), "there should be no barrier where something's standing under the arm");
            helper.assertTrue(barrierAt(helper, road(crossing, 2)) && barrierAt(helper, road(crossing, 4)), "but there should be either side of it");
            stand.discard();
        });
        helper.runAfterDelay(CLOSED + 10, () -> {
            helper.assertTrue(barrierAt(helper, caught), "once it's moved, the gap should be filled");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 300)
    public static void clickingThePostLengthensTheArm(GameTestHelper helper) {
        line(helper);
        LevelCrossingBlockEntity crossing = crossing(helper, 4);
        coach(helper, POST_X);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos post = crossing.getBlockPos();
        helper.runAfterDelay(CLOSED, () -> {
            helper.assertTrue(!barrierAt(helper, road(crossing, 5)), "a four-block arm shouldn't reach five out");
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(post), Direction.EAST, post, false);
            helper.getLevel().getBlockState(post).useWithoutItem(helper.getLevel(), player, hit);
            helper.assertTrue(helper.getLevel().getBlockState(post).getValue(LevelCrossingBlock.LENGTH) == 5, "a click should make the arm five long");
            helper.assertTrue(barrierAt(helper, road(crossing, 5)), "and the barrier should reach out with it");
            for (int i = 0; i < 4; i++) {
                helper.getLevel().getBlockState(post).useWithoutItem(helper.getLevel(), player, hit);
            }
            helper.assertTrue(helper.getLevel().getBlockState(post).getValue(LevelCrossingBlock.LENGTH) == 1, "past eight, it should go round to one");
            helper.assertTrue(barrierAt(helper, road(crossing, 1)) && !barrierAt(helper, road(crossing, 2)),
                    "a one-block arm should bar just the one block");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 300)
    public static void breakingThePostTakesItsBarrierAway(GameTestHelper helper) {
        line(helper);
        LevelCrossingBlockEntity crossing = crossing(helper, 4);
        coach(helper, POST_X);
        ServerLevel level = helper.getLevel();
        BlockPos post = crossing.getBlockPos();
        helper.runAfterDelay(CLOSED, () -> {
            helper.assertTrue(barrierAt(helper, road(crossing, 1)), "the arm should be down");
            helper.assertTrue(LevelCrossingBlock.armDownOver(level, road(crossing, 3)), "the barrier should know its arm's down over it");
            level.destroyBlock(post, false);
            for (int i = 1; i <= 4; i++) {
                helper.assertTrue(!barrierAt(helper, post.west(i)), "with the post gone, the barrier " + i + " out should go too");
            }
            // One left behind somehow, with no arm over it, should know it's not wanted.
            BlockPos stray = post.west(2);
            level.setBlock(stray, ModBlocks.CROSSING_ARM.get().defaultBlockState().setValue(CrossingArmBlock.AXIS, Direction.Axis.X), 3);
            helper.assertTrue(!LevelCrossingBlock.armDownOver(level, stray), "a stray barrier should find no arm down over it");
            level.getBlockState(stray).randomTick(level, stray, level.getRandom());
            helper.assertTrue(level.getBlockState(stray).isAir(), "and go");
            helper.succeed();
        });
    }
}
