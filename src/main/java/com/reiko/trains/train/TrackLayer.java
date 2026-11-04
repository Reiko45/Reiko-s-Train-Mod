package com.reiko.trains.train;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.menu.TrackLayerMenu;
import com.reiko.trains.registry.ModBlocks;
import com.reiko.trains.registry.ModItems;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.track.TrackBlock;
import com.reiko.trains.track.TrackBuilder;
import com.reiko.trains.track.TrackEdge;
import com.reiko.trains.track.TrackGraph;
import com.reiko.trains.track.TrackPos;
import com.reiko.trains.track.TrackScanner;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A flat wagon that lays track. Put it at the front of a train, load it with track (and stone, dirt or gravel to
 * fill dips with) and drive towards the end of the line: it lays track ahead of itself, a block at a time, and holds
 * the train to walking pace while it works. With the driver steering left or right it lays a smooth 45° bend that
 * way instead (one bend for each press). It keeps the line level: hills in the way are dug through (what it digs goes
 * in its store) and dips are filled in under the track. It stops at water, lava and bedrock, and when it runs out of
 * track or fill. A switch in its screen turns it off.
 */
public class TrackLayer extends TrainCarEntity implements Container {
    public static final int COLUMNS = 9;
    public static final int ROWS = 3;
    public static final int SLOTS = COLUMNS * ROWS;
    /** What it holds the train to while it's laying: a fast walk (about 11 km/h). */
    public static final double LAYING_SPEED = 0.15;
    /** Ticks between pieces of track. */
    public static final int LAY_INTERVAL = 4;
    /** How far past its buffers it keeps the end of the track. */
    static final double REACH = 3.0;
    /** How high a cutting it digs, from the track up. */
    public static final int CLEAR_HEIGHT = 4;
    /** How wide a cutting: blocks with their middles this close to the track are dug out. */
    static final double CLEAR_RADIUS = 1.2;
    /** Blocks closer than this to the track are right in its way (and under it, the ones it fills). */
    static final double CORE_RADIUS = 0.75;
    /** How deep a dip it fills; anything deeper needs a bridge. */
    public static final int MAX_FILL = 6;
    /** How long a bend's chord is: a 45° arc about ten blocks across. */
    static final double BEND_CHORD = 7.6;
    /** What it fills dips with. */
    public static final TagKey<Item> TRACKBED = ItemTags.create(ReikoTrains.id("trackbed"));
    /** How many layers of rails to show on it. */
    private static final EntityDataAccessor<Integer> DATA_LOAD = SynchedEntityData.defineId(TrackLayer.class, EntityDataSerializers.INT);

    /** Why it isn't laying (or that it is), for its screen and the driver. */
    public enum Status {
        READY, LAYING, OFF, OUT_OF_TRACK, NEEDS_FILL, WATER, TOO_DEEP, IN_THE_WAY;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public boolean problem() {
            return ordinal() >= OUT_OF_TRACK.ordinal();
        }
    }

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private int usedSlots;
    /** The switch: whether it lays track at all. */
    private boolean on = true;
    /** Until when (game time) it's at work: near the end of the track, going towards it. */
    private long workingUntil = Long.MIN_VALUE;
    private int cooldown;
    private Status status = Status.READY;
    private long statusTime;
    private long warnedAt = Long.MIN_VALUE;
    /** How many pieces of track it has laid (for the tests). */
    private int laid;

    public TrackLayer(EntityType<? extends TrackLayer> type, Level level) {
        super(type, level);
    }

