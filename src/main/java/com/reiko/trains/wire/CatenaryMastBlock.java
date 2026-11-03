package com.reiko.trains.wire;

import com.reiko.trains.registry.ModItems;
import com.reiko.trains.signal.TrackSide;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An overhead line mast: a steel pole beside the track (up to three blocks from it, curves included), its arm reaching
 * out over the track to hold the contact wire there, four and a half blocks above the rails. String Overhead Wire from
 * mast to mast to make a line. Breaking it takes down its spans (the wire drops).
 */
public class CatenaryMastBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final com.mojang.serialization.MapCodec<CatenaryMastBlock> CODEC = simpleCodec(CatenaryMastBlock::new);
    /** How far from the track it can stand, in blocks. */
    public static final double REACH = 3.5;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 4, 13), Block.box(6, 4, 6, 10, 32, 10));

    public CatenaryMastBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Where a mast at {@code pos} would hold the wire: over the nearest track, or null if there's none near enough. */
    @Nullable
    public static Vec3 wireFor(Level level, BlockPos pos) {
        Vec3 track = TrackSide.nearestTrackPoint(level, pos, REACH);
        return track == null ? null : track.add(0, OverheadLines.WIRE_HEIGHT, 0);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Vec3 wire = wireFor(level, pos);
        if (wire == null) {
            Player player = context.getPlayer();
            if (player != null && level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.reikotrains.mast.no_track").withStyle(ChatFormatting.RED), true);
            }
            return null;
        }
        // Its arm points at the track.
        Vec3 toTrack = wire.subtract(Vec3.atCenterOf(pos));
        return defaultBlockState().setValue(FACING, TrackSide.nearestDirection(new Vec3(toTrack.x, 0, toTrack.z)));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        Vec3 wire = wireFor(level, pos);
        if (level instanceof ServerLevel server && wire != null && level.getBlockEntity(pos) instanceof CatenaryMastBlockEntity mast) {
            mast.place(server, wire);
        }
    }

    /** Broken: its spans come down, and their wire drops here; the masts at their other ends let go of them. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            List<OverheadLines.Span> gone = OverheadLines.get(server).removeMast(pos);
            if (!gone.isEmpty()) {
                Block.popResource(level, pos, new ItemStack(ModItems.OVERHEAD_WIRE.get(), gone.size()));
            }
            for (OverheadLines.Span span : gone) {
                CatenaryMastBlockEntity.refresh(level, span.other(pos));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CatenaryMastBlockEntity(pos, state);
    }
}
