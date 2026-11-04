package com.reiko.trains;

import buildcraftreborn.api.mj.MjAPI;
import com.mojang.logging.LogUtils;
import com.reiko.trains.config.TrainsConfig;
import com.reiko.trains.registry.ModBlockEntities;
import com.reiko.trains.registry.ModBlocks;
import com.reiko.trains.registry.ModCreativeTabs;
import com.reiko.trains.registry.ModDataComponents;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.registry.ModItems;
import com.reiko.trains.registry.ModMenus;
import com.reiko.trains.registry.ModSounds;
import com.reiko.trains.train.DieselLocomotive;
import com.reiko.trains.train.TankWagon;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Reiko's Train Mod: two-block-wide steam trains (locomotive, coaches, freight wagons) on their own track, with
 * curves, ramps and junctions drawn between track blocks.
 */
@Mod(ReikoTrains.MODID)
public final class ReikoTrains {
    public static final String MODID = "reikotrains";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ReikoTrains(IEventBus modBus, ModContainer container) {
        ModDataComponents.COMPONENTS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, TrainsConfig.SPEC);
        modBus.addListener(ReikoTrains::registerCapabilities);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.reiko.trains.train.LivestockWagon::onDamage);
    }

    /** Freight wagons work with other mods' pipes and hoppers; a wagon loader passes goods through to the train. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // The wagon's hitbox parts share its entity type, and are containers too (passing through to it).
        ICapabilityProvider<Entity, Void, IItemHandler> wagon = (entity, context) -> entity instanceof Container container ? new InvWrapper(container) : null;
        ICapabilityProvider<Entity, Direction, IItemHandler> wagonSided = (entity, side) -> entity instanceof Container container ? new InvWrapper(container) : null;
        event.registerEntity(Capabilities.ItemHandler.ENTITY, ModEntities.FREIGHT_WAGON.get(), wagon);
        event.registerEntity(Capabilities.ItemHandler.ENTITY_AUTOMATION, ModEntities.FREIGHT_WAGON.get(), wagonSided);
        event.registerEntity(Capabilities.ItemHandler.ENTITY, ModEntities.TENDER.get(), wagon);
        event.registerEntity(Capabilities.ItemHandler.ENTITY, ModEntities.TRACK_LAYER.get(), wagon);
        event.registerEntity(Capabilities.ItemHandler.ENTITY_AUTOMATION, ModEntities.TRACK_LAYER.get(), wagonSided);
        event.registerEntity(Capabilities.ItemHandler.ENTITY_AUTOMATION, ModEntities.TENDER.get(), wagonSided);
        // A diesel's tank takes diesel from buckets, pipes and other mods' fluid containers (its hitbox parts too).
        event.registerEntity(Capabilities.FluidHandler.ENTITY, ModEntities.DIESEL_LOCOMOTIVE.get(), ReikoTrains::dieselTank);
        event.registerEntity(Capabilities.FluidHandler.ENTITY, ModEntities.TANK_WAGON.get(), ReikoTrains::tankWagon);
        // Steam engines' and tenders' water (an express's goes to its tender).
        for (net.minecraft.world.entity.EntityType<?> type : java.util.List.<net.minecraft.world.entity.EntityType<?>>of(
                ModEntities.STEAM_LOCOMOTIVE.get(), ModEntities.EXPRESS_LOCOMOTIVE.get(), ModEntities.TENDER.get())) {
            event.registerEntity(Capabilities.FluidHandler.ENTITY, type, ReikoTrains::waterTank);
        }
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.WATER_TOWER.get(), (tower, side) -> tower.tank());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.WAGON_LOADER.get(), (loader, side) -> loader.port());
        // An engine shed's store takes fuel, water and diesel at any block of its frame.
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, be, side) -> {
            com.reiko.trains.shed.EngineShedBlockEntity shed = com.reiko.trains.shed.EngineShedBlock.shed(level, state, pos);
            return shed == null ? null : shed.items();
        }, ModBlocks.ENGINE_SHED.get());
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, side) -> {
            com.reiko.trains.shed.EngineShedBlockEntity shed = com.reiko.trains.shed.EngineShedBlock.shed(level, state, pos);
            return shed == null ? null : shed.fluids();
        }, ModBlocks.ENGINE_SHED.get());
        // BuildCraft engines and kinesis pipes (and, as BuildCraft's config allows, other mods' energy) feed a charging
        // point from any side.
        for (BlockCapability<?, Direction> capability : java.util.List.<BlockCapability<?, Direction>>of(MjAPI.CAP_CONNECTOR,
                MjAPI.CAP_RECEIVER, MjAPI.CAP_REDSTONE_RECEIVER, MjAPI.CAP_READABLE, Capabilities.EnergyStorage.BLOCK)) {
            registerCharger(event, capability);
            registerSubstation(event, capability);
            registerShed(event, capability);
        }
    }

    private static <T> void registerSubstation(RegisterCapabilitiesEvent event, BlockCapability<T, Direction> capability) {
        event.registerBlockEntity(capability, ModBlockEntities.SUBSTATION.get(), (substation, side) -> substation.capabilities().getCapability(capability));
    }

    private static <T> void registerShed(RegisterCapabilitiesEvent event, BlockCapability<T, Direction> capability) {
        event.registerBlock(capability, (level, pos, state, be, side) -> {
            com.reiko.trains.shed.EngineShedBlockEntity shed = com.reiko.trains.shed.EngineShedBlock.shed(level, state, pos);
            return shed == null ? null : shed.capabilities().getCapability(capability);
        }, ModBlocks.ENGINE_SHED.get());
    }

    private static <T> void registerCharger(RegisterCapabilitiesEvent event, BlockCapability<T, Direction> capability) {
        event.registerBlockEntity(capability, ModBlockEntities.CHARGING_POINT.get(), (charger, side) -> charger.capabilities().getCapability(capability));
    }

    @Nullable
    private static IFluidHandler dieselTank(Entity entity, @Nullable Direction side) {
        Entity car = entity instanceof PartEntity<?> part ? part.getParent() : entity;
        return car instanceof DieselLocomotive diesel ? diesel.tank() : null;
    }

    @Nullable
    private static IFluidHandler waterTank(Entity entity, @Nullable Direction side) {
        Entity car = entity instanceof PartEntity<?> part ? part.getParent() : entity;
        if (car instanceof com.reiko.trains.train.SteamLocomotive steam) {
            com.reiko.trains.train.WaterStore store = steam.waterStore();
            return store == null ? null : new com.reiko.trains.train.WaterTank(store);
        }
        return car instanceof com.reiko.trains.train.Tender tender ? new com.reiko.trains.train.WaterTank(tender) : null;
    }

    @Nullable
    private static IFluidHandler tankWagon(Entity entity, @Nullable Direction side) {
        Entity car = entity instanceof PartEntity<?> part ? part.getParent() : entity;
        return car instanceof TankWagon wagon ? wagon.tank() : null;
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
