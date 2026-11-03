package com.reiko.trains.client;

import com.reiko.trains.ReikoTrains;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.MinimapWorldManager;

/**
 * Trains on Xaero's Minimap: the mod's own waypoints, one per train, on the minimap and in the world but not in your
 * waypoint list, coloured by the kind of locomotive. Only loaded when that mod is installed
 * ({@link TrainMinimap#XAERO}).
 */
final class TrainsXaero {
    private static final ResourceLocation TRAINS = ReikoTrains.id("trains");

    private TrainsXaero() {
    }

    @Nullable
    private static MinimapWorldManager manager() {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        return session == null ? null : session.getWorldManager();
    }

    /** Puts these trains on the minimap (and takes off any others), if it's showing the player's dimension. */
    static void update(Minecraft minecraft, List<TrainMinimap.Shown> trains) {
        MinimapWorldManager manager = manager();
        if (manager == null) {
            return;
        }
        MinimapWorld world = manager.getCurrentWorld();
        boolean here = world != null && minecraft.level != null && minecraft.level.dimension().equals(world.getDimId());
        Int2ObjectMap<Waypoint> pins = manager.getCustomWaypoints(TRAINS);
        if (!here || trains.isEmpty()) {
            pins.clear();
            return;
        }
        IntSet keep = new IntOpenHashSet();
        for (TrainMinimap.Shown train : trains) {
            int key = train.id().hashCode();
            keep.add(key);
            int x = (int) Math.floor(train.pos().x);
            int y = (int) Math.floor(train.pos().y);
            int z = (int) Math.floor(train.pos().z);
            Waypoint pin = pins.get(key);
            if (pin == null) {
                pins.put(key, new Waypoint(x, y, z, train.name(), train.initials(), color(train.kind()), WaypointPurpose.NORMAL, true, true));
            } else {
                pin.setX(x);
                pin.setY(y);
                pin.setZ(z);
                if (!pin.getName().equals(train.name())) {
                    pin.setName(train.name());
                }
            }
        }
        pins.keySet().removeIf((java.util.function.IntPredicate) key -> !keep.contains(key));
    }

    /** Takes them all off, on leaving a world. */
    static void reset() {
        MinimapWorldManager manager = manager();
        if (manager != null) {
            manager.getCustomWaypoints(TRAINS).clear();
        }
    }

    /** The locomotive's livery, near enough. */
    private static WaypointColor color(String kind) {
        return switch (kind) {
            case "steam_locomotive" -> WaypointColor.DARK_GREEN;
            case "express_locomotive" -> WaypointColor.DARK_RED;
            case "diesel_locomotive" -> WaypointColor.BLUE;
            case "electric_locomotive" -> WaypointColor.RED;
            case "bullet_train" -> WaypointColor.AQUA;
            default -> WaypointColor.WHITE;
        };
    }
}
