package com.reiko.trains.freight;

import com.reiko.trains.config.TrainsConfig;
import com.reiko.trains.registry.ModBlockEntities;
import com.reiko.trains.track.TrackEdge;
import com.reiko.trains.track.TrackGraph;
import com.reiko.trains.train.FreightWagon;
import com.reiko.trains.train.Tender;
import com.reiko.trains.train.TrainCarEntity;
import com.reiko.trains.train.Trains;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/**
 * A wagon loader's workings. It's tied to a track block, and works with the train standing there (stopped, with a car
 * alongside): loading, it moves goods from the storage next to it (chests, barrels, hoppers, other mods' storage)
 * into the train's freight wagons, front to back; unloading, the other way. Redstone pauses it. A comparator reads how
 * full the train's wagons are. Hoppers (or pipes) can also put goods into the train through it, or take them out,
 * whichever way it's working. Trains on a timetable don't leave a station while a loader's still busy with them.
 */
public class WagonLoaderBlockEntity extends BlockEntity {
    /** How often it works, in ticks. */
    private static final int PERIOD = 2;
    /** A loader counts as busy with a train until it's gone this long without moving anything. */
    private static final int BUSY_TICKS = 20;
    private static final Map<Level, Set<WagonLoaderBlockEntity>> LOADED = new WeakHashMap<>();

    @Nullable
    private BlockPos node;
    /** The freight wagons of the train standing here (none if there's no train, or it's moving). */
    private List<FreightWagon> wagons = List.of();
    /** A car of the train it last moved goods for, and when (game time). */
    @Nullable
    private TrainCarEntity serving;
    private long lastMoved = Long.MIN_VALUE / 2;
    private int comparator;
    private final Port port = new Port();

