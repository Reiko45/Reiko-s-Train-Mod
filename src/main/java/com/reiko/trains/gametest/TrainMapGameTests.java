package com.reiko.trains.gametest;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.network.TrainMapPayload;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.signal.StationBlockEntity;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.train.DieselLocomotive;
import com.reiko.trains.train.Locomotive;
import com.reiko.trains.train.PassengerCoach;
import com.reiko.trains.train.SteamLocomotive;
import com.reiko.trains.train.TrainMap;
import com.reiko.trains.train.Trains;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Game tests for the map of trains that minimap mods show. */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class TrainMapGameTests {
    private static final String LINE = "line";
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3 WEST = new Vec3(-1, 0, 0);

    private TrainMapGameTests() {
    }

    /** Track along x at z = 4 (y = 1), the whole 64 blocks, and another at z = 7. */
    private static void lines(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 64, 9);
        TestTracks.line(level, at(helper, 0), TrackAxis.X, 64);
        TestTracks.line(level, helper.absolutePos(new BlockPos(0, 1, 7)), TrackAxis.X, 64);
    }

    private static BlockPos at(GameTestHelper helper, int x) {
        return helper.absolutePos(new BlockPos(x, 1, 4));
    }

    private static TrainMap map(GameTestHelper helper) {
        return TrainMap.get(helper.getLevel());
    }

    @GameTest(template = LINE, timeoutTicks = 60)
    public static void aTrainIsOnTheMapAtItsLeadingLocomotive(GameTestHelper helper) {
        lines(helper);
        SteamLocomotive front = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 30), EAST);
        PassengerCoach coach = TestTracks.car(helper.getLevel(), ModEntities.PASSENGER_COACH.get(), at(helper, 23), EAST);
        DieselLocomotive back = TestTracks.car(helper.getLevel(), ModEntities.DIESEL_LOCOMOTIVE.get(), at(helper, 15), EAST);
        Trains.couple(front, coach);
        Trains.couple(coach, back);
        helper.runAfterDelay(25, () -> {
            TrainMap.Entry forFront = map(helper).get(front.getUUID());
            TrainMap.Entry forBack = map(helper).get(back.getUUID());
            helper.assertTrue((forFront == null) != (forBack == null), "the train should be on the map once, by one of its locomotives");
            TrainMap.Entry entry = forFront != null ? forFront : forBack;
            Locomotive leader = forFront != null ? front : back;
            helper.assertTrue(entry.cars() == 3, "it should have 3 cars, not " + entry.cars());
            helper.assertTrue(entry.type().equals(BuiltInRegistries.ENTITY_TYPE.getKey(leader.getType())), "it should be the leading locomotive's kind");
            helper.assertTrue(entry.pos().distanceTo(leader.position()) < 0.01, "it should be where the leading locomotive is");
            helper.assertTrue(entry.stop().isEmpty(), "it isn't on a timetable");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 80)
    public static void aTimetabledTrainShowsWhereItsGoing(GameTestHelper helper) {
        lines(helper);
        StationBlockEntity station = TestTracks.station(helper.getLevel(), helper.absolutePos(new BlockPos(58, 1, 2)), at(helper, 58), "Far End", 5);
        SteamLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 10), EAST);
        loco.setFuel(16000);
        loco.autopilot().start(List.of(TestTracks.stop(station)));
        helper.runAfterDelay(25, () -> {
            TrainMap.Entry entry = map(helper).get(loco.getUUID());
            helper.assertTrue(entry != null, "the train should be on the map");
            helper.assertTrue(entry.stop().equals("Far End") && !entry.atStop(), "it should be going to Far End: " + entry.stop() + " " + entry.atStop());
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 100)
    public static void aBrokenLocomotiveComesOffTheMap(GameTestHelper helper) {
        lines(helper);
        SteamLocomotive first = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 30), EAST);
        SteamLocomotive second = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 22), EAST);
        Trains.couple(first, second);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue((map(helper).get(first.getUUID()) == null) != (map(helper).get(second.getUUID()) == null),
                    "the pair should be on the map once");
            Locomotive leader = map(helper).get(first.getUUID()) != null ? first : second;
            Locomotive other = leader == first ? second : first;
            leader.kill();
            helper.assertTrue(map(helper).get(leader.getUUID()) == null, "a broken locomotive should come straight off the map");
            helper.runAfterDelay(25, () -> {
                TrainMap.Entry entry = map(helper).get(other.getUUID());
                helper.assertTrue(entry != null && entry.cars() == 1, "the one left should be on the map by itself");
                other.discard();
                helper.assertTrue(map(helper).get(other.getUUID()) == null, "a removed locomotive should come off the map");
                helper.succeed();
            });
        });
    }

    @GameTest(template = LINE, timeoutTicks = 60)
    public static void playersAreSentTheOtherTrains(GameTestHelper helper) {
        lines(helper);
        SteamLocomotive mine = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 12), EAST);
        PassengerCoach coach = TestTracks.car(helper.getLevel(), ModEntities.PASSENGER_COACH.get(), at(helper, 5), EAST);
        Trains.couple(mine, coach);
        DieselLocomotive near = TestTracks.car(helper.getLevel(), ModEntities.DIESEL_LOCOMOTIVE.get(), helper.absolutePos(new BlockPos(20, 1, 7)), WEST);
        DieselLocomotive far = TestTracks.car(helper.getLevel(), ModEntities.DIESEL_LOCOMOTIVE.get(), helper.absolutePos(new BlockPos(50, 1, 7)), WEST);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(coach.position());
        player.startRiding(coach);
        helper.runAfterDelay(25, () -> {
            List<TrainMapPayload.Pin> pins = map(helper).pinsFor(helper.getLevel(), player.position(), player.getVehicle()).stream()
                    .filter(pin -> pin.id().equals(mine.getUUID()) || pin.id().equals(near.getUUID()) || pin.id().equals(far.getUUID()))
                    .toList();
            helper.assertTrue(pins.size() == 2, "the player should be sent the two other trains, not their own: " + pins.size());
            helper.assertTrue(pins.get(0).id().equals(near.getUUID()) && pins.get(1).id().equals(far.getUUID()), "nearest first");
            // The message carries them all the way.
            TrainMapPayload payload = new TrainMapPayload(helper.getLevel().dimension().location(), pins);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            TrainMapPayload.STREAM_CODEC.encode(buf, payload);
            TrainMapPayload read = TrainMapPayload.STREAM_CODEC.decode(buf);
            helper.assertTrue(read.equals(payload), "the message should arrive as it was sent");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 40)
    public static void trainsThatArentThereAnyMoreArePruned(GameTestHelper helper) {
        lines(helper);
        ServerLevel level = helper.getLevel();
        TrainMap map = map(helper);
        ResourceLocation steam = BuiltInRegistries.ENTITY_TYPE.getKey(ModEntities.STEAM_LOCOMOTIVE.get());
        UUID here = UUID.randomUUID();
        UUID farAway = UUID.randomUUID();
        map.put(new TrainMap.Entry(here, Vec3.atCenterOf(at(helper, 30)), steam, 1, "", false));
        map.put(new TrainMap.Entry(farAway, new Vec3(29_000_000, 64, 29_000_000), steam, 1, "", false));
        helper.runAfterDelay(5, () -> {
            map.prune(level);
            boolean farKept = map.get(farAway) != null;
            map.remove(farAway);
            helper.assertTrue(map.get(here) == null, "a train that isn't where it should be, there in a loaded place, should be dropped");
            helper.assertTrue(farKept, "a train in an unloaded place should be kept");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 40)
    public static void theMapIsSavedWithTheWorld(GameTestHelper helper) {
        lines(helper);
        TrainMap map = map(helper);
        UUID id = UUID.randomUUID();
        TrainMap.Entry entry = new TrainMap.Entry(id, new Vec3(29_000_100.5, 70, -29_000_000.25),
                BuiltInRegistries.ENTITY_TYPE.getKey(ModEntities.ELECTRIC_LOCOMOTIVE.get()), 5, "Riverside", true);
        map.put(entry);
        CompoundTag saved = map.save(new CompoundTag(), helper.getLevel().registryAccess());
        map.remove(id);
        TrainMap loaded = TrainMap.load(saved, helper.getLevel().registryAccess());
        helper.assertTrue(entry.equals(loaded.get(id)), "the train should be saved and loaded as it was: " + loaded.get(id));
        helper.succeed();
    }
}
