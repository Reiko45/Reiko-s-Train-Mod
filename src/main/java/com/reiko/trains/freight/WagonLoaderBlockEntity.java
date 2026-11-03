package com.reiko.trains.freight;

import com.reiko.trains.config.TrainsConfig;
import com.reiko.trains.registry.ModBlockEntities;
import com.reiko.trains.track.TrackGraph;
import com.reiko.trains.train.DieselLocomotive;
import com.reiko.trains.train.FreightWagon;
import com.reiko.trains.train.LivestockWagon;
import com.reiko.trains.train.TankWagon;
import com.reiko.trains.train.Tender;
import com.reiko.trains.train.TrainCarEntity;
import com.reiko.trains.train.Trains;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/**
 * A wagon loader's workings. It's tied to a track block, and works with the train standing there (stopped, with a car
 * alongside): loading, it moves goods from the storage next to it (chests, barrels, hoppers, other mods' storage)
 * into the train's freight wagons, front to back; unloading, the other way. Redstone pauses it. A comparator reads how
 * full the train's wagons are. Hoppers (or pipes) can also put goods into the train through it, or take them out,
 * whichever way it's working. Trains on a timetable don't leave a station while a loader's still busy with them.
 * <p>
 * Livestock wagons too: loading, it puts animals standing around it (within four blocks) into them, a stall at a time;
 * unloading, it lets them out behind it, on the side away from the track.
 * <p>
 * Its filter (nine items, set in its screen) limits what it moves for the wagons, both ways: only the items shown, or
 * all but them. A filled bucket (or other fluid container) in the filter stands for its fluid, for tank wagons, and a
 * spawn egg for its animal, for livestock wagons. An empty filter lets everything through. Engines' fuel (a tender's
 * coal, a diesel's diesel) isn't filtered.
 */
public class WagonLoaderBlockEntity extends BlockEntity {
    /** How often it works, in ticks. */
    private static final int PERIOD = 2;
    /** How far from it (blocks, across) animals can be standing to be loaded. */
    private static final double ANIMAL_REACH = 4;
    /**
     * How much fluid it pumps into (or out of) each diesel locomotive and tank wagon a go, in millibuckets: twenty
     * buckets a second, so a 256-bucket tank wagon fills in about 13 seconds, much as a freight wagon loads.
     */
    private static final int FLUID_PER_PERIOD = 2000;
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
    /** The filter: up to nine items (copies; it holds nothing really). */
    private final SimpleContainer filter = new SimpleContainer(FILTER_SLOTS);
    /** Whether the filter lets through all but what's in it, rather than only what's in it. */
    private boolean deny;

    public static final int FILTER_SLOTS = 9;

