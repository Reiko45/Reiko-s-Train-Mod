package xaero.hud.minimap.world;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Compile-time stand-in for Xaero's Minimap (see build.gradle): only what Reiko's Train Mod uses. */
public abstract class MinimapWorld {
    public ResourceKey<Level> getDimId() {
        throw new UnsupportedOperationException();
    }
}
