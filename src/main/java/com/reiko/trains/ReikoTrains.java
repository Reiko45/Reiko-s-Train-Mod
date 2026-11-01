package com.reiko.trains;

import com.mojang.logging.LogUtils;
import com.reiko.trains.config.TrainsConfig;
import com.reiko.trains.registry.ModCreativeTabs;
import com.reiko.trains.registry.ModDataComponents;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.registry.ModItems;
import com.reiko.trains.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Reiko's Train Mod: steam locomotives, passenger coaches and freight wagons that run on ordinary rails and can be
 * coupled together into trains.
 */
@Mod(ReikoTrains.MODID)
public final class ReikoTrains {
    public static final String MODID = "reikotrains";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ReikoTrains(IEventBus modBus, ModContainer container) {
        ModDataComponents.COMPONENTS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, TrainsConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
