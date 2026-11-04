package com.reiko.trains.freight;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Car Ramp: a steel loading ramp, rising towards the track ({@link #FACING}: the way it rises). A row of them along
 * the track makes a loading dock for car transporters: a car standing on one, lined up with a standing car
 * transporter beside it, drives aboard sideways when its driver keeps going, to the nearest place on the deck with
 * room; and a car aboard beside one drives off onto it (see {@link com.reiko.trains.train.CarCargo}).
 */
public class CarRampBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<CarRampBlock> CODEC = simpleCodec(CarRampBlock::new);
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (int step = 0; step < 4; step++) {
                // Steps rising towards the front: the back quarter 4 high, up to the front quarter a whole block.
                double from = 12 - step * 4;
                double height = 4 + step * 4;
                VoxelShape slice = switch (facing) {
                    case NORTH -> Block.box(0, 0, 12 - from, 16, height, 16 - from);
                    case SOUTH -> Block.box(0, 0, from, 16, height, from + 4);
                    case WEST -> Block.box(12 - from, 0, 0, 16 - from, height, 16);
                    default -> Block.box(from, 0, 0, from + 4, height, 16);
                };
                shape = Shapes.or(shape, slice);
            }
            SHAPES[facing.get2DDataValue()] = shape.optimize();
        }
    }

    public CarRampBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** It rises the way the player looks: towards the track, from beside it. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }
}
