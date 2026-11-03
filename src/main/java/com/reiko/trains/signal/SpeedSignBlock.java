package com.reiko.trains.signal;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A speed limit sign, beside the track (up to three blocks away), put up like a signal: looking along the line the way
 * the trains will go, its face towards them. Trains going that way slow down in time to pass it at its limit, and
 * keep to it until the next speed sign their way; an end-of-limit sign lifts it. Click it to change its limit: 10, 20,
 * 30, 40 or 50 km/h, or the end of a limit.
 */
public class SpeedSignBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final com.mojang.serialization.MapCodec<SpeedSignBlock> CODEC = simpleCodec(SpeedSignBlock::new);
    /** The limits it can show, in km/h; 0 is the end of a limit. */
    public static final int[] LIMITS = {10, 20, 30, 40, 50, 0};
    /** Which of {@link #LIMITS} it shows. */
    public static final IntegerProperty LIMIT = IntegerProperty.create("limit", 0, LIMITS.length - 1);
    /** A new sign's limit: 20 km/h. */
    public static final int DEFAULT = 1;
    private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 26, 11);

    public SpeedSignBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIMIT, DEFAULT));
    }

    /** The limit a sign in this state shows, in km/h (0 for the end of a limit). */
    public static int kmh(BlockState state) {
        return LIMITS[state.getValue(LIMIT)];
    }

    /** Km/h in blocks per tick: 20 ticks a second, and a block is a metre. */
    public static double toBlocksPerTick(double kmh) {
        return kmh / 72.0;
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIMIT);
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
        Vec3 look = player != null ? player.getLookAngle() : new Vec3(0, 0, 1);
        // Its face towards the trains coming along: back against the way they go.
        return defaultBlockState().setValue(FACING, TrackSide.nearestDirection(look.reverse()));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SpeedSignBlockEntity sign) {
            BlockPos track = TrackSide.nearestTrack(level, pos);
            if (track != null) {
                Vec3 look = placer != null ? placer.getLookAngle() : Vec3.atLowerCornerOf(state.getValue(FACING).getOpposite().getNormal());
                sign.attach(track, TrackSide.sideToward(level, track, look));
            }
        }
    }

    /** Clicking it goes on to the next limit, and round. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockState next = state.setValue(LIMIT, (state.getValue(LIMIT) + 1) % LIMITS.length);
        level.setBlock(pos, next, 3);
        int kmh = kmh(next);
        player.displayClientMessage(kmh > 0 ? Component.translatable("message.reikotrains.speed_sign.limit", kmh)
                : Component.translatable("message.reikotrains.speed_sign.end"), true);
        level.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.6F, 1.2F);
        return InteractionResult.CONSUME;
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
        return new SpeedSignBlockEntity(pos, state);
    }
}
