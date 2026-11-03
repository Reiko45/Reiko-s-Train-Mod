package com.reiko.trains.water;

import com.reiko.trains.freight.WagonLoaderBlock;
import com.reiko.trains.registry.ModBlockEntities;
import com.reiko.trains.signal.TrackSide;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * A water tower, for steam engines: a wooden tank on legs beside the track, two blocks tall (up to three blocks away;
 * it ties itself to the nearest track block and its spout faces it). Its workings are in its bottom block. Fill it with buckets of water, or pipes and pumps; it fills the tank
 * engines and tenders of a train standing beside it, swinging its spout down over the track while it does. The gauge on
 * its front shows how full it is. Click it to see how much it holds.
 */
public class WaterTowerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final com.mojang.serialization.MapCodec<WaterTowerBlock> CODEC = simpleCodec(WaterTowerBlock::new);
    /** How many of the marks on its gauge are lit: how full its tank is. */
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 4);
    /** Its spout's down: it's filling a train. */
    public static final BooleanProperty FILLING = BooleanProperty.create("filling");
    /** Which of its two blocks this is: the legs, or the tank. */
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final VoxelShape LEGS = Block.box(1, 0, 1, 15, 16, 15);
    private static final VoxelShape TANK = Block.box(0.5, 0, 0.5, 15.5, 16, 15.5);

    public WaterTowerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LEVEL, 0).setValue(FILLING, false)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    /** Its bottom block, where its workings are. */
    public static BlockPos base(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEVEL, FILLING, HALF);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos above = context.getClickedPos().above();
        if (above.getY() >= level.getMaxBuildHeight() || !level.getBlockState(above).canBeReplaced(context)) {
            return null;
        }
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
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof WaterTowerBlockEntity tower) {
            BlockPos track = TrackSide.nearestTrack(level, pos);
            if (track != null) {
                tower.attach(track);
            }
        }
    }

    /** Either block goes without the other. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP)) {
            return neighbor.is(this) && neighbor.getValue(HALF) != half ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        return true;
    }

    /** Broken in creative from the top, the bottom goes too without dropping anything (as a door does). */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState base = level.getBlockState(below);
            if (base.is(this) && base.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, below, Block.getId(base));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Buckets (or other mods' fluid containers) of water fill it; an empty bucket draws a bucketful. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (level.getBlockEntity(base(state, pos)) instanceof WaterTowerBlockEntity tower && FluidUtil.getFluidHandler(stack).isPresent()) {
            if (FluidUtil.interactWithFluidHandler(player, hand, tower.tank())) {
                if (!level.isClientSide) {
                    say(player, tower);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Clicking it says how much water it holds. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(base(state, pos)) instanceof WaterTowerBlockEntity tower) {
            say(player, tower);
        }
        return InteractionResult.CONSUME;
    }

    private static void say(Player player, WaterTowerBlockEntity tower) {
        player.displayClientMessage(Component.translatable("message.reikotrains.water_tower.stored", tower.stored() / 1000,
                WaterTowerBlockEntity.CAPACITY / 1000), true);
    }

    /** While it's filling a train, water splashes from the end of its spout. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(FILLING) || state.getValue(HALF) != DoubleBlockHalf.UPPER) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * 1.42;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 1.42;
        double y = pos.getY() + 0.85;
        for (int i = 0; i < 3; i++) {
            level.addParticle(ParticleTypes.FALLING_WATER, x + random.nextGaussian() * 0.06, y, z + random.nextGaussian() * 0.06, 0, 0, 0);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SPLASH, x, y - 0.6, z, random.nextGaussian() * 0.1, 0.1, random.nextGaussian() * 0.1);
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(base(state, pos)) instanceof WaterTowerBlockEntity tower ? tower.comparatorSignal() : 0;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LEGS : TANK;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new WaterTowerBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.WATER_TOWER.get() || state.getValue(HALF) != DoubleBlockHalf.LOWER) {
            return null;
        }
        return (l, p, s, be) -> WaterTowerBlockEntity.serverTick(l, p, s, (WaterTowerBlockEntity) be);
    }
}
