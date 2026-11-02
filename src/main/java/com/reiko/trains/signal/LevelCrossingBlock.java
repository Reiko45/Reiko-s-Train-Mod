package com.reiko.trains.signal;

import com.reiko.trains.registry.ModBlockEntities;
import com.reiko.trains.registry.ModBlocks;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A level crossing barrier: a post beside the road, up to three blocks from the track, with two red lamps and a
 * barrier arm. When a train's coming the lamps flash and a bell rings, then the arm comes down across the road and
 * stays down till the train's gone by; while it's down, nothing can drive through it (the car mod's cars climb a
 * whole block, so it stands taller than that). The arm swings down towards whoever put the post there, so put it
 * there from the road; click the post to change how long the arm is. It gives off a redstone signal while it's
 * warning.
 */
public class LevelCrossingBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final com.mojang.serialization.MapCodec<LevelCrossingBlock> CODEC = simpleCodec(LevelCrossingBlock::new);
    public static final int MAX_LENGTH = 8;
    public static final int DEFAULT_LENGTH = 4;
    /** The lamps are flashing (and the bell's ringing): a train's coming. */
    public static final BooleanProperty WARNING = BooleanProperty.create("warning");
    /** The arm's down, or coming down. */
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    /** How far the arm reaches across the road, in blocks. */
    public static final IntegerProperty LENGTH = IntegerProperty.create("length", 1, MAX_LENGTH);
    private static final VoxelShape POST = Block.box(4, 0, 4, 12, 21, 12);
    /** The lamp board on top runs the way the arm points. */
    private static final VoxelShape BOARD_Z = Block.box(5, 21, 1, 11, 28, 15);
    private static final VoxelShape BOARD_X = Block.box(1, 21, 5, 15, 28, 11);
    private static final VoxelShape SHAPE_Z = Shapes.or(POST, BOARD_Z);
    private static final VoxelShape SHAPE_X = Shapes.or(POST, BOARD_X);
    private static final VoxelShape COLLISION = Block.box(5, 0, 5, 11, 24, 11);

    public LevelCrossingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WARNING, false).setValue(DOWN, false)
                .setValue(LENGTH, DEFAULT_LENGTH));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WARNING, DOWN, LENGTH);
    }

    /** The arm comes down towards the player putting it there: across the road, if they're standing in it. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        if (TrackSide.nearestTrack(level, context.getClickedPos()) == null) {
            Player player = context.getPlayer();
            if (player != null && level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.reikotrains.crossing.no_track").withStyle(ChatFormatting.RED), true);
            }
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof LevelCrossingBlockEntity crossing) {
            BlockPos node = TrackSide.nearestTrack(level, pos);
            if (node != null) {
                crossing.attach(node);
            }
        }
    }

    /** Clicking it makes the arm a block longer, round from {@value #MAX_LENGTH} back to one. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof LevelCrossingBlockEntity crossing)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        int length = state.getValue(LENGTH) % MAX_LENGTH + 1;
        crossing.setLength(length);
        player.displayClientMessage(Component.translatable("message.reikotrains.crossing.length", length), true);
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4F, 1.2F);
        return InteractionResult.CONSUME;
    }

    /** Where the arm's hidden barrier goes when it's down: the blocks it reaches over, out from the post. */
    public static BlockPos armBlock(BlockPos post, Direction facing, int i) {
        return post.relative(facing, i);
    }

    /** Takes the arm's barrier out of the road. */
    public static void clearArm(Level level, BlockPos pos, Direction facing, int length) {
        for (int i = 1; i <= length; i++) {
            BlockPos at = armBlock(pos, facing, i);
            if (level.isLoaded(at) && level.getBlockState(at).is(ModBlocks.CROSSING_ARM.get())) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    /** Whether a level crossing's arm is down over this block, so a barrier belongs here. */
    public static boolean armDownOver(Level level, BlockPos pos) {
        for (Direction toPost : Direction.Plane.HORIZONTAL) {
            for (int i = 1; i <= MAX_LENGTH; i++) {
                BlockPos at = pos.relative(toPost, i);
                if (!level.isLoaded(at)) {
                    break;
                }
                BlockState state = level.getBlockState(at);
                if (state.getBlock() instanceof LevelCrossingBlock && state.getValue(FACING) == toPost.getOpposite()
                        && state.getValue(DOWN) && state.getValue(LENGTH) >= i) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!newState.is(this) && !level.isClientSide) {
            clearArm(level, pos, state.getValue(FACING), state.getValue(LENGTH));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(WARNING) ? 15 : 0;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_Z : SHAPE_X;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION;
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
        return new LevelCrossingBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.LEVEL_CROSSING.get()) {
            return null;
        }
        return level.isClientSide
                ? (BlockEntityTicker<T>) (BlockEntityTicker<LevelCrossingBlockEntity>) LevelCrossingBlockEntity::clientTick
                : (BlockEntityTicker<T>) (BlockEntityTicker<LevelCrossingBlockEntity>) LevelCrossingBlockEntity::serverTick;
    }
}
