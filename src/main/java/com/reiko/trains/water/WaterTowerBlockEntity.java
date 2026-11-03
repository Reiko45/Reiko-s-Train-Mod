package com.reiko.trains.water;

import com.reiko.trains.config.TrainsConfig;
import com.reiko.trains.registry.ModBlockEntities;
import com.reiko.trains.track.TrackGraph;
import com.reiko.trains.train.TrainCarEntity;
import com.reiko.trains.train.Trains;
import com.reiko.trains.train.WaterStore;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * A water tower's workings: its tank (water only), filled by buckets, pipes and pumps, and its spout, which fills the
 * tank engines and tenders of any train standing at its track block (stopped, with a car alongside it), up to its rate.
 * Redstone holds the spout up. A comparator reads how full its tank is. Trains on a timetable don't leave a station
 * while a water tower is still filling them.
 */
public class WaterTowerBlockEntity extends BlockEntity {
    /** How much it holds: 256 buckets. */
    public static final int CAPACITY = 256_000;
    /** How often it fills, in ticks. */
    private static final int PERIOD = 2;
    /** It counts as busy with a train until it's gone this long without filling it. */
    private static final int BUSY_TICKS = 20;
    private static final Map<Level, Set<WaterTowerBlockEntity>> LOADED = new WeakHashMap<>();

    private final FluidTank tank = new FluidTank(CAPACITY, stack -> stack.getFluid().is(FluidTags.WATER)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    @Nullable
    private BlockPos node;
    /** A car of the train it last filled, and when (game time). */
    @Nullable
    private TrainCarEntity serving;
    private long lastFilled = Long.MIN_VALUE / 2;
    private int comparator;

    public WaterTowerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WATER_TOWER.get(), pos, state);
    }

    /** Ties it to the track block it's beside. */
    public void attach(BlockPos node) {
        this.node = node.immutable();
        setChanged();
    }

    public BlockPos node() {
        return node == null ? worldPosition : node;
    }

    public boolean isAttached() {
        return node != null && level != null && TrackGraph.axis(level, node) != null;
    }

    /** Its tank: buckets, pipes and pumps fill it (and can draw from it). */
    public FluidTank tank() {
        return tank;
    }

    /** Millibuckets of water it holds. */
    public int stored() {
        return tank.getFluidAmount();
    }

    /** Pours in up to {@code millibuckets} of water (a test, or an admin's top-up). Returns how much went in. */
    public int addWater(int millibuckets) {
        return tank.fill(new FluidStack(Fluids.WATER, millibuckets), FluidTank.FluidAction.EXECUTE);
    }

    public int comparatorSignal() {
        return comparator;
    }

    private static int signalFor(int stored) {
        return stored <= 0 ? 0 : Math.max(1, Math.min(15, (int) ((long) stored * 15 / CAPACITY)));
    }

    /** How many of the four marks on its gauge are lit: none only when it's empty. */
    private static int barsFor(int stored) {
        return stored <= 0 ? 0 : Math.max(1, Math.min(4, (stored * 4 + CAPACITY - 1) / CAPACITY));
    }

    private boolean isBusy() {
        return serving != null && level != null && level.getGameTime() - lastFilled <= BUSY_TICKS;
    }

    /** Whether a water tower is still filling any car of this train. */
    public static boolean busyWith(Level level, List<TrainCarEntity> cars) {
        synchronized (LOADED) {
            Set<WaterTowerBlockEntity> towers = LOADED.get(level);
            if (towers == null) {
                return false;
            }
            for (WaterTowerBlockEntity tower : towers) {
                if (!tower.isRemoved() && tower.isBusy() && cars.contains(tower.serving)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            synchronized (LOADED) {
                LOADED.computeIfAbsent(level, l -> Collections.newSetFromMap(new WeakHashMap<>())).add(this);
            }
        }
    }

    @Override
    public void setRemoved() {
        forget();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        forget();
        super.onChunkUnloaded();
    }

    private void forget() {
        if (level != null && !level.isClientSide) {
            synchronized (LOADED) {
                Set<WaterTowerBlockEntity> towers = LOADED.get(level);
                if (towers != null) {
                    towers.remove(this);
                }
            }
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WaterTowerBlockEntity tower) {
        if ((level.getGameTime() + pos.hashCode()) % PERIOD != 0) {
            return;
        }
        List<TrainCarEntity> train = tower.isAttached() ? Trains.standingAt(level, tower.node) : List.of();
        if (tower.serving != null && !train.contains(tower.serving)) {
            tower.serving = null; // that train's gone
        }
        if (!train.isEmpty() && tower.stored() > 0 && !level.hasNeighborSignal(pos) && WaterStore.needed()) {
            // Each tank engine and tender in the train gets up to the rate, while the tank lasts.
            int budget = TrainsConfig.WATER_TOWER_RATE.get() * PERIOD;
            boolean filled = false;
            boolean wantsMore = false;
            for (TrainCarEntity car : train) {
                if (car instanceof WaterStore store && store.waterCapacity() > 0) {
                    int give = (int) Math.min(Math.min(budget, Math.floor(store.waterRoom())), tower.stored());
                    if (give > 0) {
                        store.setWater(store.water() + give);
                        tower.tank.drain(give, FluidTank.FluidAction.EXECUTE);
                        filled = true;
                    }
                    wantsMore |= store.waterRoom() >= 1;
                }
            }
            if (filled && !tower.isBusy()) {
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 0.8F);
            }
            // It's busy with the train (holding a timetabled one) while it's filling it and could go on.
            if (filled && wantsMore && tower.stored() > 0) {
                tower.serving = train.getFirst();
                tower.lastFilled = level.getGameTime();
            }
        }
        int bars = barsFor(tower.stored());
        boolean busy = tower.isBusy();
        if (state.getValue(WaterTowerBlock.LEVEL) != bars || state.getValue(WaterTowerBlock.FILLING) != busy) {
            level.setBlock(pos, state.setValue(WaterTowerBlock.LEVEL, bars).setValue(WaterTowerBlock.FILLING, busy), 3);
            // The tank (with the gauge and the spout) shows it.
            BlockState top = level.getBlockState(pos.above());
            if (top.getBlock() instanceof WaterTowerBlock) {
                level.setBlock(pos.above(), top.setValue(WaterTowerBlock.LEVEL, bars).setValue(WaterTowerBlock.FILLING, busy), 3);
            }
        }
        int signal = signalFor(tower.stored());
        if (signal != tower.comparator) {
            tower.comparator = signal;
            level.updateNeighbourForOutputSignal(pos, level.getBlockState(pos).getBlock());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Water", tank.getFluidAmount());
        if (node != null) {
            tag.putLong("Track", node.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int water = Math.max(0, Math.min(CAPACITY, tag.getInt("Water")));
        tank.setFluid(water > 0 ? new FluidStack(Fluids.WATER, water) : FluidStack.EMPTY);
        if (tag.contains("Track")) {
            node = BlockPos.of(tag.getLong("Track"));
        }
    }
}
