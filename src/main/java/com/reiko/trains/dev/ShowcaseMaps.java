package com.reiko.trains.dev;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.client.TrainsWorldMap;
import com.reiko.trains.train.Locomotive;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;

/**
 * DEV ONLY: Xaero's World Map in the showcase (run with -PxaeroJars=... holding the world map too): opening it, as its
 * key would, and picking a train on it. Only by name: nothing here touches its classes.
 */
final class ShowcaseMaps {
    private ShowcaseMaps() {
    }

    /** Presses the world map's key (M, unless it's been changed), if it's installed. */
    static void openWorldMap(Minecraft minecraft) {
        for (KeyMapping key : minecraft.options.keyMappings) {
            if (key.getName().equals("gui.xaero_open_map")) {
                KeyMapping.click(key.getKey());
                ReikoTrains.LOGGER.info("Showcase: pressed {} for the world map", key.getKey().getName());
                return;
            }
        }
        ReikoTrains.LOGGER.info("Showcase: no world map installed");
    }

    /** Picks one of the trains on the loop, as if it had been clicked on the map. */
    static void pickLoopTrain(Minecraft minecraft, int y) {
        ReikoTrains.LOGGER.info("Showcase: map screen is {}", minecraft.screen == null ? null : minecraft.screen.getClass().getName());
        List<Locomotive> locos = minecraft.level.getEntitiesOfClass(Locomotive.class, new AABB(-10, y - 2, 8, 70, y + 4, 46));
        if (!locos.isEmpty()) {
            TrainsWorldMap.pick(locos.getFirst().getUUID());
        }
    }
}