    public WagonLoaderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WAGON_LOADER.get(), pos, state);
    }

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

    public LoaderMode mode() {
        return getBlockState().getValue(WagonLoaderBlock.MODE);
    }

    /** Whether it's moved goods for a train recently. */
    public boolean isBusy() {
        return level != null && serving != null && level.getGameTime() - lastMoved <= BUSY_TICKS;
    }

    public int comparatorSignal() {
        return comparator;
    }

    /** What hoppers and pipes see: the train's wagons, going in when it's loading and out when it's unloading. */
    public IItemHandler port() {
        return port;
    }

    /**
     * Whether a wagon loader is still busy loading or unloading any car of this train: if so, what it's doing (and if
     * several are, loading wins); null if none is.
     */
    @Nullable
    public static LoaderMode busyWith(Level level, List<TrainCarEntity> cars) {
        LoaderMode found = null;
        synchronized (LOADED) {
            Set<WagonLoaderBlockEntity> loaders = LOADED.get(level);
            if (loaders == null) {
                return null;
            }
            for (WagonLoaderBlockEntity loader : loaders) {
                if (!loader.isRemoved() && loader.isBusy() && cars.contains(loader.serving)) {
                    if (loader.mode() == LoaderMode.LOAD) {
                        return LoaderMode.LOAD;
                    }
                    found = LoaderMode.UNLOAD;
                }
            }
        }
        return found;
    }

    // ----------------------------------------------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, WagonLoaderBlockEntity loader) {
        if ((level.getGameTime() + pos.hashCode()) % PERIOD != 0) {
            return;
        }
        List<TrainCarEntity> train = loader.standingTrain();
        List<FreightWagon> wagons = new ArrayList<>();
        List<IItemHandler> tenders = new ArrayList<>();
        for (TrainCarEntity car : train) {
            if (car instanceof FreightWagon wagon) {
                wagons.add(wagon);
            } else if (car instanceof Tender tender) {
                tenders.add(new InvWrapper(tender));
            }
        }
        loader.wagons = wagons;
        if (loader.serving != null && !train.contains(loader.serving)) {
            loader.serving = null; // that train's gone
        }
        if ((!wagons.isEmpty() || !tenders.isEmpty()) && !level.hasNeighborSignal(pos)) {
            int budget = Math.max(1, Mth.ceil(TrainsConfig.LOADER_SPEED.get() * PERIOD / 20.0));
            List<IItemHandler> storage = loader.storage();
            int moved;
            if (loader.mode() == LoaderMode.LOAD) {
                // Tenders take fuel first (only fuel: their chutes take nothing else), then the wagons fill.
                List<IItemHandler> targets = new ArrayList<>(tenders);
                targets.addAll(handlers(wagons));
                moved = move(storage, targets, budget);
            } else {
                moved = move(handlers(wagons), storage, budget);
            }
            if (moved > 0) {
                loader.moved(train.getFirst());
            }
        }
        boolean active = loader.isBusy();
        if (state.getValue(WagonLoaderBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(WagonLoaderBlock.ACTIVE, active), 3);
        }
        int signal = wagons.isEmpty() ? 0 : redstone(wagons);
        if (signal != loader.comparator) {
            loader.comparator = signal;
            level.updateNeighbourForOutputSignal(pos, level.getBlockState(pos).getBlock());
        }
    }

    private void moved(TrainCarEntity car) {
        serving = car;
        lastMoved = level.getGameTime();
    }

    /** The cars of the train standing at its track block: stopped, with a car alongside it. Empty if there's none. */
    private List<TrainCarEntity> standingTrain() {
        if (!isAttached()) {
            return List.of();
        }
        Vec3 at = TrackEdge.node(node);
        for (TrainCarEntity car : Trains.loadedCars(level)) {
            if (!car.isOnTrack() || Math.abs(car.trainSpeed()) > 0.005) {
                continue;
            }
            Vec3 d = at.subtract(car.position());
            Vec3 f = car.flatForward();
            double along = Math.abs(d.x * f.x + d.z * f.z);
            double across = Math.abs(d.x * -f.z + d.z * f.x);
            if (along <= car.halfLength() + 0.5 && across < 1.5 && Math.abs(d.y) < 2.5) {
                return Trains.train(car);
            }
        }
        return List.of();
    }

    /** The storage next to it, on any side (not other loaders: they'd only pass goods back and forth). */
    private List<IItemHandler> storage() {
        List<IItemHandler> handlers = new ArrayList<>();
        Set<IItemHandler> seen = new HashSet<>();
        for (Direction direction : Direction.values()) {
            BlockPos next = worldPosition.relative(direction);
            if (level.getBlockEntity(next) instanceof WagonLoaderBlockEntity) {
                continue;
            }
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, next, direction.getOpposite());
            if (handler != null && seen.add(handler)) {
                handlers.add(handler);
            }
        }
        return handlers;
    }

    private static List<IItemHandler> handlers(List<FreightWagon> wagons) {
        List<IItemHandler> handlers = new ArrayList<>(wagons.size());
        for (FreightWagon wagon : wagons) {
            handlers.add(new InvWrapper(wagon));
        }
        return handlers;
    }

    /** Moves up to {@code budget} items from the slots of {@code from}, in order, into {@code to}; returns how many. */
    static int move(List<IItemHandler> from, List<IItemHandler> to, int budget) {
        int moved = 0;
        for (IItemHandler source : from) {
            for (int slot = 0; slot < source.getSlots() && moved < budget; slot++) {
                ItemStack available = source.extractItem(slot, budget - moved, true);
                if (available.isEmpty()) {
                    continue;
                }
                int fits = available.getCount() - insert(to, available, true).getCount();
                if (fits <= 0) {
                    continue;
                }
                ItemStack taken = source.extractItem(slot, fits, false);
                ItemStack left = insert(to, taken, false);
                if (!left.isEmpty()) {
                    left = source.insertItem(slot, left, false); // put back what didn't go in after all
                }
                moved += taken.getCount() - left.getCount();
            }
            if (moved >= budget) {
                break;
            }
        }
        return moved;
    }

    /** Puts {@code stack} into these, the first first (topping up stacks of it before using empty slots); returns what's left. */
    private static ItemStack insert(List<IItemHandler> handlers, ItemStack stack, boolean simulate) {
        ItemStack left = stack;
        for (IItemHandler handler : handlers) {
            if (left.isEmpty()) {
                break;
            }
            left = ItemHandlerHelper.insertItemStacked(handler, left, simulate);
        }
        return left;
    }

    /** 0 to 15 for how full the wagons are, the way a comparator reads a chest (1 for anything at all). */
    private static int redstone(List<FreightWagon> wagons) {
        float fullness = 0;
        int slots = 0;
        for (FreightWagon wagon : wagons) {
            for (int i = 0; i < wagon.getContainerSize(); i++) {
                ItemStack stack = wagon.getItem(i);
                if (!stack.isEmpty()) {
                    fullness += stack.getCount() / (float) wagon.getMaxStackSize(stack);
                }
            }
            slots += wagon.getContainerSize();
        }
        return slots == 0 ? 0 : Mth.lerpDiscrete(fullness / slots, 0, 15);
    }

    // ----------------------------------------------------------------------------------------------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            synchronized (LOADED) {
                LOADED.computeIfAbsent(level, l -> new HashSet<>()).add(this);
            }
        }
    }

    @Override
    public void setRemoved() {
        unregister();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        unregister();
        super.onChunkUnloaded();
    }

    private void unregister() {
        if (level != null) {
            synchronized (LOADED) {
                Set<WagonLoaderBlockEntity> loaders = LOADED.get(level);
                if (loaders != null) {
                    loaders.remove(this);
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (node != null) {
            tag.putLong("Track", node.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Track")) {
            node = BlockPos.of(tag.getLong("Track"));
        }
    }

    /** The train's wagons as one inventory, for hoppers and pipes: in only when loading, out only when unloading. */
    private final class Port implements IItemHandler {
        private List<FreightWagon> live() {
            List<FreightWagon> live = new ArrayList<>(wagons.size());
            for (FreightWagon wagon : wagons) {
                if (!wagon.isRemoved()) {
                    live.add(wagon);
                }
            }
            return live;
        }

        @Override
        public int getSlots() {
            return live().size() * FreightWagon.SLOTS;
        }

        @Nullable
        private FreightWagon wagon(List<FreightWagon> live, int slot) {
            int index = slot / FreightWagon.SLOTS;
            return slot < 0 || index >= live.size() ? null : live.get(index);
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            FreightWagon wagon = wagon(live(), slot);
            return wagon == null ? ItemStack.EMPTY : wagon.getItem(slot % FreightWagon.SLOTS);
        }

        private boolean working() {
            return level != null && !level.hasNeighborSignal(worldPosition);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            FreightWagon wagon = wagon(live(), slot);
            if (wagon == null || mode() != LoaderMode.LOAD || !working()) {
                return stack;
            }
            ItemStack left = new InvWrapper(wagon).insertItem(slot % FreightWagon.SLOTS, stack, simulate);
            if (!simulate && left.getCount() < stack.getCount()) {
                moved(wagon);
            }
            return left;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            FreightWagon wagon = wagon(live(), slot);
            if (wagon == null || mode() != LoaderMode.UNLOAD || !working()) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = new InvWrapper(wagon).extractItem(slot % FreightWagon.SLOTS, amount, simulate);
            if (!simulate && !taken.isEmpty()) {
                moved(wagon);
            }
            return taken;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 99;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return mode() == LoaderMode.LOAD;
        }
    }
}
