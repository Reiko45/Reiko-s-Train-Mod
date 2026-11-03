package com.reiko.trains.registry;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.freight.WagonLoaderBlock;
import com.reiko.trains.power.ChargingPointBlock;
import com.reiko.trains.signal.CrossingArmBlock;
import com.reiko.trains.signal.LevelCrossingBlock;
import com.reiko.trains.signal.SignalBlock;
import com.reiko.trains.signal.SpeedSignBlock;
import com.reiko.trains.signal.TrackSensorBlock;
import com.reiko.trains.signal.StationBlock;
import com.reiko.trains.signal.SwitchBlock;
import com.reiko.trains.track.TrackBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ReikoTrains.MODID);

    public static final DeferredBlock<TrackBlock> TRACK = BLOCKS.register("track", () -> new TrackBlock(BlockBehaviour.Properties.of()
            .noCollission().strength(0.7F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion()));

    public static final DeferredBlock<SignalBlock> SIGNAL = BLOCKS.register("signal", () -> new SignalBlock(BlockBehaviour.Properties.of()
            .strength(1.5F).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 5).pushReaction(PushReaction.BLOCK)));
    public static final DeferredBlock<StationBlock> STATION = BLOCKS.register("station", () -> new StationBlock(BlockBehaviour.Properties.of()
            .strength(1.5F).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.BLOCK)));

    public static final DeferredBlock<SwitchBlock> SWITCH = BLOCKS.register("switch_stand", () -> new SwitchBlock(BlockBehaviour.Properties.of()
            .strength(1.5F).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK)));

    public static final DeferredBlock<WagonLoaderBlock> WAGON_LOADER = BLOCKS.register("wagon_loader", () -> new WagonLoaderBlock(BlockBehaviour.Properties.of()
            .strength(2.5F).sound(SoundType.METAL).requiresCorrectToolForDrops()
            .lightLevel(state -> state.getValue(WagonLoaderBlock.ACTIVE) ? 4 : 0)));

    public static final DeferredBlock<ChargingPointBlock> CHARGING_POINT = BLOCKS.register("charging_point", () -> new ChargingPointBlock(
            BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()
                    .lightLevel(state -> state.getValue(ChargingPointBlock.CHARGING) ? 6 : state.getValue(ChargingPointBlock.LEVEL) > 0 ? 2 : 0)));

    public static final DeferredBlock<SpeedSignBlock> SPEED_SIGN = BLOCKS.register("speed_sign", () -> new SpeedSignBlock(
            BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK)));

    public static final DeferredBlock<TrackSensorBlock> TRACK_SENSOR = BLOCKS.register("track_sensor", () -> new TrackSensorBlock(
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK)
                    .lightLevel(state -> state.getValue(TrackSensorBlock.POWERED) ? 4 : 0)));

    public static final DeferredBlock<LevelCrossingBlock> LEVEL_CROSSING = BLOCKS.register("level_crossing", () -> new LevelCrossingBlock(
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK)));
    /** The unseen barrier under a lowered level crossing arm. */
    public static final DeferredBlock<CrossingArmBlock> CROSSING_ARM = BLOCKS.register("level_crossing_arm", () -> new CrossingArmBlock(
            BlockBehaviour.Properties.of().strength(-1.0F, 3600000.0F).noLootTable().noOcclusion().randomTicks()
                    .pushReaction(PushReaction.BLOCK).isValidSpawn((state, level, pos, type) -> false)));

    private ModBlocks() {
    }
}
