package com.reiko.trains.signal;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A four-aspect signal: red, yellow, double yellow and green. Like a three-light signal it shows yellow while the next
 * signal is red; and it shows double yellow (both its yellow lamps) while the next signal is yellow, so a fast train
 * gets two sections' warning: it passes a double yellow at no more than {@code doubleYellowSpeedKmh}, then the yellow at
 * caution speed, ready to stop at the red. A comparator reads 15 (a train in its section), 7 (yellow), 3 (double
 * yellow) or 0 (green).
 */
public class FourAspectSignalBlock extends ThreeLightSignalBlock {
    public static final com.mojang.serialization.MapCodec<FourAspectSignalBlock> CODEC = simpleCodec(FourAspectSignalBlock::new);
    /** Double yellow: clear, and the next signal is yellow. Only shown while clear and not {@link #CAUTION}. */
    public static final BooleanProperty ADVANCE = BooleanProperty.create("advance");

    public FourAspectSignalBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ADVANCE, false));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ADVANCE);
    }
}
