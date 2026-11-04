package com.reiko.trains.shed;

import com.reiko.trains.registry.ModBlockEntities;
import com.reiko.trains.registry.ModItems;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Engine shed doors: a timber frame five blocks wide and five high standing over a straight track, the doorway
 * three wide and four high, and two big doors in it. Each block of it knows its place in the frame ({@link #CELL}:
 * across, from -2 to 2, plus five times up, from 0 to 4) and which way the doors face ({@link #FACING}: out of the
 * shed), so it can find the middle of the lintel, which keeps the workings ({@link EngineShedBlockEntity}). The
 * doorway's blocks are unseen, and stop people walking through while the doors are shut ({@link #OPEN}). The track
 * block under the middle of the doorway is just track.
 */
public class EngineShedBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0, 24);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** The cell the workings are in: the middle of the lintel. */
    public static final int CONTROLLER = cell(0, 4);
    /** Blocks being taken up, so taking each away doesn't start it again; and those broken in creative mode. */
    private static final Set<BlockPos> DISMANTLING = ConcurrentHashMap.newKeySet();
    private static final Set<BlockPos> CREATIVE_BREAKS = ConcurrentHashMap.newKeySet();
    private static final VoxelShape[] SHUT = new VoxelShape[4];

    static {
        // A door's thickness, just inside the frame's outer face.
        SHUT[Direction.NORTH.get2DDataValue()] = Block.box(0, 0, 1, 16, 16, 3);
        SHUT[Direction.SOUTH.get2DDataValue()] = Block.box(0, 0, 13, 16, 16, 15);
        SHUT[Direction.WEST.get2DDataValue()] = Block.box(1, 0, 0, 3, 16, 16);
        SHUT[Direction.EAST.get2DDataValue()] = Block.box(13, 0, 0, 15, 16, 16);
    }

    public EngineShedBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CELL, CONTROLLER).setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CELL, OPEN);
    }

    public static int cell(int across, int up) {
        return across + 2 + 5 * up;
    }

    public static int across(BlockState state) {
        return state.getValue(CELL) % 5 - 2;
    }

    public static int up(BlockState state) {
        return state.getValue(CELL) / 5;
    }

    /** Whether a cell is part of the frame (the posts either side and the lintel over the top), not the doorway. */
    public static boolean isFrame(int across, int up) {
        return Math.abs(across) == 2 || up == 4;
    }

    /** The track block under the middle of the doorway. */
    public static BlockPos track(BlockState state, BlockPos pos) {
        Direction right = state.getValue(FACING).getClockWise();
        int a = across(state);
        return pos.offset(-right.getStepX() * a, -up(state), -right.getStepZ() * a);
    }

    /** Where a cell of a frame over {@code track}, facing {@code facing}, is. */
    public static BlockPos at(BlockPos track, Direction facing, int across, int up) {
        Direction right = facing.getClockWise();
        return track.offset(right.getStepX() * across, up, right.getStepZ() * across);
    }

    @Nullable
    public static EngineShedBlockEntity shed(BlockGetter level, BlockState state, BlockPos pos) {
        BlockPos controller = track(state, pos).above(4);
        return level.getBlockEntity(controller) instanceof EngineShedBlockEntity shed ? shed : null;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return isFrame(across(state), up(state)) ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (isFrame(across(state), up(state))) {
            return Shapes.block();
        }
        return state.getValue(OPEN) ? Shapes.empty() : SHUT[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return !isFrame(across(state), up(state));
    }

    /** Fuel goes into its store, as does water or diesel from a bucket (or any fluid container); see {@link EngineShedBlockEntity#offer}. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        EngineShedBlockEntity shed = shed(level, state, pos);
        if (shed == null || stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return shed.couldTake(stack) ? ItemInteractionResult.SUCCESS : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return shed.offer(player, hand, stack) ? ItemInteractionResult.CONSUME : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Clicking it opens the doors, or shuts them (they open by themselves for trains), and says what's in its store. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        EngineShedBlockEntity shed = shed(level, state, pos);
        if (shed != null) {
            shed.toggle(player);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide && isFrame(across(state), up(state))) {
            EngineShedBlockEntity shed = shed(level, state, pos);
            if (shed != null) {
                shed.redstone(pos, level.hasNeighborSignal(pos));
            }
        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.ENGINE_SHED.get());
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (player.getAbilities().instabuild) {
            CREATIVE_BREAKS.add(track(state, pos).immutable());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Taking any of it away takes all of it down, gives back the doors (once) and tips out the fuel in its store. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            BlockPos track = track(state, pos).immutable();
            if (DISMANTLING.add(track)) {
                try {
                    boolean creative = CREATIVE_BREAKS.remove(track);
                    Direction facing = state.getValue(FACING);
                    BlockPos controller = track.above(4);
                    if (server.getBlockEntity(controller) instanceof EngineShedBlockEntity shed) {
                        shed.spill();
                    }
                    for (int up = 0; up <= 4; up++) {
                        for (int across = -2; across <= 2; across++) {
                            BlockPos at = at(track, facing, across, up);
                            BlockState there = server.getBlockState(at);
                            if (!at.equals(pos) && there.is(this) && there.getValue(FACING) == facing && there.getValue(CELL) == cell(across, up)) {
                                server.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
                            }
                        }
                    }
                    if (!creative && server.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
                        Containers.dropItemStack(server, track.getX() + 0.5, track.getY() + 1, track.getZ() + 0.5, new ItemStack(ModItems.ENGINE_SHED.get()));
                    }
                } finally {
                    DISMANTLING.remove(track);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(CELL) == CONTROLLER ? new EngineShedBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.ENGINE_SHED.get() || state.getValue(CELL) != CONTROLLER) {
            return null;
        }
        if (level.isClientSide) {
            return (l, p, s, be) -> ((EngineShedBlockEntity) be).clientTick();
        }
        return (l, p, s, be) -> EngineShedBlockEntity.serverTick(l, p, s, (EngineShedBlockEntity) be);
    }
}
