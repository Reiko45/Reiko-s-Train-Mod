package com.reiko.trains.signal;

import com.reiko.trains.freight.WagonLoaderBlock;
import com.reiko.trains.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A track sensor, beside the track (up to three blocks away; it ties itself to the nearest track block and its eye
 * faces it). It gives a redstone signal while a train is over that track block, moving or standing, and a comparator
 * beside it reads how many cars the train has. Click it to choose which way trains must be going (either way, or only
 * left or right as you stand behind it), the arrow on top showing which; click it with an item to sense only some
 * trains (see {@link TrackSensorBlockEntity}), and sneak-click it to sense them all again.
 */
public class TrackSensorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final com.mojang.serialization.MapCodec<TrackSensorBlock> CODEC = simpleCodec(TrackSensorBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final EnumProperty<SensorDirection> DIRECTION = EnumProperty.create("direction", SensorDirection.class);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 12, 13);

    public TrackSensorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false)
                .setValue(DIRECTION, SensorDirection.BOTH));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, DIRECTION);
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
        return defaultBlockState().setValue(FACING, WagonLoaderBlock.facing(context.getClickedPos(), track, player));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TrackSensorBlockEntity sensor) {
            BlockPos track = TrackSide.nearestTrack(level, pos);
            if (track != null) {
                sensor.attach(track);
            }
        }
    }

    /** Clicking it with an item makes it sense only trains that item picks out. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof TrackSensorBlockEntity sensor)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            sensor.setFilter(stack);
            player.displayClientMessage(sensor.describeFilter(), true);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6F, 1.2F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Clicking it empty-handed changes which way trains must be going; sneak-clicking clears its filter. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TrackSensorBlockEntity sensor)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            sensor.setFilter(ItemStack.EMPTY);
            player.displayClientMessage(sensor.describeFilter(), true);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6F, 1.2F);
            return InteractionResult.CONSUME;
        }
        SensorDirection direction = state.getValue(DIRECTION).next();
        level.setBlock(pos, state.setValue(DIRECTION, direction), 3);
        player.displayClientMessage(Component.translatable("message.reikotrains.sensor.direction." + direction.getSerializedName()), true);
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4F, 1.2F);
        return InteractionResult.CONSUME;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof TrackSensorBlockEntity sensor ? sensor.comparatorSignal() : 0;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrackSensorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.TRACK_SENSOR.get()) {
            return null;
        }
        return (l, p, s, be) -> TrackSensorBlockEntity.serverTick(l, p, s, (TrackSensorBlockEntity) be);
    }
}
