package com.reiko.trains.gametest;

import com.mojang.authlib.GameProfile;
import com.reiko.trains.ReikoTrains;
import com.reiko.trains.advancement.TrainAdvancements;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.train.SteamLocomotive;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Game tests for the mod's advancements. */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class AdvancementGameTests {
    private static final String LINE = "line";
    private static final List<String> ALL = List.of("root", "full_steam_ahead", "ninety", "ding_ding", "under_the_wires", "coupled_up", "car_carrier",
            "right_away", "signalman", "round_we_go", "topped_up", "navvy", "over_the_river");

    private AdvancementGameTests() {
    }

    private static ServerPlayer join(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID id = UUID.randomUUID();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(id, "adv_" + id.toString().substring(0, 8)), false);
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

    private static boolean done(ServerPlayer player, String name) {
        AdvancementHolder holder = player.server.getAdvancements().get(ReikoTrains.id(name));
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    @GameTest(template = LINE, timeoutTicks = 60)
    public static void advancementsAreEarnedOnTheRailway(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        for (String name : ALL) {
            helper.assertTrue(server.getAdvancements().get(ReikoTrains.id(name)) != null, "no advancement " + name);
        }
        ServerPlayer player = join(helper);
        try {
            helper.assertTrue(!done(player, "root") && !done(player, "full_steam_ahead"), "not earned yet");
            TrainAdvancements.award(player, "place_car");
            helper.assertTrue(done(player, "root"), "putting a car on the track starts them off");
            TestTracks.floor(helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 0)), 64, 9);
            TestTracks.line(helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 4)), TrackAxis.X, 64);
            SteamLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), helper.absolutePos(new BlockPos(20, 1, 4)),
                    new Vec3(1, 0, 0));
            player.moveTo(loco.position());
            helper.assertTrue(player.startRiding(loco), "the player gets in");
            loco.control(TrainControlPayload.THROTTLE_UP);
            helper.assertTrue(done(player, "full_steam_ahead"), "opening the throttle earns Full Steam Ahead");
            TrainAdvancements.reach(player, "speed", 60);
            helper.assertTrue(!done(player, "ninety"), "60 km/h isn't The Ninety");
            TrainAdvancements.reach(player, "speed", 95);
            helper.assertTrue(done(player, "ninety"), "95 km/h is");
            TrainAdvancements.reach(player, "lay", 10);
            helper.assertTrue(!done(player, "navvy"), "10 pieces of track isn't Navvy");
            player.stopRiding();
            helper.succeed();
        } finally {
            if (server.getPlayerList().getPlayer(player.getUUID()) == player) {
                server.getPlayerList().remove(player);
            }
        }
    }
}
