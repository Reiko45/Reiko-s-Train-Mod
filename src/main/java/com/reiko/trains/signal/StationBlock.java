package com.reiko.trains.signal;

import com.reiko.trains.network.OpenStationPayload;
import com.reiko.trains.registry.ModBlockEntities;
import com.reiko.trains.track.TrackEdge;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A station nameboard. Placed beside the track (up to three blocks away), it marks where trains stop: their front
 * draws up level with it. Click it to name it and set how long timetabled trains wait. It gives a redstone signal
 * while a train stands at it. Name it in an anvil first to start with that name.
 */
public class StationBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final com.mojang.serialization.MapCodec<StationBlock> CODEC = simpleCodec(StationBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape SHAPE_NS = Shapes.or(Block.box(1, 0, 7, 3, 32, 9), Block.box(13, 0, 7, 15, 32, 9), Block.box(0, 19, 7, 16, 28, 9));
    private static final VoxelShape SHAPE_EW = Shapes.or(Block.box(7, 0, 1, 9, 32, 3), Block.box(7, 0, 13, 9, 32, 15), Block.box(7, 19, 0, 9, 28, 16));

    public StationBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos track = TrackSide.nearestTrack(level, context.getClickedPos());
        Player player = context.getPlayer();
        if (track == null) {
            if (player != null && level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.reikotrains.trackside.no_track").withStyle(ChatFormatting.RED), true);
            }
            return null;
        }
        return defaultBlockState().setValue(FACING, facingTrack(context.getClickedPos(), track, player));
    }

    /** The board faces the track (its front towards it), so it's read from the train. */
    private static Direction facingTrack(BlockPos pos, BlockPos track, @Nullable Player player) {
        Vec3 toTrack = TrackEdge.node(track).subtract(Vec3.atCenterOf(pos));
        if (toTrack.horizontalDistanceSqr() < 0.01) {
            return player != null ? player.getDirection().getOpposite() : Direction.NORTH;
        }
        return TrackSide.nearestDirection(toTrack);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof StationBlockEntity station) {
            BlockPos track = TrackSide.nearestTrack(level, pos);
            if (track != null) {
                station.attach(track);
            }
            if (stack.has(DataComponents.CUSTOM_NAME)) {
                station.setName(stack.getHoverName().getString());
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof StationBlockEntity station) {
            if (player instanceof ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayer(serverPlayer, new OpenStationPayload(pos, station.name(), station.dwellSeconds()));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StationBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != ModBlockEntities.STATION.get() ? null
                : (l, p, s, be) -> StationBlockEntity.serverTick(l, p, s, (StationBlockEntity) be);
    }
}
