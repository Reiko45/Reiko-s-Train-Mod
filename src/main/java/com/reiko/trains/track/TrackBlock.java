package com.reiko.trains.track;

import com.reiko.trains.registry.ModItems;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Train track: one block of it, running east-west, north-south or diagonally. Blocks in a line join up by
 * themselves; curves, ramps and long straights are drawn between blocks with the track item.
 */
public class TrackBlock extends Block implements EntityBlock {
    public static final EnumProperty<TrackAxis> AXIS = EnumProperty.create("axis", TrackAxis.class);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 4, 16);

    public TrackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, TrackAxis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        TrackAxis axis = TrackAxis.nearest(TrackAxis.look(context.getRotation()));
        BlockState state = defaultBlockState().setValue(AXIS, axis);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canSupportRigidBlock(level, pos.below());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        TrackAxis axis = state.getValue(AXIS);
        boolean quarter = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
        if (!quarter) {
            return state;
        }
        return state.setValue(AXIS, switch (axis) {
            case X -> TrackAxis.Z;
            case Z -> TrackAxis.X;
            case SE -> TrackAxis.NE;
            case NE -> TrackAxis.SE;
        });
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        TrackAxis axis = state.getValue(AXIS);
        if (mirror == Mirror.NONE || axis == TrackAxis.X || axis == TrackAxis.Z) {
            return state;
        }
        return state.setValue(AXIS, axis == TrackAxis.SE ? TrackAxis.NE : TrackAxis.SE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrackBlockEntity(pos, state);
    }

    private static void drop(Level level, BlockPos pos, net.minecraft.world.item.Item item, int amount) {
        while (amount > 0) {
            int count = Math.min(64, amount);
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, new ItemStack(item, count));
            amount -= count;
        }
    }

    /** Track blocks being broken by players in creative mode, whose curves shouldn't drop anything. */
    private static final java.util.Set<BlockPos> CREATIVE_BREAKS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.player.Player player) {
        if (player.getAbilities().instabuild) {
            CREATIVE_BREAKS.add(pos.immutable());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Taking a track block away takes its curves with it: they come off the blocks at their other ends too, and the
     * track they were made of drops here.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        boolean creative = !state.is(newState.getBlock()) && CREATIVE_BREAKS.remove(pos);
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof TrackBlockEntity track) {
            TrackAxis axis = state.getValue(AXIS);
            boolean street = this instanceof StreetTrackBlock;
            int refund = 0;
            int streetRefund = 0;
            for (TrackConnection connection : List.copyOf(track.connections())) {
                TrackAxis otherAxis = TrackGraph.axis(level, connection.other());
                if (otherAxis != null) {
                    boolean otherStreet = TrackGraph.street(level, connection.other());
                    int cost = TrackBuilder.cost(new EdgeRef(pos, axis, connection.mySide(), connection.other(), otherAxis,
                            connection.otherSide(), street, otherStreet).geometry());
                    // Street track between street track blocks was laid with street track, and gives it back.
                    if (street && otherStreet) {
                        streetRefund += cost;
                    } else {
                        refund += cost;
                    }
                }
                if (level.getBlockEntity(connection.other()) instanceof TrackBlockEntity other) {
                    other.remove(new TrackConnection(pos, connection.otherSide(), connection.mySide()));
                }
            }
            if (!creative && serverLevel.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS)) {
                drop(level, pos, ModItems.TRACK.get(), refund);
                drop(level, pos, ModItems.STREET_TRACK.get(), streetRefund);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
