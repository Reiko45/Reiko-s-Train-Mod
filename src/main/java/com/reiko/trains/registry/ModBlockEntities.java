package com.reiko.trains.registry;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.freight.WagonLoaderBlockEntity;
import com.reiko.trains.signal.SignalBlockEntity;
import com.reiko.trains.signal.StationBlockEntity;
import com.reiko.trains.signal.SwitchBlockEntity;
import com.reiko.trains.track.TrackBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ReikoTrains.MODID);

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<TrackBlockEntity>> TRACK = BLOCK_ENTITIES.register("track",
            () -> BlockEntityType.Builder.of(TrackBlockEntity::new, ModBlocks.TRACK.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<SignalBlockEntity>> SIGNAL = BLOCK_ENTITIES.register("signal",
            () -> BlockEntityType.Builder.of(SignalBlockEntity::new, ModBlocks.SIGNAL.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<StationBlockEntity>> STATION = BLOCK_ENTITIES.register("station",
            () -> BlockEntityType.Builder.of(StationBlockEntity::new, ModBlocks.STATION.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<SwitchBlockEntity>> SWITCH = BLOCK_ENTITIES.register("switch_stand",
            () -> BlockEntityType.Builder.of(SwitchBlockEntity::new, ModBlocks.SWITCH.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<WagonLoaderBlockEntity>> WAGON_LOADER = BLOCK_ENTITIES.register("wagon_loader",
            () -> BlockEntityType.Builder.of(WagonLoaderBlockEntity::new, ModBlocks.WAGON_LOADER.get()).build(null));

    private ModBlockEntities() {
    }
}
