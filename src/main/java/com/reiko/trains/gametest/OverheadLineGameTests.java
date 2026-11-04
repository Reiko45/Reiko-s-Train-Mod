package com.reiko.trains.gametest;

import buildcraftreborn.api.mj.IMjReceiver;
import buildcraftreborn.api.mj.MjAPI;
import com.reiko.trains.ReikoTrains;
import com.reiko.trains.config.TrainsConfig;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.registry.ModDataComponents;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.registry.ModItems;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.train.BulletCoach;
import com.reiko.trains.train.BulletTrain;
import com.reiko.trains.train.ElectricLocomotive;
import com.reiko.trains.train.Locomotive;
import com.reiko.trains.train.Trains;
import com.reiko.trains.wire.CatenaryMastBlockEntity;
import com.reiko.trains.wire.OverheadLines;
import com.reiko.trains.wire.SubstationBlock;
import com.reiko.trains.wire.SubstationBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Game tests for overhead lines: masts, wire, substations, and electrics running from the wire. */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class OverheadLineGameTests {
    private static final String LINE = "line";
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3 WEST = new Vec3(-1, 0, 0);

    private OverheadLineGameTests() {
    }

    /** Track along x at z = 4 (y = 1), the whole 64 blocks, on a floor. */
    private static void line(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 64, 9);
        TestTracks.line(level, at(helper, 0), TrackAxis.X, 64);
    }

    private static BlockPos at(GameTestHelper helper, int x) {
        return helper.absolutePos(new BlockPos(x, 1, 4));
    }

    /** Where a mast at {@code x} along the line goes: two blocks south of the track ({@code z} = 6), or north ({@code z} = 2). */
    private static BlockPos mastAt(GameTestHelper helper, int x, int z) {
        return helper.absolutePos(new BlockPos(x, 1, z));
    }

    private static CatenaryMastBlockEntity mast(GameTestHelper helper, int x, int z) {
        return TestTracks.mast(helper.getLevel(), mastAt(helper, x, z));
    }

    /** Masts at x = 2, 30 and 58, south of the track, wired together: the whole line under wire. */
    private static List<BlockPos> wiredLine(GameTestHelper helper) {
        List<BlockPos> masts = List.of(mast(helper, 2, 6).getBlockPos(), mast(helper, 30, 6).getBlockPos(), mast(helper, 58, 6).getBlockPos());
        TestTracks.wire(helper.getLevel(), masts.toArray(BlockPos[]::new));
        return masts;
    }

    private static OverheadLines lines(GameTestHelper helper) {
        return OverheadLines.get(helper.getLevel());
    }

    /** Clicks a block with whatever the player's holding. */
    private static void click(Player player, BlockPos pos) {
        ItemStack stack = player.getMainHandItem();
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
    }

    private static Player board(GameTestHelper helper, Locomotive loco) {
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        driver.moveTo(loco.position());
        driver.startRiding(loco);
        return driver;
    }

    @GameTest(template = LINE)
    public static void mastsHoldTheWireOverTheTrack(GameTestHelper helper) {
        line(helper);
        CatenaryMastBlockEntity south = mast(helper, 10, 6);
        CatenaryMastBlockEntity north = mast(helper, 20, 2);
        BlockPos track = at(helper, 10);
        for (CatenaryMastBlockEntity mast : List.of(south, north)) {
            Vec3 wire = mast.wire();
            helper.assertTrue(wire != null && lines(helper).wire(mast.getBlockPos()) != null, "a mast by the track should be on the line");
            helper.assertTrue(Math.abs(wire.z - (track.getZ() + 0.5)) < 0.01, "holding the wire over the track's middle, not at z " + wire.z);
            helper.assertTrue(Math.abs(wire.x - (mast.getBlockPos().getX() + 0.5)) < 0.3, "level with the mast");
            double above = wire.y - track.getY();
            helper.assertTrue(above > OverheadLines.WIRE_HEIGHT - 0.01 && above < OverheadLines.WIRE_HEIGHT + 0.3,
                    "about " + OverheadLines.WIRE_HEIGHT + " blocks above the rails, not " + above);
        }
        helper.assertTrue(south.getBlockState().getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING) == Direction.NORTH,
                "the south mast's arm should point north, at the track");
        // Too far from any track, it won't go up.
        helper.assertTrue(com.reiko.trains.wire.CatenaryMastBlock.wireFor(helper.getLevel(), mastAt(helper, 30, 8).above(3)) == null
                && com.reiko.trains.wire.CatenaryMastBlock.wireFor(helper.getLevel(), helper.absolutePos(new BlockPos(30, 1, 9))) == null,
                "five blocks from the track is too far for a mast");
        helper.succeed();
    }

    @GameTest(template = LINE)
    public static void wireIsStrungMastToMastByHand(GameTestHelper helper) {
        line(helper);
        BlockPos a = mast(helper, 4, 6).getBlockPos();
        BlockPos b = mast(helper, 30, 6).getBlockPos();
        BlockPos c = mast(helper, 56, 6).getBlockPos();
        OverheadLines lines = lines(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.OVERHEAD_WIRE.get(), 8));
        ItemStack wire = player.getMainHandItem();

        click(player, a);
        helper.assertTrue(a.equals(wire.get(ModDataComponents.WIRE_FROM.get())), "clicking a mast should start a span there");
        click(player, c);
        helper.assertTrue(!lines.connected(a, c) && wire.getCount() == 8, "52 blocks is too far for one span, and takes no wire");
        click(player, b);
        helper.assertTrue(lines.connected(a, b) && wire.getCount() == 7, "26 blocks is fine, and takes one wire");
        helper.assertTrue(b.equals(wire.get(ModDataComponents.WIRE_FROM.get())), "ready to go on from the mast just wired");
        click(player, c);
        helper.assertTrue(lines.connected(b, c) && wire.getCount() == 6, "and on to the next");
        click(player, b);
        helper.assertTrue(wire.getCount() == 6, "masts already wired together take no more wire");
        click(player, c);
        helper.assertTrue(!wire.has(ModDataComponents.WIRE_FROM.get()), "clicking the same mast twice stops stringing");

        BlockPos line = lines.line(a);
        helper.assertTrue(line != null && line.equals(lines.line(c)) && lines.masts(line) == 3, "three masts wired together make one line");
        CatenaryMastBlockEntity middle = (CatenaryMastBlockEntity) helper.getLevel().getBlockEntity(b);
        helper.assertTrue(middle.links().keySet().equals(java.util.Set.of(a, c)), "the middle mast should know both its spans, to draw them");

        // Sneak-click a mast: its wires come down, and come back.
        player.setShiftKeyDown(true);
        click(player, b);
        helper.assertTrue(lines.spans().stream().noneMatch(s -> s.a().equals(b) || s.b().equals(b)), "the middle mast's spans should be down");
        helper.assertTrue(wire.getCount() == 8, "giving back both wires");
        helper.assertTrue(lines.line(a) != null && !lines.line(a).equals(lines.line(c)), "leaving the end masts on lines of their own");
        helper.succeed();
    }

    @GameTest(template = LINE)
    public static void aMastHoldsFourSpansAtMost(GameTestHelper helper) {
        line(helper);
        BlockPos hub = mast(helper, 30, 6).getBlockPos();
        BlockPos[] others = {mast(helper, 14, 6).getBlockPos(), mast(helper, 46, 6).getBlockPos(), mast(helper, 20, 2).getBlockPos(),
                mast(helper, 40, 2).getBlockPos()};
        for (BlockPos other : others) {
            TestTracks.wire(helper.getLevel(), hub, other);
        }
        BlockPos fifth = mast(helper, 52, 2).getBlockPos();
        helper.assertTrue("full".equals(lines(helper).cantConnect(hub, fifth)), "a fifth span on one mast should be refused");
        helper.assertTrue(lines(helper).cantConnect(others[1], fifth) == null, "but the next mast along can take it");
        helper.succeed();
    }

    @GameTest(template = LINE)
    public static void breakingAMastDropsItsWire(GameTestHelper helper) {
        line(helper);
        List<BlockPos> masts = wiredLine(helper);
        BlockPos middle = masts.get(1);
        helper.getLevel().destroyBlock(middle, true);
        OverheadLines lines = lines(helper);
        helper.assertTrue(!lines.hasMast(middle) && lines.spans().stream().noneMatch(s -> s.a().equals(middle) || s.b().equals(middle)),
                "the broken mast and its spans should be gone from the line");
        int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(middle).inflate(2)).stream()
                .filter(e -> e.getItem().is(ModItems.OVERHEAD_WIRE.get())).mapToInt(e -> e.getItem().getCount()).sum();
        helper.assertTrue(dropped == 2, "its two spans' wire should drop, dropped " + dropped);
        CatenaryMastBlockEntity end = (CatenaryMastBlockEntity) helper.getLevel().getBlockEntity(masts.get(0));
        helper.assertTrue(end.links().isEmpty(), "the mast at the other end should let go of its span");
        helper.succeed();
    }

    @GameTest(template = LINE, timeoutTicks = 60)
    public static void substationFeedsItsLineFromBuildcraft(GameTestHelper helper) {
        line(helper);
        List<BlockPos> masts = wiredLine(helper);
        ServerLevel level = helper.getLevel();
        BlockPos at = mastAt(helper, 30, 8);
        SubstationBlockEntity substation = TestTracks.substation(level, at);
        OverheadLines lines = lines(helper);
        BlockPos line = lines.line(masts.get(0));
        long capacity = TrainsConfig.SUBSTATION_CAPACITY.get();
        helper.assertTrue(masts.get(1).equals(lines.fedBy(at)) && line.equals(substation.line()), "it should feed the nearest mast's line");
        helper.assertTrue(lines.capacity(line) == capacity, "giving the line its store");
        IMjReceiver receiver = level.getCapability(MjAPI.CAP_RECEIVER, at, Direction.UP);
        helper.assertTrue(receiver != null, "BuildCraft engines should be able to power it");
        long excess = receiver.receivePower(capacity / 4 * MjAPI.MJ, false);
        helper.assertTrue(excess == 0 && Math.abs(lines.energy(line) - capacity / 4.0) < 1, "power in should go to the line, has " + lines.energy(line));
        excess = receiver.receivePower(capacity * MjAPI.MJ, false);
        helper.assertTrue(lines.energy(line) == capacity && Math.abs(excess - capacity / 4 * MjAPI.MJ) < MjAPI.MJ,
                "the line should take only what fits");
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(substation.getBlockState().getValue(SubstationBlock.LIVE)
                    && substation.getBlockState().getValue(SubstationBlock.LEVEL) == 4, "its lamp should be lit and all four bars up");
            helper.assertTrue(substation.comparatorSignal() == 15, "a full line gives a full comparator signal");
            helper.succeed();
        });
    }

    @GameTest(template = LINE)
    public static void linesShareTheirPowerWhenJoinedAndSplit(GameTestHelper helper) {
        line(helper);
        ServerLevel level = helper.getLevel();
        BlockPos a = mast(helper, 4, 6).getBlockPos();
        BlockPos b = mast(helper, 30, 6).getBlockPos();
        BlockPos c = mast(helper, 56, 6).getBlockPos();
        SubstationBlockEntity west = TestTracks.substation(level, mastAt(helper, 4, 8));
        SubstationBlockEntity east = TestTracks.substation(level, mastAt(helper, 56, 8));
        OverheadLines lines = lines(helper);
        long capacity = TrainsConfig.SUBSTATION_CAPACITY.get();
        west.addMj(600_000);
        east.addMj(200_000);
        helper.assertTrue(lines.energy(lines.line(a)) == 600_000 && lines.energy(lines.line(c)) == 200_000, "two lines, two stores");
        TestTracks.wire(level, a, b, c);
        BlockPos line = lines.line(a);
        helper.assertTrue(line.equals(lines.line(c)), "wired together they're one line");
        helper.assertTrue(lines.capacity(line) == 2 * capacity && Math.abs(lines.energy(line) - 800_000) < 1,
                "with both substations' room and all the power, has " + lines.energy(line));
        // Taking down the span between the middle and east masts: the west two keep two thirds, the east one a third.
        lines.disconnect(b, c);
        helper.assertTrue(Math.abs(lines.energy(lines.line(a)) - 800_000 * 2 / 3.0) < 1
                && Math.abs(lines.energy(lines.line(c)) - 800_000 / 3.0) < 1, "split, the power should be shared out by masts");
        // A line with no substation has no room to keep power in.
        level.destroyBlock(east.getBlockPos(), false);
        helper.assertTrue(lines.capacity(lines.line(c)) == 0 && lines.energy(lines.line(c)) == 0, "a line with no substation stores nothing");
        helper.succeed();
    }

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void flatElectricRunsAndChargesUnderALiveWire(GameTestHelper helper) {
        line(helper);
        List<BlockPos> masts = wiredLine(helper);
        SubstationBlockEntity substation = TestTracks.substation(helper.getLevel(), mastAt(helper, 30, 8));
        substation.addMj(1_000_000);
        BlockPos line = lines(helper).line(masts.get(0));
        ElectricLocomotive electric = TestTracks.car(helper.getLevel(), ModEntities.ELECTRIC_LOCOMOTIVE.get(), at(helper, 12), EAST);
        helper.assertTrue(electric.fuel() == 0, "its batteries should start flat");
        Player driver = board(helper, electric);
        double startX = electric.getX();
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(electric.isOnWire(), "under a live wire, its pantograph should be up on it");
            double charged = electric.fuel();
            helper.assertTrue(charged > 10 * TrainsConfig.WIRE_CHARGE_RATE.get(), "and its batteries charging from it, has " + charged);
            helper.assertTrue(Math.abs(lines(helper).energy(line) + charged - 1_000_000) < 50, "with the power coming out of the line");
            for (int i = 0; i < 2; i++) {
                electric.control(TrainControlPayload.THROTTLE_UP);
            }
        });
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(electric.getX() > startX + 2, "it should run from the wire");
            helper.assertTrue(electric.isOnWire(), "still on the wire");
            for (int i = 0; i < Locomotive.MAX_THROTTLE; i++) {
                electric.control(TrainControlPayload.THROTTLE_DOWN);
            }
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 100)
    public static void deadWireDoesNothing(GameTestHelper helper) {
        line(helper);
        wiredLine(helper);
        // A substation, but nothing powering it.
        TestTracks.substation(helper.getLevel(), mastAt(helper, 30, 8));
        ElectricLocomotive electric = TestTracks.car(helper.getLevel(), ModEntities.ELECTRIC_LOCOMOTIVE.get(), at(helper, 12), EAST);
        Player driver = board(helper, electric);
        for (int i = 0; i < 4; i++) {
            electric.control(TrainControlPayload.THROTTLE_UP);
        }
        double startX = electric.getX();
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(!electric.isOnWire() && electric.fuel() == 0, "a dead wire shouldn't charge it");
            helper.assertTrue(!electric.isRunning() && Math.abs(electric.getX() - startX) < 0.01, "or move it");
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 100)
    public static void bulletTrainChargesFromTheWireAtBothEnds(GameTestHelper helper) {
        line(helper);
        wiredLine(helper);
        TestTracks.substation(helper.getLevel(), mastAt(helper, 30, 8)).addMj(1_000_000);
        ServerLevel level = helper.getLevel();
        BulletTrain front = TestTracks.car(level, ModEntities.BULLET_TRAIN.get(), at(helper, 44), EAST);
        BulletCoach coach = TestTracks.car(level, ModEntities.BULLET_COACH.get(), at(helper, 35), EAST);
        BulletTrain back = TestTracks.car(level, ModEntities.BULLET_TRAIN.get(), at(helper, 26), WEST);
        helper.assertTrue(Trains.couple(front, coach) == Trains.CoupleResult.COUPLED && Trains.couple(coach, back) == Trains.CoupleResult.COUPLED,
                "the set should couple up");
        helper.runAfterDelay(30, () -> {
            for (BulletTrain car : List.of(front, back)) {
                helper.assertTrue(car.isOnWire() && car.fuel() > 10 * TrainsConfig.WIRE_CHARGE_RATE.get(),
                        "each driving car should have its pantograph up and be charging, has " + car.fuel());
            }
            helper.succeed();
        });
    }

    @GameTest(template = LINE)
    public static void overheadLinesSaveAndLoad(GameTestHelper helper) {
        line(helper);
        List<BlockPos> masts = wiredLine(helper);
        ServerLevel level = helper.getLevel();
        BlockPos substation = mastAt(helper, 30, 8);
        TestTracks.substation(level, substation).addMj(123_456);
        OverheadLines lines = lines(helper);
        CompoundTag tag = lines.save(new CompoundTag(), level.registryAccess());
        OverheadLines loaded = OverheadLines.load(tag, level.registryAccess());
        BlockPos line = loaded.line(masts.get(0));
        helper.assertTrue(line != null && line.equals(loaded.line(masts.get(2))) && loaded.masts(line) == 3, "the line should come back whole");
        for (BlockPos mast : masts) {
            helper.assertTrue(loaded.wire(mast).equals(lines.wire(mast)), "with each mast's wire where it was");
        }
        helper.assertTrue(loaded.connected(masts.get(0), masts.get(1)) && loaded.connected(masts.get(1), masts.get(2)), "and its spans");
        helper.assertTrue(masts.get(1).equals(loaded.fedBy(substation)), "its substation");
        helper.assertTrue(Math.abs(loaded.energy(line) - 123_456) < 0.01, "and its power, has " + loaded.energy(line));
        Vec3 over = Vec3.atBottomCenterOf(at(helper, 40));
        helper.assertTrue(line.equals(loaded.lineAbove(over)), "and know where its wire runs");
        helper.succeed();
    }

    @GameTest(template = LINE)
    public static void wireWontRunThroughBlocks(GameTestHelper helper) {
        line(helper);
        BlockPos a = mast(helper, 10, 6).getBlockPos();
        BlockPos b = mast(helper, 30, 6).getBlockPos();
        OverheadLines lines = lines(helper);
        Vec3 wireA = lines.wire(a);
        Vec3 wireB = lines.wire(b);
        helper.assertTrue(com.reiko.trains.wire.OverheadWireItem.blockedAt(helper.getLevel(), wireA, wireB) == null, "nothing in the way");
        BlockPos block = BlockPos.containing(wireA.lerp(wireB, 0.5));
        helper.getLevel().setBlock(block, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(block.equals(com.reiko.trains.wire.OverheadWireItem.blockedAt(helper.getLevel(), wireA, wireB)), "a block in the way, found");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.OVERHEAD_WIRE.get(), 4));
        click(player, a);
        click(player, b);
        helper.assertTrue(!lines.connected(a, b) && player.getMainHandItem().getCount() == 4, "so no span is strung, and no wire used");
        helper.getLevel().setBlock(block, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        click(player, b);
        helper.assertTrue(lines.connected(a, b), "with it cleared away, the span goes up");
        helper.succeed();
    }
}
