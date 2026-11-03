package com.reiko.trains.client;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.config.TrainsClientConfig;
import com.reiko.trains.network.TrainMapPayload;
import com.reiko.trains.train.Locomotive;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Trains on minimaps: the server sends where every train in your dimension is every couple of seconds
 * ({@link TrainMapPayload}), and this puts them on Xaero's Minimap if it's installed ({@link TrainsXaero}), each at its
 * leading locomotive, named with its kind, its length and where it's going. Trains near enough to be loaded are shown
 * where they are now, as they move.
 */
public final class TrainMinimap {
    static final boolean XAERO = ModList.get().isLoaded("xaerominimap");
    /** How often the minimap's trains are moved, in ticks. */
    private static final int UPDATE_INTERVAL = 5;

    /** A train as the minimap shows it. */
    record Shown(UUID id, Vec3 pos, String name, String initials, String kind) {
    }

    @Nullable
    private static TrainMapPayload latest;
    private static boolean shown;
    /** Xaero's Minimap went wrong (a version this doesn't fit): left alone from then on. */
    private static boolean broken;
    private static int ticks;

    private TrainMinimap() {
    }

    /** The trains, from the server. */
    public static void receive(TrainMapPayload payload) {
        latest = payload;
    }

    static void tick(Minecraft minecraft) {
        if (!XAERO || broken || ++ticks % UPDATE_INTERVAL != 0) {
            return;
        }
        boolean on = minecraft.level != null && TrainsClientConfig.TRAINS_ON_MINIMAP.getAsBoolean();
        if (!on && !shown) {
            return;
        }
        try {
            TrainsXaero.update(minecraft, on ? shown(minecraft) : List.of());
            shown = on;
        } catch (LinkageError | RuntimeException e) {
            broken = true;
            ReikoTrains.LOGGER.warn("Couldn't show trains on Xaero's Minimap (a version this mod doesn't know?)", e);
        }
    }

    /** Leaving a world: off the minimap with them. */
    static void reset() {
        latest = null;
        if (XAERO && !broken && shown) {
            try {
                TrainsXaero.reset();
            } catch (LinkageError | RuntimeException e) {
                broken = true;
            }
        }
        shown = false;
    }

    /** The trains to show: those in the player's dimension, loaded ones where they are now. */
    static List<Shown> shown(Minecraft minecraft) {
        TrainMapPayload payload = latest;
        List<Shown> trains = new ArrayList<>();
        if (payload == null || minecraft.level == null || !payload.dimension().equals(minecraft.level.dimension().location())) {
            return trains;
        }
        Map<UUID, Entity> loaded = new HashMap<>();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof Locomotive) {
                loaded.put(entity.getUUID(), entity);
            }
        }
        for (TrainMapPayload.Pin pin : payload.trains()) {
            Entity loco = loaded.get(pin.id());
            Vec3 pos = loco != null ? loco.position() : pin.pos();
            trains.add(new Shown(pin.id(), pos, name(pin), initials(pin), pin.type().getPath()));
        }
        return trains;
    }

    /** "Express Locomotive · 4 cars · to Riverside". */
    static String name(TrainMapPayload.Pin pin) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(pin.type());
        StringBuilder name = new StringBuilder(type.getDescription().getString());
        if (pin.cars() > 1) {
            name.append(" · ").append(Component.translatable("minimap.reikotrains.cars", pin.cars()).getString());
        }
        if (!pin.stop().isEmpty()) {
            name.append(" · ").append(Component.translatable(pin.atStop() ? "minimap.reikotrains.at" : "minimap.reikotrains.to", pin.stop()).getString());
        }
        return name.toString();
    }

    /** Two letters for the minimap's marker: the kind of locomotive. */
    static String initials(TrainMapPayload.Pin pin) {
        String key = "minimap.reikotrains.initials." + pin.type().getPath();
        Component initials = Component.translatable(key);
        String text = initials.getString();
        if (!text.equals(key)) {
            return text;
        }
        String name = BuiltInRegistries.ENTITY_TYPE.get(pin.type()).getDescription().getString();
        return name.length() <= 2 ? name : name.substring(0, 2);
    }
}
