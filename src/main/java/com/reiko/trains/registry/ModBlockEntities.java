package com.reiko.trains.registry;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.freight.WagonLoaderBlockEntity;
import com.reiko.trains.power.ChargingPointBlockEntity;
import com.reiko.trains.signal.LevelCrossingBlockEntity;
import com.reiko.trains.signal.SignalBlockEntity;
import com.reiko.trains.signal.SpeedSignBlockEntity;
import com.reiko.trains.signal.TrackSensorBlockEntity;
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
            () -> BlockEntityType.Builder.of(TrackBlockEntity::new, ModBlocks.TRACK.get(), ModBlocks.STREET_TRACK.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<SignalBlockEntity>> SIGNAL = BLOCK_ENTITIES.register("signal",
            () -> BlockEntityType.Builder.of(SignalBlockEntity::new, ModBlocks.SIGNAL.get(), ModBlocks.THREE_LIGHT_SIGNAL.get(),
                    ModBlocks.FOUR_ASPECT_SIGNAL.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<StationBlockEntity>> STATION = BLOCK_ENTITIES.register("station",
            () -> BlockEntityType.Builder.of(StationBlockEntity::new, ModBlocks.STATION.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<SwitchBlockEntity>> SWITCH = BLOCK_ENTITIES.register("switch_stand",
            () -> BlockEntityType.Builder.of(SwitchBlockEntity::new, ModBlocks.SWITCH.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<WagonLoaderBlockEntity>> WAGON_LOADER = BLOCK_ENTITIES.register("wagon_loader",
            () -> BlockEntityType.Builder.of(WagonLoaderBlockEntity::new, ModBlocks.WAGON_LOADER.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<com.reiko.trains.water.WaterTowerBlockEntity>> WATER_TOWER = BLOCK_ENTITIES.register("water_tower",
            () -> BlockEntityType.Builder.of(com.reiko.trains.water.WaterTowerBlockEntity::new, ModBlocks.WATER_TOWER.get()).build(null));
    public static final Supplier<BlockEntityType<ChargingPointBlockEntity>> CHARGING_POINT = BLOCK_ENTITIES.register("charging_point",
            () -> BlockEntityType.Builder.of(ChargingPointBlockEntity::new, ModBlocks.CHARGING_POINT.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<com.reiko.trains.wire.CatenaryMastBlockEntity>> CATENARY_MAST = BLOCK_ENTITIES.register(
            "catenary_mast", () -> BlockEntityType.Builder.of(com.reiko.trains.wire.CatenaryMastBlockEntity::new, ModBlocks.CATENARY_MAST.get())
                    .build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<com.reiko.trains.wire.SubstationBlockEntity>> SUBSTATION = BLOCK_ENTITIES.register(
            "substation", () -> BlockEntityType.Builder.of(com.reiko.trains.wire.SubstationBlockEntity::new, ModBlocks.SUBSTATION.get())
                    .build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<SpeedSignBlockEntity>> SPEED_SIGN = BLOCK_ENTITIES.register("speed_sign",
            () -> BlockEntityType.Builder.of(SpeedSignBlockEntity::new, ModBlocks.SPEED_SIGN.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<TrackSensorBlockEntity>> TRACK_SENSOR = BLOCK_ENTITIES.register("track_sensor",
            () -> BlockEntityType.Builder.of(TrackSensorBlockEntity::new, ModBlocks.TRACK_SENSOR.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<LevelCrossingBlockEntity>> LEVEL_CROSSING = BLOCK_ENTITIES.register("level_crossing",
            () -> BlockEntityType.Builder.of(LevelCrossingBlockEntity::new, ModBlocks.LEVEL_CROSSING.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<com.reiko.trains.turntable.TurntableBlockEntity>> TURNTABLE = BLOCK_ENTITIES.register("turntable",
            () -> BlockEntityType.Builder.of(com.reiko.trains.turntable.TurntableBlockEntity::new, ModBlocks.TURNTABLE.get(),
                    ModBlocks.SMALL_TURNTABLE.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<com.reiko.trains.shed.EngineShedBlockEntity>> ENGINE_SHED = BLOCK_ENTITIES.register("engine_shed",
            () -> BlockEntityType.Builder.of(com.reiko.trains.shed.EngineShedBlockEntity::new, ModBlocks.ENGINE_SHED.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<com.reiko.trains.signal.SignalBoxBlockEntity>> SIGNAL_BOX = BLOCK_ENTITIES.register("signal_box",
            () -> BlockEntityType.Builder.of(com.reiko.trains.signal.SignalBoxBlockEntity::new, ModBlocks.SIGNAL_BOX.get()).build(null));

    private ModBlockEntities() {
    }
}
