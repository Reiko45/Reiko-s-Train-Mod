package com.reiko.trains.power;

import buildcraftreborn.api.mj.IMjConnector;
import buildcraftreborn.api.mj.IMjReadable;
import buildcraftreborn.api.mj.IMjRedstoneReceiver;
import buildcraftreborn.api.mj.MjAPI;
import buildcraftreborn.api.mj.MjCapabilityHelper;
import com.reiko.trains.config.TrainsConfig;
import com.reiko.trains.registry.ModBlockEntities;
import com.reiko.trains.track.TrackGraph;
import com.reiko.trains.train.ElectricLocomotive;
import com.reiko.trains.train.TrainCarEntity;
import com.reiko.trains.train.Trains;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A charging point's workings. Power comes in as BuildCraft MJ (from engines and kinesis pipes, through
 * {@link MjAPI}'s capabilities, or as other mods' energy, which BuildCraft converts as its config says) and is kept in
 * its store; it goes out into the batteries of any electric locomotive in a train standing at its track block (stopped,
 * with a car alongside it), up to its charging rate. Redstone pauses it. A comparator reads how full its store is.
 * Trains on a timetable don't leave a station while a charging point is still charging them. Stored in micro-MJ, as
 * BuildCraft counts it.
 */
public class ChargingPointBlockEntity extends BlockEntity {
    /** How often it charges, in ticks. */
    private static final int PERIOD = 2;
    /** It counts as busy with a train until it's gone this long without charging it. */
    private static final int BUSY_TICKS = 20;
    private static final Map<Level, Set<ChargingPointBlockEntity>> LOADED = new WeakHashMap<>();

    private final Receiver receiver = new Receiver();
    private final MjCapabilityHelper capabilities = new MjCapabilityHelper(receiver);
    /** Micro-MJ stored. */
    private long stored;
    @Nullable
    private BlockPos node;
    /** A car of the train it last charged, and when (game time). */
    @Nullable
    private TrainCarEntity serving;
    private long lastCharged = Long.MIN_VALUE / 2;
    private int comparator;

    public ChargingPointBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHARGING_POINT.get(), pos, state);
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

    /** BuildCraft's MJ capabilities (receiver, redstone receiver, readable, connector) and an energy view, as its config allows. */
    public MjCapabilityHelper capabilities() {
        return capabilities;
    }

    /** Whole MJ stored. */
    public long storedMj() {
        return stored / MjAPI.MJ;
    }

    public static long capacityMj() {
        return TrainsConfig.CHARGER_CAPACITY.get();
    }

    private static long capacityMicro() {
        return capacityMj() * MjAPI.MJ;
    }

    /** Adds up to {@code mj} MJ to the store (an admin's top-up, or a test). Returns how much went in. */
    public long addMj(long mj) {
        long added = Math.max(0, Math.min(mj * MjAPI.MJ, capacityMicro() - stored));
        stored += added;
        setChanged();
        return added / MjAPI.MJ;
    }

    /** How full its store is, 0-15, for a comparator. */
    public int comparatorSignal() {
        return comparator;
    }

    private static int signalFor(long stored) {
        return stored <= 0 ? 0 : Math.max(1, (int) Math.min(15, stored * 15 / Math.max(1, capacityMicro())));
    }

    /** How many of the four bars on its front light up: none only when it's empty. */
    private static int barsFor(long stored) {
        return stored <= 0 ? 0 : Math.max(1, (int) Math.min(4, (stored * 4 + capacityMicro() - 1) / Math.max(1, capacityMicro())));
    }

    private boolean isBusy() {
        return serving != null && level != null && level.getGameTime() - lastCharged <= BUSY_TICKS;
    }

    /** Whether a charging point is still charging any car of this train. */
    public static boolean busyWith(Level level, List<TrainCarEntity> cars) {
        synchronized (LOADED) {
            Set<ChargingPointBlockEntity> chargers = LOADED.get(level);
            if (chargers == null) {
                return false;
            }
            for (ChargingPointBlockEntity charger : chargers) {
                if (!charger.isRemoved() && charger.isBusy() && cars.contains(charger.serving)) {
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
                Set<ChargingPointBlockEntity> chargers = LOADED.get(level);
                if (chargers != null) {
                    chargers.remove(this);
                }
            }
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ChargingPointBlockEntity charger) {
        if ((level.getGameTime() + pos.hashCode()) % PERIOD != 0) {
            return;
        }
        List<TrainCarEntity> train = charger.isAttached() ? Trains.standingAt(level, charger.node) : List.of();
        if (charger.serving != null && !train.contains(charger.serving)) {
            charger.serving = null; // that train's gone
        }
        if (!train.isEmpty() && charger.stored > 0 && !level.hasNeighborSignal(pos)) {
            // Each electric in the train gets up to the charging rate, while the store lasts.
            double budget = TrainsConfig.CHARGER_RATE.get() * (double) PERIOD;
            boolean charged = false;
            boolean wantsMore = false;
            for (TrainCarEntity car : train) {
                if (car instanceof ElectricLocomotive electric && charger.stored > 0) {
                    double give = Math.min(Math.min(budget, electric.room()), charger.stored / (double) MjAPI.MJ);
                    if (give >= 0.001) {
                        electric.charge(give);
                        charger.stored = Math.max(0, charger.stored - Math.round(give * MjAPI.MJ));
                        charged = true;
                    }
                    wantsMore |= electric.room() >= 1.0;
                }
            }
            if (charged) {
                charger.setChanged();
            }
            // It's busy with the train (holding a timetabled one) while it's charging it and could go on: not once the
            // batteries are full, nor once the store's run dry (engines still trickle power in, but too slowly to wait
            // for).
            if (charged && wantsMore && charger.stored >= MjAPI.MJ) {
                charger.serving = train.getFirst();
                charger.lastCharged = level.getGameTime();
            }
        }
        int bars = barsFor(charger.stored);
        boolean busy = charger.isBusy();
        if (state.getValue(ChargingPointBlock.LEVEL) != bars || state.getValue(ChargingPointBlock.CHARGING) != busy) {
            level.setBlock(pos, state.setValue(ChargingPointBlock.LEVEL, bars).setValue(ChargingPointBlock.CHARGING, busy), 3);
        }
        int signal = signalFor(charger.stored);
        if (signal != charger.comparator) {
            charger.comparator = signal;
            level.updateNeighbourForOutputSignal(pos, level.getBlockState(pos).getBlock());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("Stored", stored);
        if (node != null) {
            tag.putLong("Track", node.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stored = Math.max(0, tag.getLong("Stored"));
        if (tag.contains("Track")) {
            node = BlockPos.of(tag.getLong("Track"));
        }
    }

    /** What engines, pipes and gauges talk to: takes power until full, and says how full it is. */
    private final class Receiver implements IMjRedstoneReceiver, IMjReadable {
        @Override
        public boolean canConnect(IMjConnector other) {
            return true;
        }

        @Override
        public long getPowerRequested() {
            return Math.max(0, capacityMicro() - stored);
        }

        /** Returns the power it couldn't take, as BuildCraft expects. */
        @Override
        public long receivePower(long microJoules, boolean simulate) {
            long taken = Math.max(0, Math.min(microJoules, capacityMicro() - stored));
            if (!simulate && taken > 0) {
                stored += taken;
                setChanged();
            }
            return microJoules - taken;
        }

        @Override
        public long getStored() {
            return stored;
        }

        @Override
        public long getCapacity() {
            return capacityMicro();
        }
    }
}
