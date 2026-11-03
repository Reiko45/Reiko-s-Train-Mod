package com.reiko.trains.track;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Street track: a block of road with grooved rails set into it, level with its top, so cars, people and horses go
 * over it like any road (it's a whole, solid block). It's track like any other: blocks in a line join up, curves
 * and ramps are drawn between them, and trains (trams especially) run along it, their wheels at the top of the block.
 * Curves between street track blocks can be tighter than ordinary track's, for street corners.
 */
public class StreetTrackBlock extends TrackBlock {
    public StreetTrackBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    /** It's the road itself: it stands on whatever the road's built on, as long as there's something under it. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return !level.getBlockState(pos.below()).isAir();
    }
}