    public WagonLoaderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WAGON_LOADER.get(), pos, state);
        filter.addListener(changed -> setChanged());
    }

    // ----------------------------------------------------------------------------------------------------------------
    // The filter

    public SimpleContainer filter() {
        return filter;
    }

    /** Whether the filter lets through all but what's in it (rather than only what's in it). */
    public boolean isDeny() {
        return deny;
    }

    public void setDeny(boolean deny) {
        this.deny = deny;
        setChanged();
    }

    /** Switches between loading and unloading. */
    public LoaderMode toggleMode() {
        LoaderMode mode = mode().other();
        if (level != null) {
            level.setBlock(worldPosition, getBlockState().setValue(WagonLoaderBlock.MODE, mode).setValue(WagonLoaderBlock.ACTIVE, false), 3);
        }
        return mode;
    }

    /**
     * Whether the filter lets this item through. Filled fluid containers in the filter stand for fluids, and spawn eggs
     * for animals, not items.
     */
    public boolean allows(ItemStack stack) {
        boolean any = false;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack entry = filter.getItem(i);
            if (entry.isEmpty() || FluidUtil.getFluidContained(entry).isPresent() || entry.getItem() instanceof SpawnEggItem) {
                continue;
            }
            any = true;
            if (ItemStack.isSameItem(entry, stack)) {
                return !deny;
            }
        }
        return deny || !any;
    }

    /** Whether the filter lets this fluid through (for tank wagons): the fluids of filled containers in the filter. */
    public boolean allows(FluidStack fluid) {
        boolean any = false;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            var entry = FluidUtil.getFluidContained(filter.getItem(i));
            if (entry.isEmpty()) {
                continue;
            }
            any = true;
            if (FluidStack.isSameFluid(entry.get(), fluid)) {
                return !deny;
            }
        }
        return deny || !any;
    }

    /** Whether the filter lets this animal through (for livestock wagons): the animals of spawn eggs in the filter. */
    public boolean allows(EntityType<?> animal) {
        boolean any = false;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack entry = filter.getItem(i);
            if (!(entry.getItem() instanceof SpawnEggItem egg)) {
                continue;
            }
            any = true;
            if (egg.getType(entry) == animal) {
                return !deny;
            }
        }
        return deny || !any;
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
        List<DieselLocomotive> diesels = new ArrayList<>();
        List<TankWagon> tanks = new ArrayList<>();
        List<LivestockWagon> pens = new ArrayList<>();
        for (TrainCarEntity car : train) {
            if (car instanceof FreightWagon wagon) {
                wagons.add(wagon);
            } else if (car instanceof Tender tender) {
                tenders.add(new InvWrapper(tender)); // its chute takes only coal and the like
            } else if (car instanceof DieselLocomotive diesel) {
                diesels.add(diesel);
            } else if (car instanceof TankWagon tank) {
                tanks.add(tank);
            } else if (car instanceof LivestockWagon pen) {
                pens.add(pen);
            }
        }
        loader.wagons = wagons;
        if (loader.serving != null && !train.contains(loader.serving)) {
            loader.serving = null; // that train's gone
        }
        if ((!diesels.isEmpty() || !tanks.isEmpty()) && !level.hasNeighborSignal(pos)) {
            // Fluids, twenty buckets a second for each car: loading, diesels fill up from tanks next to the loader (a
            // BuildCraft tank, say) and then tank wagons; unloading, tank wagons empty into them.
            List<IFluidHandler> fluids = loader.fluidStorage();
            int pumped = 0;
            if (loader.mode() == LoaderMode.LOAD) {
                for (DieselLocomotive diesel : diesels) {
                    for (IFluidHandler source : fluids) {
                        pumped += FluidUtil.tryFluidTransfer(diesel.tank(), source, FLUID_PER_PERIOD, true).getAmount();
                    }
                }
                for (TankWagon tank : tanks) {
                    int room = FLUID_PER_PERIOD;
                    for (IFluidHandler source : fluids) {
                        FluidStack offered = source.drain(room, IFluidHandler.FluidAction.SIMULATE);
                        if (!offered.isEmpty() && loader.allows(offered)) {
                            room -= FluidUtil.tryFluidTransfer(tank.tank(), source, offered, true).getAmount();
                        }
                    }
                    pumped += FLUID_PER_PERIOD - room;
                }
            } else {
                for (TankWagon tank : tanks) {
                    if (tank.tank().isEmpty() || !loader.allows(tank.tank().getFluid())) {
                        continue;
                    }
                    int left = FLUID_PER_PERIOD;
                    for (IFluidHandler target : fluids) {
                        left -= FluidUtil.tryFluidTransfer(target, tank.tank(), left, true).getAmount();
                    }
                    pumped += FLUID_PER_PERIOD - left;
                }
            }
            if (pumped > 0) {
                loader.moved(train.getFirst());
            }
        }
        if ((!wagons.isEmpty() || !tenders.isEmpty()) && !level.hasNeighborSignal(pos)) {
            int budget = Math.max(1, Mth.ceil(TrainsConfig.LOADER_SPEED.get() * PERIOD / 20.0));
            List<IItemHandler> storage = loader.storage();
            int moved;
            if (loader.mode() == LoaderMode.LOAD) {
                // Tenders take their coal first (their chutes take nothing else, and fuel isn't filtered), then the
                // wagons fill with what the filter lets through.
                moved = move(storage, tenders, budget, stack -> true);
                moved += move(storage, handlers(wagons), budget - moved, loader::allows);
            } else {
                moved = move(handlers(wagons), storage, budget, loader::allows);
            }
            if (moved > 0) {
                loader.moved(train.getFirst());
            }
        }
        if (!pens.isEmpty() && !level.hasNeighborSignal(pos)) {
            // Animals, one into (or out of) each livestock wagon a go: in from around the loader, out behind it.
            int moved = loader.mode() == LoaderMode.LOAD ? loader.loadAnimals(pens) : loader.unloadAnimals(pens);
            if (moved > 0) {
                loader.moved(train.getFirst());
            }
        }
        boolean active = loader.isBusy();
        if (state.getValue(WagonLoaderBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(WagonLoaderBlock.ACTIVE, active), 3);
        }
        int signal = wagons.isEmpty() && tanks.isEmpty() && pens.isEmpty() ? 0 : redstone(wagons, tanks, pens);
        if (signal != loader.comparator) {
            loader.comparator = signal;
            level.updateNeighbourForOutputSignal(pos, level.getBlockState(pos).getBlock());
        }
    }

    /** Animals standing around it (within {@link #ANIMAL_REACH} blocks) that the filter lets through. */
    private List<Animal> waitingAnimals() {
        AABB around = new AABB(worldPosition).inflate(ANIMAL_REACH, 1, ANIMAL_REACH);
        List<Animal> animals = new ArrayList<>(level.getEntitiesOfClass(Animal.class, around,
                animal -> LivestockWagon.canCarry(animal) && !animal.isLeashed() && allows(animal.getType())));
        Vec3 here = Vec3.atCenterOf(worldPosition);
        animals.sort(java.util.Comparator.comparingDouble(animal -> animal.distanceToSqr(here)));
        return animals;
    }

    /** Puts the nearest waiting animal into each livestock wagon with a stall free. Returns how many went in. */
    private int loadAnimals(List<LivestockWagon> pens) {
        List<Animal> waiting = waitingAnimals();
        int moved = 0;
        for (LivestockWagon pen : pens) {
            while (pen.hasRoom() && !waiting.isEmpty()) {
                if (pen.load(waiting.removeFirst())) {
                    moved++;
                    break;
                }
            }
        }
        return moved;
    }

    /** Lets an animal out of each livestock wagon that has any, behind the loader (away from the track). */
    private int unloadAnimals(List<LivestockWagon> pens) {
        int moved = 0;
        for (LivestockWagon pen : pens) {
            List<Animal> aboard = pen.livestock();
            for (int i = aboard.size() - 1; i >= 0; i--) {
                Animal animal = aboard.get(i);
                if (!allows(animal.getType())) {
                    continue;
                }
                Vec3 spot = outSpot(animal);
                if (spot != null) {
                    pen.unload(animal, spot);
                    moved++;
                }
                break;
            }
        }
        return moved;
    }

    /** Somewhere an animal let out can stand: behind the loader, or beside that, or further back. */
    @Nullable
    private Vec3 outSpot(Animal animal) {
        Direction back = getBlockState().getValue(WagonLoaderBlock.FACING).getOpposite();
        BlockPos behind = worldPosition.relative(back);
        for (BlockPos pos : new BlockPos[]{behind, behind.relative(back.getClockWise()), behind.relative(back.getCounterClockWise()),
                behind.relative(back), worldPosition.relative(back.getClockWise()), worldPosition.relative(back.getCounterClockWise())}) {
            for (int dy = 0; dy >= -1; dy--) {
                Vec3 safe = DismountHelper.findSafeDismountLocation(animal.getType(), level, pos.above(dy), true);
                if (safe != null) {
                    return safe;
                }
            }
        }
        return null;
    }

    private void moved(TrainCarEntity car) {
        serving = car;
        lastMoved = level.getGameTime();
    }

    /** The cars of the train standing at its track block: stopped, with a car alongside it. Empty if there's none. */
    private List<TrainCarEntity> standingTrain() {
        return isAttached() ? Trains.standingAt(level, node) : List.of();
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

    /** The fluid storage next to it (tanks, pipes), for filling diesels. */
    private List<IFluidHandler> fluidStorage() {
        List<IFluidHandler> handlers = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            BlockPos next = worldPosition.relative(direction);
            if (level.getBlockEntity(next) instanceof WagonLoaderBlockEntity) {
                continue;
            }
            IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, next, direction.getOpposite());
            if (handler != null && !handlers.contains(handler)) {
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

    /**
     * Moves up to {@code budget} items that {@code allowed} lets through from the slots of {@code from}, in order, into
     * {@code to}; returns how many.
     */
    static int move(List<IItemHandler> from, List<IItemHandler> to, int budget, Predicate<ItemStack> allowed) {
        int moved = 0;
        if (budget <= 0 || to.isEmpty()) {
            return 0;
        }
        for (IItemHandler source : from) {
            for (int slot = 0; slot < source.getSlots() && moved < budget; slot++) {
                ItemStack available = source.extractItem(slot, budget - moved, true);
                if (available.isEmpty() || !allowed.test(available)) {
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

    /**
     * 0 to 15 for how full the train's wagons are, the way a comparator reads a chest (1 for anything at all): each
     * freight wagon and tank wagon counts the same.
     */
    private static int redstone(List<FreightWagon> wagons, List<TankWagon> tanks, List<LivestockWagon> pens) {
        float fullness = 0;
        for (FreightWagon wagon : wagons) {
            float used = 0;
            for (int i = 0; i < wagon.getContainerSize(); i++) {
                ItemStack stack = wagon.getItem(i);
                if (!stack.isEmpty()) {
                    used += stack.getCount() / (float) wagon.getMaxStackSize(stack);
                }
            }
            fullness += used / wagon.getContainerSize();
        }
        for (TankWagon tank : tanks) {
            fullness += tank.tank().getFluidAmount() / (float) TankWagon.CAPACITY;
        }
        for (LivestockWagon pen : pens) {
            fullness += pen.animals() / (float) LivestockWagon.STALLS;
        }
        int cars = wagons.size() + tanks.size() + pens.size();
        return cars == 0 ? 0 : Mth.lerpDiscrete(fullness / cars, 0, 15);
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
        tag.put("Filter", filter.createTag(registries));
        tag.putBoolean("FilterDeny", deny);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Track")) {
            node = BlockPos.of(tag.getLong("Track"));
        }
        filter.clearContent();
        if (tag.contains("Filter")) {
            filter.fromTag(tag.getList("Filter", Tag.TAG_COMPOUND), registries);
        }
        deny = tag.getBoolean("FilterDeny");
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
            if (wagon == null || mode() != LoaderMode.LOAD || !working() || !allows(stack)) {
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
            if (wagon == null || mode() != LoaderMode.UNLOAD || !working() || !allows(wagon.getItem(slot % FreightWagon.SLOTS))) {
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
