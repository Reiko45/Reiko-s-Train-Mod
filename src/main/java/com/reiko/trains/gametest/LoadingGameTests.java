package com.reiko.trains.gametest;

import com.mojang.authlib.GameProfile;
import com.reiko.trains.ReikoTrains;
import com.reiko.trains.item.TrainCarItem;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.track.TrackGraph;
import com.reiko.trains.track.TrackPos;
import com.reiko.trains.train.PassengerCoach;
import com.reiko.trains.train.SteamLocomotive;
import com.reiko.trains.train.TrainCarEntity;
import com.reiko.trains.train.Trains;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Game tests for trains that have been saved and loaded again (a world opened again, a player back in the game), and
 * for putting cars on the track anywhere along it.
 */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class LoadingGameTests {
    private static final String LINE = "line";
    private static final String YARD = "yard";
    private static final Vec3 EAST = new Vec3(1, 0, 0);

    private LoadingGameTests() {
    }

    private static void straight(GameTestHelper helper, int blocks) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 64, 9);
        TestTracks.line(level, helper.absolutePos(new BlockPos(0, 1, 4)), TrackAxis.X, blocks);
    }

    private static BlockPos onLine(GameTestHelper helper, int x) {
        return helper.absolutePos(new BlockPos(x, 1, 4));
    }

    /** Saves a car, takes it out of the world, and loads it back in, the way it would be when its chunk loads again. */
    @SuppressWarnings("unchecked")
    private static <T extends TrainCarEntity> T reload(GameTestHelper helper, T car) {
        ServerLevel level = helper.getLevel();
        CompoundTag saved = new CompoundTag();
        car.save(saved);
        car.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        Entity loaded = EntityType.loadEntityRecursive(saved, level, e -> e);
        helper.assertTrue(loaded != null && level.addFreshEntity(loaded), "the car should load back into the world");
        return (T) loaded;
    }

    private static ServerPlayer join(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID id = UUID.randomUUID();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(id, "load_" + id.toString().substring(0, 8)), false);
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        try {
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
        } catch (UnsupportedOperationException e) {
            // A mod sending a packet on login that a fake connection can't take: the player's in by then.
        }
        return player;
    }

    private static void leave(ServerPlayer player) {
        MinecraftServer server = player.server;
        if (server.getPlayerList().getPlayer(player.getUUID()) == player) {
            try {
                server.getPlayerList().remove(player);
            } catch (UnsupportedOperationException e) {
                // A packet the fake connection can't take, on the way out.
            }
        }
    }

    /** Stands a player beside a car, two and a half blocks to its north, facing it. */
    private static void standBeside(ServerPlayer player, TrainCarEntity car) {
        player.moveTo(car.getX(), car.getY(), car.getZ() - 2.5, 0, 20);
    }

    /** The car's hitbox section nearest the player: the one a player looking at it would click. */
    private static PartEntity<?> nearestSection(TrainCarEntity car, ServerPlayer player) {
        PartEntity<?> best = null;
        for (PartEntity<?> part : car.getParts()) {
            if (best == null || part.distanceToSqr(player) < best.distanceToSqr(player)) {
                best = part;
            }
        }
        return best;
    }

    @GameTest(template = LINE, timeoutTicks = 40)
    public static void carsCanBeClickedAfterLoading(GameTestHelper helper) {
        straight(helper, 40);
        ServerLevel level = helper.getLevel();
        PassengerCoach coach = reload(helper, TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), onLine(helper, 10), EAST));
        SteamLocomotive loco = reload(helper, TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), onLine(helper, 25), EAST));
        for (TrainCarEntity car : new TrainCarEntity[]{coach, loco}) {
            for (PartEntity<?> part : car.getParts()) {
                helper.assertTrue(part.position().distanceTo(car.position()) < car.halfLength() + 0.5,
                        "a loaded car's hitbox sections should be round it, not left at " + part.position());
            }
        }
        ServerPlayer player = join(helper);
        try {
            // Getting in: the click the client sends, checked by the server as a player's click is (within reach).
            standBeside(player, coach);
            player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(nearestSection(coach, player), false,
                    InteractionHand.MAIN_HAND));
            helper.assertTrue(player.getVehicle() == coach, "clicking a loaded coach should get the player in");
            player.stopRiding();
            // Hitting it: in creative, one hit takes it away.
            player.setGameMode(GameType.CREATIVE);
            standBeside(player, loco);
            player.connection.handleInteract(ServerboundInteractPacket.createAttackPacket(nearestSection(loco, player), false));
            helper.assertTrue(loco.isRemoved(), "hitting a loaded locomotive should break it");
            helper.succeed();
        } finally {
            leave(player);
        }
    }

    @GameTest(template = YARD, timeoutTicks = 40)
    public static void bogiesFollowTheCurveAfterLoading(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(2, 1, 4));
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 48, 48);
        TestTracks.line(level, start, TrackAxis.X, 15);
        TestTracks.connect(level, start.offset(14, 0, 0), start.offset(26, 0, 12));
        PassengerCoach coach = TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), start.offset(14, 0, 0), EAST);
        float front = coach.frontBogieYaw();
        float rear = coach.rearBogieYaw();
        helper.assertTrue(Math.abs(front - rear) > 2, "a coach going onto a curve has its bogies turned");
        PassengerCoach loaded = reload(helper, coach);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(Math.abs(loaded.frontBogieYaw() - front) < 0.6F && Math.abs(loaded.rearBogieYaw() - rear) < 0.6F,
                    "a loaded coach's bogies should follow the curve again (" + loaded.frontBogieYaw() + " / " + loaded.rearBogieYaw() + ")");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 300)
    public static void leavingTheGameInATrainLeavesTheTrainWhole(GameTestHelper helper) {
        straight(helper, 40);
        ServerLevel level = helper.getLevel();
        SteamLocomotive loco = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), onLine(helper, 22), EAST);
        PassengerCoach coach = TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), onLine(helper, 13), EAST);
        helper.assertTrue(Trains.couple(loco, coach) == Trains.CoupleResult.COUPLED, "they should couple");
        ServerPlayer player = join(helper);
        try {
            player.moveTo(coach.position());
            helper.assertTrue(player.startRiding(coach), "the player gets in");
        } finally {
            leave(player);
        }
        helper.assertTrue(!coach.isRemoved(), "the coach should stay on the track when its passenger leaves the game");
        helper.assertTrue(loco.getUUID().equals(coach.leaderId()) && coach.getUUID().equals(loco.followerId()), "still coupled");
        helper.runAfterDelay(250, () -> {
            helper.assertTrue(loco.getUUID().equals(coach.leaderId()) && coach.getUUID().equals(loco.followerId()),
                    "still coupled after the train's waited a while");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 400)
    public static void aCarWhoseCarInFrontIsGoneRunsOnItsOwn(GameTestHelper helper) {
        straight(helper, 40);
        ServerLevel level = helper.getLevel();
        SteamLocomotive loco = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), onLine(helper, 22), EAST);
        PassengerCoach coach = TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), onLine(helper, 13), EAST);
        Trains.couple(loco, coach);
        // The world all round loaded, so a car missing from it is gone rather than not loaded yet.
        ChunkPos at = coach.chunkPosition();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setChunkForced(at.x + dx, at.z + dz, true);
            }
        }
        // Gone without uncoupling (as if its chunk had been lost): the coach is left coupled to nothing.
        loco.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        helper.runAfterDelay(260, () -> {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    level.setChunkForced(at.x + dx, at.z + dz, false);
                }
            }
            helper.assertTrue(coach.leaderId() == null, "the coach should be let go of, to run on its own");
            coach.shove(0.2);
            helper.assertTrue(coach.trainSpeed() > 0.1, "and it should move again");
            helper.succeed();
        });
    }

    @GameTest(template = YARD, timeoutTicks = 40)
    public static void carsGoOnCurvesAndNearTheEndOfTheLine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(2, 1, 4));
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 48, 48);
        TestTracks.line(level, start, TrackAxis.X, 15);
        TestTracks.connect(level, start.offset(14, 0, 0), start.offset(34, 0, 20));
        // Clicked on the middle of the curve, where there's no track block: the coach goes there.
        Vec3 middle = new Vec3(start.getX() + 14 + 20 * Math.sin(Math.PI / 4) + 0.5, start.getY(), start.getZ() + 20 - 20 * Math.cos(Math.PI / 4) + 0.5);
        TrackPos clicked = TrackGraph.nearest(level, middle, new Vec3(1, 0, 1), 1.25);
        helper.assertTrue(clicked != null, "there's track under the middle of the curve");
        TrainCarEntity onCurve = ModEntities.PASSENGER_COACH.get().create(level);
        helper.assertTrue(TrainCarItem.place(level, onCurve, clicked) == null, "a coach goes on the middle of a curve");
        helper.assertTrue(onCurve.position().distanceTo(new Vec3(middle.x, onCurve.getY(), middle.z)) < 1.5, "where it was clicked");
        helper.assertTrue(TrackGraph.nearest(level, middle.add(0, 0, 6), EAST, 1.25) == null, "but not off the track beside it");
        // Clicked on the last block of the line: it goes as near the end as it fits, all on the track.
        TrainCarEntity atEnd = ModEntities.PASSENGER_COACH.get().create(level);
        helper.assertTrue(TrainCarItem.place(level, atEnd, start, EAST) == null, "a coach clicked onto the end of the line still goes on");
        helper.assertTrue(atEnd.front().isValid(level) && atEnd.rear().isValid(level), "with both bogies on the track");
        helper.assertTrue(Math.min(atEnd.front().position().x, atEnd.rear().position().x) < start.getX() + 0.6, "right up to the end");
        // But not on track shorter than it.
        BlockPos stub = helper.absolutePos(new BlockPos(2, 1, 40));
        TestTracks.line(level, stub, TrackAxis.X, 3);
        TrainCarEntity tooLong = ModEntities.PASSENGER_COACH.get().create(level);
        helper.assertTrue(TrainCarItem.place(level, tooLong, stub.offset(1, 0, 0), EAST) == TrainCarItem.Problem.NO_ROOM,
                "a coach doesn't go on three blocks of track");
        helper.succeed();
    }
}
