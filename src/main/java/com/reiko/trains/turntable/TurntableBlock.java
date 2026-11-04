package com.reiko.trains.turntable;

import com.reiko.trains.registry.ModBlockEntities;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The floor of a turntable's pit: a circle of these (15 blocks across, or 11 for the small one), a block below the
 * track. Each knows where it is in the circle ({@link #X} and {@link #Z}, from 0 to 14, the middle being 7, whatever
 * the size), so it can find the middle one, the pivot, which keeps the turntable's workings
 * ({@link TurntableBlockEntity}). The bridge that turns on it is drawn by the pivot. The blocks round the edge have a
 * curved stone wall in them, so the pit's round. Clicking any of it turns the bridge to the next track (sneaking, the other way round); a redstone signal
 * beside the pit does the same; and a comparator beside it reads which way the bridge points. Breaking any of it
 * takes the whole turntable up (the pit stays dug).
 */
public class TurntableBlock extends Block implements EntityBlock {
    /** How far each way the biggest circle reaches from its middle, in blocks (and where its middle is in X and Z). */
    public static final int SPAN = 7;
    public static final IntegerProperty X = IntegerProperty.create("x", 0, 2 * SPAN);
    public static final IntegerProperty Z = IntegerProperty.create("z", 0, 2 * SPAN);
    private static final VoxelShape FLOOR = Block.box(0, 0, 0, 16, 2, 16);
    private static final VoxelShape PIVOT = Shapes.or(FLOOR, Block.box(4, 2, 4, 12, 15, 12));
    /** Turntables being taken up, so taking each block away doesn't start it again. */
    private static final Set<BlockPos> DISMANTLING = ConcurrentHashMap.newKeySet();
    /** Turntables being broken by players in creative mode, which shouldn't drop anything. */
    private static final Set<BlockPos> CREATIVE_BREAKS = ConcurrentHashMap.newKeySet();

    /** The pit's radius, from the middle of the pivot, in blocks. */
    private final double radius;
    /** What it is, taken up. */
    private final Supplier<? extends ItemLike> item;
    /** Each block's floor, and the wall round the edge in it. */
    private final java.util.Map<BlockState, VoxelShape> shapes = new ConcurrentHashMap<>();

    public TurntableBlock(Properties properties, double radius, Supplier<? extends ItemLike> item) {
        super(properties);
        this.radius = radius;
        this.item = item;
        registerDefaultState(stateDefinition.any().setValue(X, SPAN).setValue(Z, SPAN));
    }

    public double radius() {
        return radius;
    }

    /** How far each way the pit reaches from the pivot, in blocks. */
    public int span() {
        return Math.min(SPAN, (int) Math.ceil(radius + 0.5 - 0.01) - 1);
    }

    public ItemLike item() {
        return item.get();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(X, Z);
    }

    /**
     * Whether a block {@code dx}, {@code dz} from the pivot is dug for the pit: any of it is inside the circle. On the
     * eight lines the bridge can point along, only the blocks under it are: track runs up to it on the ground beyond.
     */
    public boolean inPit(int dx, int dz) {
        int ax = Math.abs(dx);
        int az = Math.abs(dz);
        if (ax == 0 || az == 0 || ax == az) {
            return underBridge(dx, dz);
        }
        double x = ax - 0.5;
        double z = az - 0.5;
        double r = radius - 0.01;
        return x * x + z * z < r * r;
    }

    /**
     * The curved wall round the edge of the pit in the block {@code dx}, {@code dz} from the pivot: the parts of it
     * outside the circle, in columns an eighth of a block across, as boxes in the block (pixels), merged into rows.
     */
    public java.util.List<int[]> wall(int dx, int dz) {
        java.util.List<int[]> boxes = new java.util.ArrayList<>();
        for (int j = 0; j < 8; j++) {
            int run = -1;
            for (int i = 0; i <= 8; i++) {
                boolean out = false;
                if (i < 8) {
                    double x = dx - 0.5 + (i + 0.5) / 8.0;
                    double z = dz - 0.5 + (j + 0.5) / 8.0;
                    out = x * x + z * z > radius * radius;
                }
                if (out && run < 0) {
                    run = i;
                } else if (!out && run >= 0) {
                    boxes.add(new int[] {run * 2, j * 2, i * 2, j * 2 + 2});
                    run = -1;
                }
            }
        }
        return boxes;
    }

    /** Whether the bridge reaches a track-level block {@code dx}, {@code dz} from its middle: its middle's in the circle. */
    public boolean underBridge(int dx, int dz) {
        return dx * dx + dz * dz <= radius * radius;
    }

    /** The pit block {@code dx}, {@code dz} from the pivot. */
    public BlockState cell(int dx, int dz) {
        return defaultBlockState().setValue(X, dx + SPAN).setValue(Z, dz + SPAN);
    }

    public static boolean isPivot(BlockState state) {
        return state.getValue(X) == SPAN && state.getValue(Z) == SPAN;
    }

    /** The pivot of the turntable this block's part of. */
    public static BlockPos pivot(BlockState state, BlockPos pos) {
        return pos.offset(SPAN - state.getValue(X), 0, SPAN - state.getValue(Z));
    }

    @Nullable
    public static TurntableBlockEntity turntable(Level level, BlockState state, BlockPos pos) {
        BlockPos pivot = pivot(state, pos);
        return level.isLoaded(pivot) && level.getBlockEntity(pivot) instanceof TurntableBlockEntity table ? table : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (isPivot(state)) {
            return PIVOT;
        }
        return shapes.computeIfAbsent(state, s -> {
            VoxelShape shape = FLOOR;
            for (int[] box : wall(s.getValue(X) - SPAN, s.getValue(Z) - SPAN)) {
                shape = Shapes.or(shape, Block.box(box[0], 0, box[1], box[2], 16, box[3]));
            }
            return shape.optimize();
        });
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    /** Clicking it turns the bridge to the next track; sneaking, it goes the other way. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        TurntableBlockEntity table = turntable(level, state, pos);
        if (table != null) {
            TurntableBlockEntity.Result result = table.turnNext(player.isSecondaryUseActive() ? -1 : 1);
            player.displayClientMessage(Component.translatable("message.reikotrains.turntable." + result.key()), true);
        }
        return InteractionResult.CONSUME;
    }

    /** A redstone signal beside the pit (into any block of it) turns it, as a click does, as it comes on. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide) {
            TurntableBlockEntity table = turntable(level, state, pos);
            if (table != null) {
                table.redstone(pos, level.hasNeighborSignal(pos));
            }
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** 1 to 8, for the eight ways the bridge can point (east first, then round clockwise); 0 while it's turning. */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        TurntableBlockEntity table = turntable(level, state, pos);
        return table == null ? 0 : table.comparatorSignal();
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(item());
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (player.getAbilities().instabuild) {
            CREATIVE_BREAKS.add(pivot(state, pos).immutable());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Taking any of it away takes the whole turntable up, and gives the turntable back (once). */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            BlockPos pivot = pivot(state, pos).immutable();
            if (DISMANTLING.add(pivot)) {
                try {
                    boolean creative = CREATIVE_BREAKS.remove(pivot);
                    if (server.getBlockEntity(pivot) instanceof TurntableBlockEntity table) {
                        table.release();
                    }
                    int span = span();
                    for (int dx = -span; dx <= span; dx++) {
                        for (int dz = -span; dz <= span; dz++) {
                            BlockPos at = pivot.offset(dx, 0, dz);
                            if (!at.equals(pos) && inPit(dx, dz) && server.getBlockState(at).equals(cell(dx, dz))) {
                                server.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
                            }
                        }
                    }
                    if (!creative && server.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
                        Containers.dropItemStack(server, pivot.getX() + 0.5, pivot.getY() + 1.2, pivot.getZ() + 0.5,
                                new ItemStack(item()));
                    }
                } finally {
                    DISMANTLING.remove(pivot);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isPivot(state) ? new TurntableBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.TURNTABLE.get() || !isPivot(state)) {
            return null;
        }
        return (l, p, s, be) -> TurntableBlockEntity.serverTick(l, p, s, (TurntableBlockEntity) be);
    }
}
