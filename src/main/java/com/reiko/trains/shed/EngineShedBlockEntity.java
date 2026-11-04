package com.reiko.trains.shed;

import buildcraftreborn.api.mj.IMjConnector;
import buildcraftreborn.api.mj.IMjReadable;
import buildcraftreborn.api.mj.IMjRedstoneReceiver;
import buildcraftreborn.api.mj.MjAPI;
import buildcraftreborn.api.mj.MjCapabilityHelper;
import com.reiko.trains.config.TrainsConfig;
import com.reiko.trains.registry.ModBlockEntities;
import com.reiko.trains.train.DieselFuel;
import com.reiko.trains.train.DieselLocomotive;
import com.reiko.trains.train.ElectricLocomotive;
import com.reiko.trains.train.FuelStore;
import com.reiko.trains.train.Locomotive;
import com.reiko.trains.train.SteamLocomotive;
import com.reiko.trains.train.Tender;
import com.reiko.trains.train.TrainCarEntity;
import com.reiko.trains.train.Trains;
import com.reiko.trains.train.WaterStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * An engine shed's workings, kept in the middle of its lintel. Its doors open by themselves as a train comes up to
 * them (from either side), and shut once it's gone by; a redstone signal at the frame, or a click, holds them open.
 *
 * <p>Its store keeps engine fuel (nine slots, from hoppers and pipes or by hand), water and diesel (64 buckets each,
 * from pipes or buckets), and BuildCraft MJ (from engines and kinesis pipes, as much as a charging point holds). It
 * tops up every locomotive and tender standing still on the track inside the shed, up to {@link #DEPTH} blocks in from
 * the doors: coal for steam engines and tenders, water for them too (if the server's steam engines need water),
 * diesel for diesels, and charge for electrics. Trains on a timetable stopped inside wait until they're topped up.
 */
public class EngineShedBlockEntity extends BlockEntity {
    public static final int FUEL_SLOTS = 9;
    public static final int WATER_CAPACITY = 64_000;
    public static final int DIESEL_CAPACITY = 64_000;
    /** How far into the shed (from the doors, along the track) it looks after engines, in blocks. */
    public static final int DEPTH = 32;
    /** How much diesel it pumps into a diesel a tick, in millibuckets. */
    public static final int DIESEL_RATE = 250;
    /** How many lumps of fuel it shovels into each firebox or tender each time round. */
    private static final int FUEL_ITEMS = 4;
    private static final int PERIOD = 2;
    private static final int BUSY_TICKS = 20;
    /** How far the doors swing each tick, as a share of the way open. */
    private static final float SWING = 0.06F;
    private static final Map<Level, Set<EngineShedBlockEntity>> LOADED = new WeakHashMap<>();

    private final ItemStackHandler fuel = new ItemStackHandler(FUEL_SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isFuel(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final FluidTank water = new FluidTank(WATER_CAPACITY, stack -> stack.getFluid().is(FluidTags.WATER)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final FluidTank diesel = new FluidTank(DIESEL_CAPACITY, DieselFuel::isDiesel) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final Fluids fluids = new Fluids();
    /** The fuel store as pipes and hoppers see it: the whole shed's (the keeper's). */
    private final IItemHandler sharedItems = new IItemHandler() {
        @Override
        public int getSlots() {
            return FUEL_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return keeper().fuel.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return keeper().fuel.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return keeper().fuel.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return keeper().fuel.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isFuel(stack);
        }
    };
    private final Receiver receiver = new Receiver();
    private final MjCapabilityHelper capabilities = new MjCapabilityHelper(receiver);
    /** Micro-MJ stored. */
    private long stored;
    /** Held open by a click; held open by a redstone signal beside these blocks of the frame. */
    private boolean held;
    private final Set<BlockPos> powered = new HashSet<>();
    /** Whether the doors are open (or opening); on the client, how far open they are, now and last tick. */
    private boolean open;
    private float openness;
    private float opennessO;
    @Nullable
    private TrainCarEntity serving;
    private long lastServed = Long.MIN_VALUE / 2;

    public EngineShedBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENGINE_SHED.get(), pos, state);
    }

    public static boolean isFuel(ItemStack stack) {
        return stack.is(Locomotive.FUEL) && stack.getBurnTime(RecipeType.SMELTING) > 0 && !stack.hasCraftingRemainingItem();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Where it is

    public Direction facing() {
        return getBlockState().getValue(EngineShedBlock.FACING);
    }

    /** The track block under the middle of the doorway. */
    public BlockPos track() {
        return worldPosition.below(4);
    }

    /** The middle of the doorway at rail height. */
    private Vec3 door() {
        BlockPos track = track();
        return new Vec3(track.getX() + 0.5, track.getY() + 0.25, track.getZ() + 0.5);
    }

    /** Into the shed, along the track. */
    private Vec3 inward() {
        return Vec3.atLowerCornerOf(facing().getOpposite().getNormal());
    }

    private Vec3 right() {
        return Vec3.atLowerCornerOf(facing().getClockWise().getNormal());
    }

    // ----------------------------------------------------------------------------------------------------------------
    // The store

    // Doors side by side in the same wall (on tracks four or five blocks apart, sharing a post or post to post, facing
    // the same way) make one shed of several roads: the store is the leftmost doors' (looking out of the shed),
    // whichever doors fuel, water, diesel or power is put in at, and each set tops up the engines on its own road.

    /** The doors beside these in the same wall, this side (+1 to the right, looking out of the shed), if any. */
    @Nullable
    public EngineShedBlockEntity beside(int side) {
        if (level == null) {
            return null;
        }
        Direction right = facing().getClockWise();
        for (int gap = 4; gap <= 5; gap++) {
            BlockPos controller = worldPosition.relative(right, gap * side);
            if (level.isLoaded(controller) && level.getBlockEntity(controller) instanceof EngineShedBlockEntity shed && !shed.isRemoved()
                    && shed.facing() == facing()) {
                return shed;
            }
        }
        return null;
    }

    /** The doors at the left end of the row these are in, which keep the store for the whole shed. */
    public EngineShedBlockEntity keeper() {
        EngineShedBlockEntity at = this;
        for (int i = 0; i < 32; i++) {
            EngineShedBlockEntity next = at.beside(-1);
            if (next == null || next == this) {
                break;
            }
            at = next;
        }
        return at;
    }

    /** How many roads the shed these doors are in has (sets of doors side by side). */
    public int roads() {
        EngineShedBlockEntity at = keeper();
        int roads = 1;
        for (int i = 0; i < 32; i++) {
            at = at.beside(1);
            if (at == null) {
                break;
            }
            roads++;
        }
        return roads;
    }

    public IItemHandler items() {
        return sharedItems;
    }

    public IFluidHandler fluids() {
        return fluids;
    }

    public MjCapabilityHelper capabilities() {
        return capabilities;
    }

    public int fuelCount() {
        ItemStackHandler store = keeper().fuel;
        int count = 0;
        for (int i = 0; i < store.getSlots(); i++) {
            count += store.getStackInSlot(i).getCount();
        }
        return count;
    }

    public int water() {
        return keeper().water.getFluidAmount();
    }

    public int diesel() {
        return keeper().diesel.getFluidAmount();
    }

    public long storedMj() {
        return keeper().stored / MjAPI.MJ;
    }

    private static long capacityMicro() {
        return TrainsConfig.CHARGER_CAPACITY.get() * MjAPI.MJ;
    }

    /** Puts power straight in (tests, admins); returns how much went in. */
    public long addMj(long mj) {
        EngineShedBlockEntity store = keeper();
        long added = Math.max(0, Math.min(mj * MjAPI.MJ, capacityMicro() - store.stored));
        store.stored += added;
        store.setChanged();
        return added / MjAPI.MJ;
    }

    /** Whether something in a player's hand is something it would take. */
    public boolean couldTake(ItemStack stack) {
        return isFuel(stack) || FluidUtil.getFluidHandler(stack).isPresent();
    }

    /** A player hands it something: fuel goes in the store, as much as fits; a bucket of water or diesel is poured in. */
    public boolean offer(Player player, InteractionHand hand, ItemStack stack) {
        if (isFuel(stack)) {
            ItemStack left = ItemHandlerHelper.insertItem(sharedItems, stack.copy(), false);
            int taken = stack.getCount() - left.getCount();
            if (taken > 0 && !player.getAbilities().instabuild) {
                stack.shrink(taken);
            }
            player.displayClientMessage(storeLine(), true);
            return true;
        }
        if (FluidUtil.getFluidHandler(stack).isPresent() && FluidUtil.interactWithFluidHandler(player, hand, fluids)) {
            player.displayClientMessage(storeLine(), true);
            return true;
        }
        return false;
    }

    public Component storeLine() {
        int roads = roads();
        Component line = Component.translatable("message.reikotrains.shed.store", fuelCount(), water() / 1000, WATER_CAPACITY / 1000, diesel() / 1000,
                DIESEL_CAPACITY / 1000, storedMj());
        return roads > 1 ? Component.translatable("message.reikotrains.shed.roads", roads).append(" - ").append(line) : line;
    }

    /** Joined to doors on the left, it hands what's in its own store over to theirs. */
    private void handOver() {
        EngineShedBlockEntity keeper = keeper();
        if (keeper == this) {
            return;
        }
        boolean moved = false;
        for (int i = 0; i < fuel.getSlots(); i++) {
            ItemStack stack = fuel.getStackInSlot(i);
            if (!stack.isEmpty()) {
                ItemStack left = ItemHandlerHelper.insertItem(keeper.fuel, stack, false);
                if (left.getCount() != stack.getCount()) {
                    fuel.setStackInSlot(i, left);
                    moved = true;
                }
            }
        }
        if (!water.isEmpty()) {
            water.drain(keeper.water.fill(water.getFluid(), IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
            moved = true;
        }
        if (!diesel.isEmpty()) {
            diesel.drain(keeper.diesel.fill(diesel.getFluid(), IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
            moved = true;
        }
        if (stored > 0) {
            long take = Math.max(0, Math.min(stored, capacityMicro() - keeper.stored));
            keeper.stored += take;
            stored -= take;
            moved |= take > 0;
        }
        if (moved) {
            setChanged();
            keeper.setChanged();
        }
    }

    /** Taken down: the fuel in its store falls out (the water, diesel and power are lost). */
    public void spill() {
        if (level == null) {
            return;
        }
        for (int i = 0; i < fuel.getSlots(); i++) {
            ItemStack stack = fuel.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, track().getY() + 1, worldPosition.getZ() + 0.5, stack.copy());
                fuel.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // The doors

    public boolean isOpen() {
        return open;
    }

    /** Client: how far open the doors are, 0 to 1. */
    public float openness(float partialTick) {
        return Mth.lerp(partialTick, opennessO, openness);
    }

    /** A click holds the doors open, or lets them shut. */
    public void toggle(Player player) {
        held = !held;
        setChanged();
        player.displayClientMessage(Component.translatable(held ? "message.reikotrains.shed.held_open" : "message.reikotrains.shed.shut")
                .append(" - ").append(storeLine()), true);
    }

    public void redstone(BlockPos cell, boolean on) {
        if (on) {
            powered.add(cell.immutable());
        } else {
            powered.remove(cell);
        }
    }

    /** Whether a train's coming up to the doors (from either side), or standing in them: nearer the faster it's going. */
    private boolean trainNear() {
        Vec3 door = door();
        Vec3 in = inward();
        Vec3 right = right();
        for (TrainCarEntity car : Trains.loadedCars(level)) {
            if (car.isRemoved() || !car.isOnTrack()) {
                continue;
            }
            Vec3 d = car.position().subtract(door);
            if (Math.abs(d.y) > 3 || Math.abs(d.x * right.x + d.z * right.z) > 2.5) {
                continue;
            }
            double along = Math.abs(d.x * in.x + d.z * in.z);
            double speed = Math.hypot(car.getX() - car.xo, car.getZ() - car.zo);
            if (along - car.halfLength() <= 4 + 20 * speed) {
                return true;
            }
        }
        return false;
    }

    private void setOpen(boolean open) {
        this.open = open;
        if (level == null) {
            return;
        }
        Direction facing = facing();
        BlockPos track = track();
        for (int up = 0; up < 4; up++) {
            for (int across = -1; across <= 1; across++) {
                if (across == 0 && up == 0) {
                    continue;
                }
                BlockPos at = EngineShedBlock.at(track, facing, across, up);
                BlockState state = level.getBlockState(at);
                if (state.getBlock() instanceof EngineShedBlock && state.getValue(EngineShedBlock.OPEN) != open) {
                    level.setBlock(at, state.setValue(EngineShedBlock.OPEN, open), 2);
                }
            }
        }
        Vec3 c = door().add(0, 1.5, 0);
        level.playSound(null, c.x, c.y, c.z, open ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1.0F, 0.55F);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    void clientTick() {
        opennessO = openness;
        openness = Mth.clamp(openness + (open ? SWING : -SWING), 0, 1);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Looking after engines

    /** The cars standing still on the track inside the shed. */
    public List<TrainCarEntity> carsInside() {
        List<TrainCarEntity> inside = new ArrayList<>();
        Vec3 door = door();
        Vec3 in = inward();
        Vec3 right = right();
        for (TrainCarEntity car : Trains.loadedCars(level)) {
            if (car.isRemoved() || !car.isOnTrack()) {
                continue;
            }
            Vec3 d = car.position().subtract(door);
            double along = d.x * in.x + d.z * in.z;
            if (along > 0 && along <= DEPTH && Math.abs(d.x * right.x + d.z * right.z) <= 1.5 && Math.abs(d.y) < 3
                    && Math.abs(car.trainSpeed()) < 0.005) {
                inside.add(car);
            }
        }
        return inside;
    }

    /** Tops up what's standing inside, as much as it can this time round. */
    private void service() {
        EngineShedBlockEntity store = keeper();
        ItemStackHandler fuel = store.fuel;
        FluidTank water = store.water;
        FluidTank diesel = store.diesel;
        List<TrainCarEntity> inside = carsInside();
        if (serving != null && !inside.contains(serving)) {
            serving = null;
        }
        // A tank engine is its own firebox and water tank, and an express's are in its tender: each is seen to once.
        Set<FuelStore> fired = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<WaterStore> watered = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean served = false;
        boolean wantsMore = false;
        TrainCarEntity servedCar = null;
        for (TrainCarEntity car : inside) {
            boolean before = served;
            if (car instanceof ElectricLocomotive electric) {
                double give = Math.min(Math.min(TrainsConfig.CHARGER_RATE.get() * (double) PERIOD, electric.room()), store.stored / (double) MjAPI.MJ);
                if (give >= 0.001) {
                    electric.charge(give);
                    store.stored = Math.max(0, store.stored - Math.round(give * MjAPI.MJ));
                    served = true;
                }
                wantsMore |= electric.room() >= 1.0 && store.stored >= MjAPI.MJ;
            } else if (car instanceof DieselLocomotive engine) {
                FluidStack drawn = diesel.drain(DIESEL_RATE * PERIOD, IFluidHandler.FluidAction.SIMULATE);
                if (!drawn.isEmpty()) {
                    int filled = engine.tank().fill(drawn, IFluidHandler.FluidAction.EXECUTE);
                    if (filled > 0) {
                        diesel.drain(filled, IFluidHandler.FluidAction.EXECUTE);
                        served = true;
                        wantsMore |= diesel.getFluidAmount() > 0;
                    }
                }
            }
            FuelStore firebox = car instanceof Tender tender ? tender
                    : car instanceof Locomotive loco && !(car instanceof ElectricLocomotive) && !(car instanceof DieselLocomotive) ? loco.fuelStore() : null;
            if (firebox != null && fired.add(firebox)) {
                int shovelled = shovel(fuel, firebox);
                served |= shovelled > 0;
                wantsMore |= shovelled > 0 && fuelCount() > 0 && room(fuel, firebox) > 0;
            }
            WaterStore tank = car instanceof Tender tender ? tender : car instanceof SteamLocomotive steam ? steam.waterStore() : null;
            if (tank != null && WaterStore.needed() && tank.waterCapacity() > 0 && watered.add(tank)) {
                int give = (int) Math.min(Math.min(TrainsConfig.WATER_TOWER_RATE.get() * (double) PERIOD, tank.waterRoom()), water.getFluidAmount());
                if (give > 0) {
                    tank.setWater(tank.water() + give);
                    water.drain(give, IFluidHandler.FluidAction.EXECUTE);
                    served = true;
                    wantsMore |= tank.waterRoom() > 0 && water.getFluidAmount() > 0;
                }
            }
            if (served && !before && servedCar == null) {
                servedCar = car;
            }
        }
        if (served) {
            store.setChanged();
        }
        if (served && servedCar != null && (level.getGameTime() % 20) < PERIOD) {
            com.reiko.trains.train.Locomotive leader = com.reiko.trains.train.Trains.leaderOf(servedCar);
            if (leader != null) {
                com.reiko.trains.advancement.TrainAdvancements.award(leader.driver(), "shed");
            }
        }
        if (served && wantsMore && servedCar != null) {
            serving = servedCar;
            lastServed = level.getGameTime();
        }
    }

    /** How many more of the biggest lump in the store would fit in a firebox. */
    private static int room(ItemStackHandler fuel, FuelStore store) {
        for (int i = 0; i < fuel.getSlots(); i++) {
            ItemStack stack = fuel.getStackInSlot(i);
            if (!stack.isEmpty()) {
                return (int) ((store.fuelCapacity() - store.fuel()) / stack.getBurnTime(RecipeType.SMELTING));
            }
        }
        return 0;
    }

    /** Shovels a few lumps of fuel into a firebox (or tender) that has room for them. Returns how many. */
    private static int shovel(ItemStackHandler fuel, FuelStore store) {
        int shovelled = 0;
        for (int i = 0; i < fuel.getSlots() && shovelled < FUEL_ITEMS; i++) {
            ItemStack stack = fuel.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            int burn = stack.getBurnTime(RecipeType.SMELTING);
            int count = Math.min(FUEL_ITEMS - shovelled, Math.min(stack.getCount(), (int) ((store.fuelCapacity() - store.fuel()) / burn)));
            if (count > 0) {
                store.setFuel(store.fuel() + (double) burn * count);
                fuel.extractItem(i, count, false);
                shovelled += count;
            }
        }
        return shovelled;
    }

    private boolean isBusy() {
        return serving != null && level != null && level.getGameTime() - lastServed <= BUSY_TICKS;
    }

    /** Whether a shed is still topping up any car of this train (so a timetabled one waits). */
    public static boolean busyWith(Level level, List<TrainCarEntity> cars) {
        synchronized (LOADED) {
            Set<EngineShedBlockEntity> sheds = LOADED.get(level);
            if (sheds == null) {
                return false;
            }
            for (EngineShedBlockEntity shed : sheds) {
                if (!shed.isRemoved() && shed.isBusy() && cars.contains(shed.serving)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EngineShedBlockEntity shed) {
        boolean want = shed.held || !shed.powered.isEmpty() || shed.trainNear();
        if (want != shed.open) {
            shed.setOpen(want);
        }
        if ((level.getGameTime() + pos.hashCode()) % PERIOD == 0) {
            shed.service();
        }
        if ((level.getGameTime() + pos.hashCode()) % 20 == 0) {
            shed.handOver();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            synchronized (LOADED) {
                LOADED.computeIfAbsent(level, l -> Collections.newSetFromMap(new WeakHashMap<>())).add(this);
            }
        } else {
            openness = opennessO = open ? 1 : 0;
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
                Set<EngineShedBlockEntity> sheds = LOADED.get(level);
                if (sheds != null) {
                    sheds.remove(this);
                }
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Saving, syncing

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Fuel", fuel.serializeNBT(registries));
        tag.put("Water", water.writeToNBT(registries, new CompoundTag()));
        tag.put("Diesel", diesel.writeToNBT(registries, new CompoundTag()));
        tag.putLong("Stored", stored);
        tag.putBoolean("Held", held);
        tag.putBoolean("Open", open);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Fuel")) {
            fuel.deserializeNBT(registries, tag.getCompound("Fuel"));
        }
        if (tag.contains("Water")) {
            water.readFromNBT(registries, tag.getCompound("Water"));
        }
        if (tag.contains("Diesel")) {
            diesel.readFromNBT(registries, tag.getCompound("Diesel"));
        }
        stored = Math.max(0, tag.getLong("Stored"));
        held = tag.getBoolean("Held");
        open = tag.getBoolean("Open");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Open", open);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The frame and the doors swung out in front of it. */
    public AABB renderBox() {
        return new AABB(track()).inflate(3, 0, 3).expandTowards(0, 5, 0);
    }

    /** Water and diesel in, from pipes or buckets; nothing out. */
    private final class Fluids implements IFluidHandler {
        @Override
        public int getTanks() {
            return 2;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            EngineShedBlockEntity store = keeper();
            return tank == 0 ? store.water.getFluid() : store.diesel.getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank == 0 ? WATER_CAPACITY : DIESEL_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 ? water.isFluidValid(stack) : diesel.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            EngineShedBlockEntity store = keeper();
            if (store.water.isFluidValid(resource)) {
                return store.water.fill(resource, action);
            }
            return store.diesel.isFluidValid(resource) ? store.diesel.fill(resource, action) : 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }

    /** What engines, pipes and gauges talk to, as for a charging point. */
    private final class Receiver implements IMjRedstoneReceiver, IMjReadable {
        @Override
        public boolean canConnect(IMjConnector other) {
            return true;
        }

        @Override
        public long getPowerRequested() {
            return Math.max(0, capacityMicro() - keeper().stored);
        }

        @Override
        public long receivePower(long microJoules, boolean simulate) {
            EngineShedBlockEntity store = keeper();
            long taken = Math.max(0, Math.min(microJoules, capacityMicro() - store.stored));
            if (!simulate && taken > 0) {
                store.stored += taken;
                store.setChanged();
            }
            return microJoules - taken;
        }

        @Override
        public long getStored() {
            return keeper().stored;
        }

        @Override
        public long getCapacity() {
            return capacityMicro();
        }
    }
}
