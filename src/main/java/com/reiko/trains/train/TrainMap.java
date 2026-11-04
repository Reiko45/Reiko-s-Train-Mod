package com.reiko.trains.train;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.config.TrainsConfig;
import com.reiko.trains.network.TrainMapPayload;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Where every train in a world is, for minimap mods: one entry per train, at its leading locomotive (trains without a
 * locomotive aren't on it). Kept up as the trains run, saved with the world, and kept for trains whose chunks unload
 * (where they last stood) until they're broken up. Every couple of seconds each player is sent the trains in their
 * dimension (not the one they're on), unless the server turns it off.
 */
@EventBusSubscriber(modid = ReikoTrains.MODID)
public final class TrainMap extends SavedData {
    private static final String NAME = ReikoTrains.MODID + "_trains";
    /** How often players are told where the trains are, in ticks. */
    static final int SEND_INTERVAL = 40;
    /** The most trains sent to a player (the nearest). */
    static final int MAX_SENT = 256;

    /** A train: its leading locomotive, where it is, what kind it is, how many cars, and its timetable's stop. */
    public record Entry(UUID id, Vec3 pos, ResourceLocation type, int cars, String stop, boolean atStop) {
    }

    private final Map<UUID, Entry> trains = new LinkedHashMap<>();

    private TrainMap() {
    }

    public static TrainMap get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(TrainMap::new, TrainMap::load), NAME);
    }

    public Collection<Entry> trains() {
        return trains.values();
    }

    @Nullable
    public Entry get(UUID id) {
        return trains.get(id);
    }

    public void put(Entry entry) {
        if (!entry.equals(trains.put(entry.id(), entry))) {
            setDirty();
        }
    }

    public void remove(UUID id) {
        if (trains.remove(id) != null) {
            setDirty();
        }
    }

    /**
     * Notes where a locomotive's train is: an entry for it if it leads its train (the one driven, or on a timetable,
     * or else the first), and none if another locomotive does.
     */
    static void note(Locomotive loco) {
        if (!(loco.level() instanceof ServerLevel level)) {
            return;
        }
        List<TrainCarEntity> cars = Trains.train(loco);
        int leader = Trains.leaderIndex(cars);
        TrainMap map = get(level);
        if (leader < 0 || cars.get(leader) != loco) {
            map.remove(loco.getUUID());
            return;
        }
        Autopilot autopilot = loco.autopilot();
        Timetable.Stop stop = autopilot.isOn() ? autopilot.current() : null;
        map.put(new Entry(loco.getUUID(), loco.position(), BuiltInRegistries.ENTITY_TYPE.getKey(loco.getType()), cars.size(),
                stop == null ? "" : stop.name(), stop != null && autopilot.isDwelling()));
    }

    /** A locomotive's left the world: broken up, it's off the map; unloaded, its train stays where it is. */
    static void removed(Locomotive loco) {
        if (!(loco.level() instanceof ServerLevel level) || loco.getRemovalReason() == null) {
            return;
        }
        TrainMap map = get(level);
        if (loco.getRemovalReason().shouldDestroy()) {
            map.remove(loco.getUUID());
        } else {
            Entry entry = map.get(loco.getUUID());
            if (entry != null) {
                map.put(new Entry(entry.id(), loco.position(), entry.type(), entry.cars(), entry.stop(), entry.atStop()));
            }
        }
    }

    /**
     * Drops trains that aren't there any more: where an entry's chunk and those around it are loaded and its
     * locomotive isn't (say the world was edited while it was unloaded).
     */
    public void prune(ServerLevel level) {
        List<UUID> gone = new ArrayList<>();
        for (Entry entry : trains.values()) {
            if (level.getEntity(entry.id()) == null && loadedAround(level, entry.pos())) {
                gone.add(entry.id());
            }
        }
        gone.forEach(this::remove);
    }

    private static boolean loadedAround(ServerLevel level, Vec3 pos) {
        ChunkPos chunk = new ChunkPos(BlockPos.containing(pos));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.areEntitiesLoaded(ChunkPos.asLong(chunk.x + dx, chunk.z + dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The trains to show someone in {@code level} at {@code at}, nearest first, leaving out the one they're on (if
     * {@code riding} is a car of it).
     */
    public List<TrainMapPayload.Pin> pinsFor(ServerLevel level, Vec3 at, @Nullable Entity riding) {
        UUID own = null;
        if (riding instanceof TrainCarEntity car) {
            List<TrainCarEntity> cars = Trains.train(car);
            int leader = Trains.leaderIndex(cars);
            if (leader >= 0) {
                own = cars.get(leader).getUUID();
            }
        }
        List<TrainMapPayload.Pin> pins = new ArrayList<>();
        for (Entry entry : trains.values()) {
            if (!entry.id().equals(own)) {
                // A loaded train's where it is now, not where it was last noted.
                Entity loco = level.getEntity(entry.id());
                if (loco instanceof Locomotive running) {
                    net.minecraft.world.entity.player.Player driver = running.driver();
                    int kmh = (int) Math.round(Math.abs(running.trainSpeed()) * 72);
                    pins.add(new TrainMapPayload.Pin(entry.id(), loco.position(), entry.type(), entry.cars(), entry.stop(), entry.atStop(), true,
                            kmh, driver == null ? "" : driver.getGameProfile().getName(), running.autopilot().isOn()));
                } else {
                    pins.add(new TrainMapPayload.Pin(entry.id(), entry.pos(), entry.type(), entry.cars(), entry.stop(), entry.atStop()));
                }
            }
        }
        pins.sort(Comparator.comparingDouble(pin -> pin.pos().distanceToSqr(at)));
        return pins.size() > MAX_SENT ? List.copyOf(pins.subList(0, MAX_SENT)) : pins;
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        int tick = event.getServer().getTickCount();
        if (tick % SEND_INTERVAL != 0) {
            return;
        }
        boolean show = TrainsConfig.TRAINS_ON_MINIMAPS.getAsBoolean();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            TrainMap map = get(level);
            map.prune(level);
            for (ServerPlayer player : level.players()) {
                List<TrainMapPayload.Pin> pins = show ? map.pinsFor(level, player.position(), player.getVehicle()) : List.of();
                PacketDistributor.sendToPlayer(player, new TrainMapPayload(level.dimension().location(), pins));
            }
        }
    }

    /** Before the world's saved: where the loaded trains are now. */
    @SubscribeEvent
    static void onSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level) {
            for (Locomotive loco : level.getEntities(EntityTypeTest.forClass(Locomotive.class), loco -> true)) {
                note(loco);
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry entry : trains.values()) {
            CompoundTag train = new CompoundTag();
            train.putUUID("Id", entry.id());
            train.putDouble("X", entry.pos().x);
            train.putDouble("Y", entry.pos().y);
            train.putDouble("Z", entry.pos().z);
            train.putString("Type", entry.type().toString());
            train.putInt("Cars", entry.cars());
            if (!entry.stop().isEmpty()) {
                train.putString("Stop", entry.stop());
                train.putBoolean("AtStop", entry.atStop());
            }
            list.add(train);
        }
        tag.put("Trains", list);
        return tag;
    }

    public static TrainMap load(CompoundTag tag, HolderLookup.Provider registries) {
        TrainMap map = new TrainMap();
        for (Tag t : tag.getList("Trains", Tag.TAG_COMPOUND)) {
            CompoundTag train = (CompoundTag) t;
            ResourceLocation type = ResourceLocation.tryParse(train.getString("Type"));
            if (!train.hasUUID("Id") || type == null) {
                continue;
            }
            Entry entry = new Entry(train.getUUID("Id"), new Vec3(train.getDouble("X"), train.getDouble("Y"), train.getDouble("Z")), type,
                    train.getInt("Cars"), train.getString("Stop"), train.getBoolean("AtStop"));
            map.trains.put(entry.id(), entry);
        }
        return map;
    }
}
