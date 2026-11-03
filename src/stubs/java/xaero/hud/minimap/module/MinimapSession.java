package xaero.hud.minimap.module;

import xaero.hud.minimap.world.MinimapWorldManager;
import xaero.hud.module.ModuleSession;

/** Compile-time stand-in for Xaero's Minimap (see build.gradle): only what Reiko's Train Mod uses. */
public abstract class MinimapSession extends ModuleSession<MinimapSession> {
    public MinimapWorldManager getWorldManager() {
        throw new UnsupportedOperationException();
    }
}
