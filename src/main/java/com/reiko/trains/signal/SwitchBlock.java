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
 * straightest way, thrown the way that branches off. Click it to throw it over or back (at a three-way junction, to
 * go round its three ways). Redstone sets it and holds it there, and lets it go back to normal when the power goes
 * off; sneak-click picks which way redstone sets it: thrown (to which branch, at a three-way junction) or straight
 * on. While it's powered it's held: timetabled trains, which otherwise set switches their way as they come up to
 * them, have to go the way it's set.
 */
public class SwitchBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final com.mojang.serialization.MapCodec<SwitchBlock> CODEC = simpleCodec(SwitchBlock::new);
    public static final BooleanProperty THROWN = BooleanProperty.create("thrown");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** The marker on the post: what redstone sets the switch to. True: it holds it straight on; false: it throws it. */
    public static final BooleanProperty REDSTONE_STRAIGHT = BooleanProperty.create("redstone_straight");
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 19, 12);

    public SwitchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(THROWN, false).setValue(POWERED, false)
                .setValue(REDSTONE_STRAIGHT, false));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, THROWN, POWERED, REDSTONE_STRAIGHT);
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
        return defaultBlockState().setValue(FACING, facing(junction, context.getClickedPos())).setValue(POWERED, powered);
    }

    /** The stand's lever moves along the track; it faces the junction it works. */
    public static Direction facing(TrackSide.Junction junction, BlockPos pos) {
        Vec3 toTrack = TrackEdge.node(junction.node(), false).subtract(Vec3.atCenterOf(pos));
        return toTrack.horizontalDistanceSqr() < 0.01 ? Direction.NORTH : TrackSide.nearestDirection(toTrack);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SwitchBlockEntity stand) {
            TrackSide.Junction junction = TrackSide.nearestJunction(level, pos);
            if (junction != null) {
                stand.attach(junction.node(), junction.side());
                stand.powered(state.getValue(POWERED));
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
        boolean threeWay = stand.branchSides().size() > 1;
        if (player.isSecondaryUseActive()) {
            int way = stand.cycleRedstoneWay();
            String key = way == 0 ? "straight" : !threeWay ? "thrown" : way < 0 ? "left" : "right";
            player.displayClientMessage(Component.translatable("message.reikotrains.switch.redstone." + key), true);
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4F, 1.2F);
            return InteractionResult.CONSUME;
        }
        if (state.getValue(POWERED)) {
            player.displayClientMessage(Component.translatable("message.reikotrains.switch.locked").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        if (stand.isHeld()) {
            player.displayClientMessage(Component.translatable("message.reikotrains.switch.route").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        int way = stand.next(stand.setting());
        stand.set(way);
        if (threeWay) {
            player.displayClientMessage(Component.translatable("message.reikotrains.switch.set." + (way == 0 ? "straight" : way < 0 ? "left" : "right")), true);
        }
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, way != 0 ? 0.6F : 0.5F);
        level.playSound(null, stand.node(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.4F, 1.4F);
        return InteractionResult.CONSUME;
    }

    /** Redstone sets it its redstone way and holds it there; when the power goes off, it goes back to normal. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED) && level.getBlockEntity(pos) instanceof SwitchBlockEntity stand) {
            int was = stand.setting();
            stand.powered(powered);
            if (stand.setting() != was) {
                level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, stand.isThrown() ? 0.6F : 0.5F);
            }
        }
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
    }

    /** Brings the marker into line with the stand's redstone setting (scheduled when an older stand loads). */
    @Override
    protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (level.getBlockEntity(pos) instanceof SwitchBlockEntity stand) {
            stand.showRedstoneWay();
        }
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
