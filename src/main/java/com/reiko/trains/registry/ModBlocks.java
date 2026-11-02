package com.reiko.trains.registry;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.signal.SignalBlock;
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

    private ModBlocks() {
    }
}
