package com.reiko.trains.registry;

import com.reiko.trains.ReikoTrains;
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

    private ModBlocks() {
    }
}
