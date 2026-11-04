package com.reiko.trains.signal;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A three-light signal: red, yellow and green. It guards its section like any signal (red while there's a train in
 * it, or it's held by redstone), and shows yellow while it's clear but the next signal along the way the switches are
 * set is red, so trains come past it at caution speed, ready to stop there. A comparator next to it reads 15 while
 * there's a train in its section and 7 while it shows yellow.
 */
public class ThreeLightSignalBlock extends SignalBlock {
    public static final com.mojang.serialization.MapCodec<ThreeLightSignalBlock> CODEC = simpleCodec(ThreeLightSignalBlock::new);
    /** Yellow: clear, but the next signal is red. Only shown while {@link #CLEAR}. */
    public static final BooleanProperty CAUTION = BooleanProperty.create("caution");

    public ThreeLightSignalBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CAUTION, false));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CAUTION);
    }
}
