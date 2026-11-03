package xaero.hud.minimap.world;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.resources.ResourceLocation;
import xaero.common.minimap.waypoints.Waypoint;

/** Compile-time stand-in for Xaero's Minimap (see build.gradle): only what Reiko's Train Mod uses. */
public class MinimapWorldManager {
    public MinimapWorld getCurrentWorld() {
        throw new UnsupportedOperationException();
    }

    public Int2ObjectMap<Waypoint> getCustomWaypoints(ResourceLocation modId) {
        throw new UnsupportedOperationException();
    }
}
