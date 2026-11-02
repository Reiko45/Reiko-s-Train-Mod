package com.reiko.trains.signal;

import com.reiko.trains.track.TrackEdge;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A switch stand, beside a junction (up to three blocks away). It decides which way trains go there: normally the
 * straightest way, thrown the way that branches off. Click it to throw it over or back, or work it with redstone
 * like a door (powered: thrown). While it's powered, it's locked: timetabled trains, which otherwise set switches
 * their way as they come up to them, have to go the way it's set. Sneak-click picks which way a thrown switch goes
 * at a three-way junction.
 */
public class SwitchBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final com.mojang.serialization.MapCodec<SwitchBlock> CODEC = simpleCodec(SwitchBlock::new);
    public static final BooleanProperty THROWN = BooleanProperty.create("thrown");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 19, 12);

    public SwitchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(THROWN, false).setValue(POWERED, false));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, THROWN, POWERED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        TrackSide.Junction junction = TrackSide.nearestJunction(level, context.getClickedPos());
        Player player = context.getPlayer();
        if (junction == null) {
            if (player != null && level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.reikotrains.switch.no_junction").withStyle(ChatFormatting.RED), true);
            }
            return null;
        }
        boolean powered = level.hasNeighborSignal(context.getClickedPos());
        return defaultBlockState().setValue(FACING, facing(junction, context.getClickedPos())).setValue(POWERED, powered).setValue(THROWN, powered);
    }

    /** The stand's lever moves along the track; it faces the junction it works. */
    public static Direction facing(TrackSide.Junction junction, BlockPos pos) {
        Vec3 toTrack = TrackEdge.node(junction.node()).subtract(Vec3.atCenterOf(pos));
        return toTrack.horizontalDistanceSqr() < 0.01 ? Direction.NORTH : TrackSide.nearestDirection(toTrack);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SwitchBlockEntity stand) {
            TrackSide.Junction junction = TrackSide.nearestJunction(level, pos);
            if (junction != null) {
                stand.attach(junction.node(), junction.side());
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SwitchBlockEntity stand)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            stand.flipThrownSide();
            player.displayClientMessage(Component.translatable(stand.thrownSide() < 0 ? "message.reikotrains.switch.thrown_left"
                    : "message.reikotrains.switch.thrown_right"), true);
            return InteractionResult.CONSUME;
        }
        if (state.getValue(POWERED)) {
            player.displayClientMessage(Component.translatable("message.reikotrains.switch.locked").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        boolean thrown = !state.getValue(THROWN);
        level.setBlock(pos, state.setValue(THROWN, thrown), 3);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, thrown ? 0.6F : 0.5F);
        level.playSound(null, stand.node(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.4F, 1.4F);
        return InteractionResult.CONSUME;
    }

    /** Redstone works it like a door: powered, it's thrown; unpowered, it goes back. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered).setValue(THROWN, powered), 3);
            if (state.getValue(THROWN) != powered) {
                level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, powered ? 0.6F : 0.5F);
            }
        }
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
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
        return new SwitchBlockEntity(pos, state);
    }
}