    @Override
    protected CarPart[] createParts() {
        CarPart[] plain = super.createParts();
        CarPart[] parts = new CarPart[plain.length];
        for (int i = 0; i < plain.length; i++) {
            parts[i] = new ContainerCarPart(this, plain[i].offset, 2.0F, partHeight());
        }
        return parts;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LOAD, 0);
    }

    @Override
    public double halfLength() {
        return 3.0;
    }

    @Override
    public double bogieOffset() {
        return 1.9;
    }

    @Override
    public double mass() {
        return 1.0 + 1.0 * usedSlots / SLOTS;
    }

    @Override
    protected float partHeight() {
        return 2.4F;
    }

    @Override
    public int renderLoad() {
        return entityData.get(DATA_LOAD);
    }

    public boolean isOn() {
        return on;
    }

    public void setOn(boolean on) {
        this.on = on;
        if (!on) {
            workingUntil = Long.MIN_VALUE;
        }
    }

    /** Whether it's at work, so the train goes no faster than {@link #LAYING_SPEED}. */
    public boolean isWorking() {
        return on && level().getGameTime() <= workingUntil;
    }

    public Status status() {
        if (!on) {
            return Status.OFF;
        }
        return level().getGameTime() - statusTime <= 40 ? status : Status.READY;
    }

    public int laid() {
        return laid;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || isRemoved()) {
            return;
        }
        if (tickCount % 10 == 0) {
            int used = 0;
            for (ItemStack stack : items) {
                if (!stack.isEmpty()) {
                    used++;
                }
            }
            usedSlots = used;
            int track = count(stack -> stack.is(ModItems.TRACK.get()));
            int load = track == 0 ? 0 : track < 64 ? 1 : track < 192 ? 2 : track < 448 ? 3 : 4;
            if (load != renderLoad()) {
                entityData.set(DATA_LOAD, load);
            }
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if (on && isOnTrack() && carriedBy() == null) {
            work();
        }
    }

    /** The end it's going towards (+1 its front, -1 its back), or 0 if it's standing still and not being pushed. */
    private int leadEnd() {
        double speed = trainSpeed();
        if (Math.abs(speed) > 1.0E-4) {
            return speed > 0 ? 1 : -1;
        }
        return level().getGameTime() - endBumpTime <= 2 ? endBumpEnd : 0;
    }

    private void work() {
        int end = leadEnd();
        if (end == 0 || Trains.coupledAt(this, end) != null) {
            return;
        }
        TrackPos bogie = end > 0 ? front : rear;
        BlockPos[] last = {null};
        int[] arrived = {0};
        TrackScanner.End found = TrackScanner.scan(level(), bogie, end, halfLength() - bogieOffset() + REACH, TrackPos.STRAIGHTEST,
                (node, side, distance) -> {
                    last[0] = node;
                    arrived[0] = side;
                    return true;
                });
        if (!found.endOfTrack() || last[0] == null) {
            return;
        }
        workingUntil = level().getGameTime() + 20;
        if (cooldown > 0) {
            return;
        }
        cooldown = LAY_INTERVAL;
        Status result = layFrom(last[0], -arrived[0]);
        if (result == null) {
            return;
        }
        setStatus(result);
        if (result.problem()) {
            warn(result);
        }
    }

    private void setStatus(Status status) {
        this.status = status;
        this.statusTime = level().getGameTime();
    }

    /** Tells the driver why it's stopped (now and then, not every tick). */
    private void warn(Status problem) {
        long now = level().getGameTime();
        if (now - warnedAt < 40) {
            return;
        }
        warnedAt = now;
        Locomotive loco = Trains.leaderOf(this);
        Player driver = loco == null ? null : loco.driver();
        if (driver != null) {
            driver.displayClientMessage(Component.translatable("message.reikotrains.track_layer." + problem.key()), true);
        }
    }

    /** Which way the train's driver is steering, -1 left, +1 right, 0 straight on. */
    private int steering() {
        Locomotive loco = Trains.leaderOf(this);
        return loco == null ? 0 : loco.getSteer();
    }

    /**
     * Lays the next piece of track out of the end of the line at {@code node}, by its side {@code side}: one block
     * straight on, or a 45° bend if the driver's steering. Returns how it went, or null if there's nothing it can
     * lay onto there (street track, a turntable).
     */
    @Nullable
    Status layFrom(BlockPos node, int side) {
        Level level = level();
        TrackAxis axis = TrackGraph.axis(level, node);
        if (axis == null || !(level.getBlockState(node).getBlock() instanceof TrackBlock) || TrackGraph.street(level, node)) {
            return null;
        }
        Vec3 out = axis.out(side);
        int steer = steering();
        BlockPos target;
        TrackAxis endAxis;
        int endSide;
        Vec3 endDir;
        if (steer == 0) {
            target = node.offset(side * axis.dx, 0, side * axis.dz);
            endAxis = axis;
            endSide = -side;
            endDir = out;
        } else {
            // Positive angles turn clockwise looking down (x east, z south): to the right.
            double angle = Math.atan2(out.z, out.x) + steer * Math.PI / 8;
            target = node.offset((int) Math.round(BEND_CHORD * Math.cos(angle)), 0, (int) Math.round(BEND_CHORD * Math.sin(angle)));
            Vec3 chord = new Vec3(target.getX() - node.getX(), 0, target.getZ() - node.getZ()).normalize();
            endDir = TrackAxis.snap(chord.scale(2 * out.dot(chord)).subtract(out));
            endAxis = TrackAxis.nearest(endDir);
            endSide = -endAxis.sideToward(endDir);
        }
        if (!level.isLoaded(target) || !level.isLoaded(node.offset(-8, 0, -8)) || !level.isLoaded(node.offset(8, 0, 8))) {
            return null;
        }
        TrackEdge shape = TrackEdge.between(node, axis, side, target, endAxis, endSide, false, false);
        int cost = steer == 0 ? 1 : TrackBuilder.cost(shape) + 1;
        Predicate<ItemStack> isTrack = stack -> stack.is(ModItems.TRACK.get());
        if (count(isTrack) < cost) {
            return Status.OUT_OF_TRACK;
        }
        if (TrackGraph.axis(level, target) != null) {
            return Status.IN_THE_WAY;
        }

        // Work out the earthworks: the cutting to dig, and the dips under the track to fill.
        int y = node.getY();
        Set<BlockPos> core = new LinkedHashSet<>();
        Set<BlockPos> cutting = new LinkedHashSet<>();
        for (double d = 0.75; d <= shape.length() + 1.0E-6; d += 0.25) {
            Vec3 p = shape.point(Math.min(d, shape.length()));
            int x0 = (int) Math.floor(p.x - CLEAR_RADIUS);
            int z0 = (int) Math.floor(p.z - CLEAR_RADIUS);
            for (int x = x0; x <= x0 + 3; x++) {
                for (int z = z0; z <= z0 + 3; z++) {
                    double ex = x + 0.5 - p.x;
                    double ez = z + 0.5 - p.z;
                    double distance = Math.sqrt(ex * ex + ez * ez);
                    if (distance <= CLEAR_RADIUS && (x != node.getX() || z != node.getZ())) {
                        for (int up = 0; up < CLEAR_HEIGHT; up++) {
                            cutting.add(new BlockPos(x, y + up, z));
                        }
                        if (distance < CORE_RADIUS) {
                            core.add(new BlockPos(x, y, z));
                        }
                    }
                }
            }
        }
        List<BlockPos> dig = new ArrayList<>();
        for (BlockPos pos : cutting) {
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) {
                return Status.WATER;
            }
            if (state.isAir()) {
                continue;
            }
            boolean inTheWay = core.contains(pos.atY(y));
            if (state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) {
                if (inTheWay) {
                    return Status.IN_THE_WAY;
                }
                continue; // beside the line: left be
            }
            dig.add(pos);
        }
        // Under the track: how deep each dip is, down to firm ground (digging out anything soft on the way).
        Map<BlockPos, Integer> fill = new LinkedHashMap<>();
        for (BlockPos top : core) {
            int depth = -1;
            for (int down = 1; down <= MAX_FILL + 1; down++) {
                BlockPos pos = top.below(down);
                BlockState state = level.getBlockState(pos);
                if (!state.getFluidState().isEmpty()) {
                    return Status.WATER;
                }
                if (firm(level, pos, state)) {
                    depth = down - 1;
                    break;
                }
                if (!state.isAir() && !state.canBeReplaced()) {
                    if (state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) {
                        return Status.IN_THE_WAY;
                    }
                    dig.add(pos);
                }
            }
            if (depth < 0) {
                return Status.TOO_DEEP;
            }
            if (depth > 0) {
                fill.put(top, depth);
            }
        }
        // Sand and gravel over the cutting would fall in on the track: that comes out too.
        List<BlockPos> above = new ArrayList<>();
        for (BlockPos pos : cutting) {
            if (pos.getY() == y + CLEAR_HEIGHT - 1) {
                for (BlockPos up = pos.above(); up.getY() < y + CLEAR_HEIGHT + 16; up = up.above()) {
                    BlockState state = level.getBlockState(up);
                    if (!(state.getBlock() instanceof FallingBlock) || state.getDestroySpeed(level, up) < 0) {
                        break;
                    }
                    above.add(up);
                }
            }
        }

        // Dig: from the top down, so nothing falls in.
        above.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
        for (BlockPos pos : above) {
            dig(pos);
        }
        dig.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
        for (BlockPos pos : dig) {
            dig(pos);
        }
        // Fill: from the bottom up.
        int needed = 0;
        for (int depth : fill.values()) {
            needed += depth;
        }
        Predicate<ItemStack> isFill = stack -> stack.is(TRACKBED) && stack.getItem() instanceof BlockItem;
        if (count(isFill) < needed) {
            return Status.NEEDS_FILL;
        }
        for (Map.Entry<BlockPos, Integer> column : fill.entrySet()) {
            for (int down = column.getValue(); down >= 1; down--) {
                ItemStack used = take(isFill);
                level.setBlock(column.getKey().below(down), ((BlockItem) used.getItem()).getBlock().defaultBlockState(), 3);
            }
        }

        // And the track.
        if (steer == 0) {
            BlockState track = ModBlocks.TRACK.get().defaultBlockState().setValue(TrackBlock.AXIS, axis);
            if (!level.getBlockState(target).canBeReplaced() || !track.canSurvive(level, target)) {
                return Status.IN_THE_WAY;
            }
            level.setBlock(target, track, 3);
            consume(isTrack, 1);
            level.playSound(null, target, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
        } else {
            TrackBuilder.Plan plan = TrackBuilder.plan(level, node, target, endDir, true);
            if (!plan.ok() || !TrackBuilder.build(level, plan, null)) {
                return Status.IN_THE_WAY;
            }
            consume(isTrack, plan.cost());
            // One bend for each press: then straight on again.
            Locomotive loco = Trains.leaderOf(this);
            if (loco != null) {
                loco.setSteer(0);
            }
        }
        laid++;
        return Status.LAYING;
    }

    /** Whether the track (or the fill) could stand on this. */
    private static boolean firm(Level level, BlockPos pos, BlockState state) {
        return !state.canBeReplaced() && !state.is(BlockTags.LEAVES) && Block.canSupportRigidBlock(level, pos);
    }

    /** Digs a block out; what it drops goes in the store, and whatever won't fit falls out beside the wagon. */
    private void dig(BlockPos pos) {
        Level level = level();
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !(level instanceof ServerLevel server)) {
            return;
        }
        List<ItemStack> drops = Block.getDrops(state, server, pos, null, this, new ItemStack(Items.DIAMOND_PICKAXE));
        level.destroyBlock(pos, false, this);
        for (ItemStack drop : drops) {
            ItemStack left = store(drop);
            if (!left.isEmpty()) {
                spawnAtLocation(left);
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // The store

    /** How many of what matches it has. */
    public int count(Predicate<ItemStack> what) {
        int total = 0;
        for (ItemStack stack : items) {
            if (!stack.isEmpty() && what.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Takes one of what matches (the last stack first), or nothing. */
    private ItemStack take(Predicate<ItemStack> what) {
        for (int i = items.size() - 1; i >= 0; i--) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty() && what.test(stack)) {
                return stack.split(1);
            }
        }
        return ItemStack.EMPTY;
    }

    private void consume(Predicate<ItemStack> what, int amount) {
        for (int i = items.size() - 1; i >= 0 && amount > 0; i--) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty() && what.test(stack)) {
                int taken = Math.min(amount, stack.getCount());
                stack.shrink(taken);
                amount -= taken;
            }
        }
    }

    /** Puts a stack in the store, onto stacks of the same first; returns what won't fit. */
    public ItemStack store(ItemStack stack) {
        ItemStack left = stack.copy();
        for (int i = 0; i < items.size() && !left.isEmpty(); i++) {
            ItemStack there = items.get(i);
            if (!there.isEmpty() && ItemStack.isSameItemSameComponents(there, left)) {
                int moved = Math.min(left.getCount(), Math.min(there.getMaxStackSize(), getMaxStackSize()) - there.getCount());
                if (moved > 0) {
                    there.grow(moved);
                    left.shrink(moved);
                }
            }
        }
        for (int i = 0; i < items.size() && !left.isEmpty(); i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, left.split(Math.min(left.getMaxStackSize(), getMaxStackSize())));
            }
        }
        return left;
    }

    @Override
    protected InteractionResult interactCar(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide) {
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new TrackLayerMenu(id, inventory, this), getDisplayName()));
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void destroy(Item dropItem) {
        if (!level().isClientSide) {
            Containers.dropContents(level(), this, this);
        }
        super.destroy(dropItem);
    }

    @Override
    protected Item getDropItem() {
        return ModItems.TRACK_LAYER.get();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Container

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ContainerHelper.removeItem(items, slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return !isRemoved() && player.distanceToSqr(this) < 64.0;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ContainerHelper.saveAllItems(tag, items, registryAccess());
        tag.putBoolean("Laying", on);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registryAccess());
        on = !tag.contains("Laying") || tag.getBoolean("Laying");
    }
}
