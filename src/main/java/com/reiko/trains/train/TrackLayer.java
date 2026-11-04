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
 * fill dips with, and bricks for bridges) and drive towards the end of the line: it lays track ahead of itself, a block
 * at a time, and holds the train to walking pace while it works. With the driver steering left or right it lays a
 * smooth 45° bend that way instead (one bend for each press; tight or wide, as set in its screen). It follows the
 * ground up and down at no more than 1 in 8 (or, set to, keeps the line level). Hills in the way are dug through, three
 * blocks wide: an open cutting where they're low, a tunnel where they're tall (its roof made good where it would fall
 * in); what it digs goes in its store. Dips are filled in, three blocks wide; gaps too deep to fill, and water, are
 * bridged, on piers. Off the end of street track it lays street track (with street track aboard). With masts and
 * overhead wire aboard, it wires the line as it goes. It stops at water in its way, lava and bedrock, and when it runs
 * out of track or fill. A switch in its screen turns it off.
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
    /** ... or 1 in 8 bends: a 45° arc about twenty blocks across. */
    static final double WIDE_BEND_CHORD = 15.2;
    /** How long a slope is, for each block it climbs or falls: 1 in 8. */
    public static final int SLOPE_LENGTH = 8;
    /** How far apart a bridge's piers are, along the line. */
    public static final int PIER_SPACING = 4;
    /** How deep a pier can go. */
    public static final int MAX_PIER = 48;
    /** How much ground over a cutting it digs off, to leave it open to the sky; more, and it's a tunnel. */
    static final int OPEN_CUTTING = 2;
    /** How high it digs with masts and wire aboard, for the wire. */
    public static final int WIRED_CLEAR_HEIGHT = 6;
    /** How far apart it puts masts up. */
    public static final double MAST_SPACING = 16;
    /** What it fills dips with. */
    public static final TagKey<Item> TRACKBED = ItemTags.create(ReikoTrains.id("trackbed"));
    /** What it builds bridges and lines tunnels with, before plain fill. */
    public static final TagKey<Item> MASONRY = ItemTags.create(ReikoTrains.id("masonry"));
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
    /** How many pieces of track it has laid, blocks of bridge and tunnel lining it's built, masts it's put up (for the tests). */
    private int laid;
    private int bridged;
    private int lined;
    private int masts;
    /** Whether it follows the ground up and down (or keeps the line level), and how wide its bends are. */
    private boolean followGround = true;
    private boolean wideBends;
    /** The last mast it put up (or found by the line), and how far it's laid since. */
    @Nullable
    private BlockPos lastMast;
    private double sinceMast;

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

    public int bridged() {
        return bridged;
    }

    public int lined() {
        return lined;
    }

    public int masts() {
        return masts;
    }

    public boolean followsGround() {
        return followGround;
    }

    public void setFollowGround(boolean followGround) {
        this.followGround = followGround;
    }

    public boolean wideBends() {
        return wideBends;
    }

    public void setWideBends(boolean wideBends) {
        this.wideBends = wideBends;
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
     * straight on (or, following the ground, a slope of 1 in 8 up or down), or a 45° bend if the driver's steering.
     * Street track goes on as street track, set into the ground (or, with only train track aboard, runs on into train
     * track). Returns how it went, or null if there's nothing it can lay onto there (a turntable).
     */
    @Nullable
    Status layFrom(BlockPos node, int side) {
        Level level = level();
        TrackAxis axis = TrackGraph.axis(level, node);
        if (axis == null || !(level.getBlockState(node).getBlock() instanceof TrackBlock)) {
            return null;
        }
        boolean fromStreet = TrackGraph.street(level, node);
        Predicate<ItemStack> isStreetTrack = stack -> stack.is(ModItems.STREET_TRACK.get());
        // Off the end of street track: more street track, or (with none) train track a block up, on the ground beside it.
        boolean street = fromStreet && count(isStreetTrack) > 0;
        boolean ontoTrain = fromStreet && !street;
        Predicate<ItemStack> isTrack = street ? isStreetTrack : stack -> stack.is(ModItems.TRACK.get());
        boolean wired = count(stack -> stack.is(ModItems.CATENARY_MAST.get())) > 0 && count(stack -> stack.is(ModItems.OVERHEAD_WIRE.get())) > 0;
        int clear = wired ? WIRED_CLEAR_HEIGHT : CLEAR_HEIGHT;
        Vec3 out = axis.out(side);
        int steer = ontoTrain ? 0 : steering();
        BlockPos target;
        TrackAxis endAxis;
        int endSide;
        Vec3 endDir;
        if (steer == 0) {
            int rise = ontoTrain ? 1 : street || !followGround ? 0 : slopeAhead(level, node, axis, side);
            int length = rise == 0 || ontoTrain ? 1 : SLOPE_LENGTH;
            target = node.offset(side * axis.dx * length, rise, side * axis.dz * length);
            endAxis = axis;
            endSide = -side;
            endDir = out;
        } else {
            // Positive angles turn clockwise looking down (x east, z south): to the right.
            double chord = wideBends ? WIDE_BEND_CHORD : BEND_CHORD;
            double angle = Math.atan2(out.z, out.x) + steer * Math.PI / 8;
            target = node.offset((int) Math.round(chord * Math.cos(angle)), 0, (int) Math.round(chord * Math.sin(angle)));
            Vec3 c = new Vec3(target.getX() - node.getX(), 0, target.getZ() - node.getZ()).normalize();
            endDir = TrackAxis.snap(c.scale(2 * out.dot(c)).subtract(out));
            endAxis = TrackAxis.nearest(endDir);
            endSide = -endAxis.sideToward(endDir);
        }
        int reach = steer == 0 && target.distManhattan(node) <= 2 ? 8 : 20;
        if (!level.isLoaded(target) || !level.isLoaded(node.offset(-reach, 0, -reach)) || !level.isLoaded(node.offset(reach, 0, reach))) {
            return null;
        }
        boolean single = steer == 0 && (ontoTrain || target.getY() == node.getY() && target.distManhattan(node) <= 2);
        TrackEdge shape = TrackEdge.between(node, axis, side, target, endAxis, endSide, fromStreet, street);
        int cost = single ? 1 : TrackBuilder.cost(shape) + 1;
        if (count(isTrack) < cost) {
            return Status.OUT_OF_TRACK;
        }
        if (TrackGraph.axis(level, target) != null) {
            return Status.IN_THE_WAY;
        }

        // Work out the earthworks before touching anything: the cutting (or tunnel), the fill or the bridge under the
        // line, and the walls that keep out water.
        Works works = new Works();
        Status problem = survey(level, node, target, endDir, shape, street, clear, works);
        if (problem != null) {
            return problem;
        }
        Predicate<ItemStack> isFill = stack -> stack.is(TRACKBED) && stack.getItem() instanceof BlockItem;
        Predicate<ItemStack> isMasonry = stack -> stack.is(MASONRY) && stack.getItem() instanceof BlockItem;
        int fillNeeded = works.fill.size();
        int buildNeeded = works.lining.size() + works.bridge.size();
        int masonry = count(isMasonry);
        if (count(isFill) < fillNeeded + Math.max(0, buildNeeded - masonry)) {
            return Status.NEEDS_FILL;
        }

        // Walls and roofs first (so nothing falls or flows in), then the digging from the top down, then the fill and
        // the bridge from the bottom up.
        for (BlockPos pos : Works.sorted(works.lining, true)) {
            dig(pos);
            place(pos, take(count(isMasonry) > 0 ? isMasonry : isFill));
            lined++;
        }
        for (BlockPos pos : Works.sorted(works.dig, true)) {
            dig(pos);
        }
        for (BlockPos pos : Works.sorted(works.fill, false)) {
            place(pos, take(isFill));
        }
        for (BlockPos pos : Works.sorted(works.bridge, false)) {
            place(pos, take(count(isMasonry) > 0 ? isMasonry : isFill));
            bridged++;
        }

        // And the track.
        if (single) {
            BlockState track = (street ? ModBlocks.STREET_TRACK.get() : ModBlocks.TRACK.get()).defaultBlockState().setValue(TrackBlock.AXIS, axis);
            if (street) {
                dig(target);
            }
            if (!(street || level.getBlockState(target).canBeReplaced()) || !track.canSurvive(level, target)) {
                return Status.IN_THE_WAY;
            }
            level.setBlock(target, track, 3);
            consume(isTrack, 1);
            level.playSound(null, target, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
        } else {
            TrackBuilder.Plan plan = TrackBuilder.plan(level, node, target, endDir, true, street);
            if (!plan.ok() || !TrackBuilder.build(level, plan, null)) {
                return Status.IN_THE_WAY;
            }
            consume(isTrack, plan.cost());
            if (steer != 0) {
                // One bend for each press: then straight on again.
                Locomotive loco = Trains.leaderOf(this);
                if (loco != null) {
                    loco.setSteer(0);
                }
            }
        }
        laid++;
        if (wired) {
            putUpWire(level, node, target, endDir, street, shape.length());
        }
        return Status.LAYING;
    }

    /** What has to be done to the ground for a piece of track. */
    private static final class Works {
        /** Blocks to dig out. */
        final Set<BlockPos> dig = new LinkedHashSet<>();
        /** Embankment: dips under the line filled in. */
        final Set<BlockPos> fill = new LinkedHashSet<>();
        /** A bridge's deck and piers, where the ground's too far down (or under water) to fill. */
        final Set<BlockPos> bridge = new LinkedHashSet<>();
        /** A tunnel's roof where it would fall in, and walls where water would get in. */
        final Set<BlockPos> lining = new LinkedHashSet<>();

        void add(Set<BlockPos> set, BlockPos pos) {
            set.add(pos.immutable());
        }

        /** Highest first (to dig), or lowest first (to build). */
        static List<BlockPos> sorted(Set<BlockPos> set, boolean down) {
            List<BlockPos> list = new ArrayList<>(set);
            list.sort((a, b) -> down ? Integer.compare(b.getY(), a.getY()) : Integer.compare(a.getY(), b.getY()));
            return list;
        }
    }

    /** A column of the ground the line passes over: the lowest block that has to be clear there, and whether the track's right over it. */
    private static final class Column {
        final int x;
        final int z;
        int base;
        boolean core;
        boolean pier;

        Column(int x, int z, int base) {
            this.x = x;
            this.z = z;
            this.base = base;
        }
    }

    /**
     * Works out what has to be dug, filled and built for track along {@code shape}, three blocks wide, into
     * {@code works}. Returns why it can't be laid, or null if it can.
     */
    @Nullable
    private Status survey(Level level, BlockPos node, BlockPos target, Vec3 ahead, TrackEdge shape, boolean street, int clear, Works works) {
        double railHeight = street ? TrackEdge.STREET_RAIL_HEIGHT : TrackEdge.RAIL_HEIGHT;
        Map<Long, Column> columns = new LinkedHashMap<>();
        for (double d = 0.75; d <= shape.length() + 1.0E-6; d += 0.25) {
            double at = Math.min(d, shape.length());
            Vec3 p = shape.point(at);
            Vec3 t = shape.tangent(at);
            // The ground comes up to the bottom of the sleepers, as near as whole blocks allow (on a slope).
            int base = (int) Math.round(p.y - railHeight) + (street ? 1 : 0);
            int x0 = (int) Math.floor(p.x - CLEAR_RADIUS);
            int z0 = (int) Math.floor(p.z - CLEAR_RADIUS);
            for (int x = x0; x <= x0 + 3; x++) {
                for (int z = z0; z <= z0 + 3; z++) {
                    double ex = x + 0.5 - p.x;
                    double ez = z + 0.5 - p.z;
                    double distance = Math.sqrt(ex * ex + ez * ez);
                    if (distance > CLEAR_RADIUS || x == node.getX() && z == node.getZ()) {
                        continue;
                    }
                    final int cx = x;
                    final int cz = z;
                    Column column = columns.computeIfAbsent(BlockPos.asLong(x, 0, z), k -> new Column(cx, cz, base));
                    column.base = Math.max(column.base, base);
                    if (distance < CORE_RADIUS) {
                        column.core = true;
                        int along = Math.abs(t.x) >= Math.abs(t.z) ? x : z;
                        column.pier |= Math.floorMod(along, PIER_SPACING) == 0;
                    }
                }
            }
        }
        // Only as far as the end of this piece: the ground beyond it is the next piece's (dug, it might let water in).
        columns.values().removeIf(c -> (c.x - target.getX()) * ahead.x + (c.z - target.getZ()) * ahead.z >= 0.5);
        for (Column column : columns.values()) {
            Status problem = surveyColumn(level, target, ahead, street, clear, column, columns, works);
            if (problem != null) {
                return problem;
            }
        }
        return null;
    }

    @Nullable
    private Status surveyColumn(Level level, BlockPos target, Vec3 ahead, boolean street, int clear, Column column, Map<Long, Column> columns,
            Works works) {
        int x = column.x;
        int z = column.z;
        int base = column.base;
        // The cutting (or the bore of a tunnel).
        for (int up = 0; up < clear; up++) {
            BlockPos pos = new BlockPos(x, base + up, z);
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) {
                return Status.WATER;
            }
            // Water beside it is walled off; water just ahead stops it here.
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos beside = pos.relative(d);
                Column other = columns.get(BlockPos.asLong(beside.getX(), 0, beside.getZ()));
                boolean inside = other != null && beside.getY() >= other.base && beside.getY() < other.base + clear;
                double forward = (beside.getX() - target.getX()) * ahead.x + (beside.getZ() - target.getZ()) * ahead.z;
                if (!inside && !level.getBlockState(beside).getFluidState().isEmpty()) {
                    if (forward >= 0.5) {
                        return Status.WATER; // digging this would let it in, over the end of the track
                    }
                    if (breakable(level, beside)) {
                        works.add(works.lining, beside);
                    }
                }
            }
            if (state.isAir()) {
                continue;
            }
            if (!breakable(level, pos)) {
                if (column.core && up == 0) {
                    return Status.IN_THE_WAY;
                }
                continue; // beside the line, or over it: left be
            }
            works.add(works.dig, pos);
        }
        // Over it: a little ground is dug off, for an open cutting; under a hill, it's a tunnel, its roof left be (but made
        // good where it would fall in or let water in).
        int top = base + clear;
        int cover = 0;
        while (cover <= OPEN_CUTTING && cover(level.getBlockState(new BlockPos(x, top + cover, z)))) {
            cover++;
        }
        if (cover <= OPEN_CUTTING) {
            for (int k = 0; k < cover; k++) {
                BlockPos pos = new BlockPos(x, top + k, z);
                if (breakable(level, pos)) {
                    works.add(works.dig, pos);
                }
            }
            // Sand and gravel over the cutting would fall in on the track: that comes out too.
            for (BlockPos up = new BlockPos(x, top + cover, z); up.getY() < top + cover + 16; up = up.above()) {
                BlockState state = level.getBlockState(up);
                if (!(state.getBlock() instanceof FallingBlock) || !breakable(level, up)) {
                    break;
                }
                works.add(works.dig, up);
            }
        } else {
            BlockPos roof = new BlockPos(x, top, z);
            BlockState state = level.getBlockState(roof);
            if ((state.getBlock() instanceof FallingBlock || !state.getFluidState().isEmpty()) && breakable(level, roof)) {
                works.add(works.lining, roof);
            }
        }
        // Under it: the bed, filled in down to firm ground; or, where that's too far down or under water, a bridge.
        boolean streetEnd = street && x == target.getX() && z == target.getZ();
        BlockPos bed = streetEnd ? target.below() : new BlockPos(x, base - 1, z);
        if (streetEnd && level.getBlockState(target).canBeReplaced() && level.getBlockState(target).getFluidState().isEmpty()) {
            works.add(works.fill, target); // street track's set into the road: there has to be road there
        }
        List<BlockPos> soft = new ArrayList<>();
        int depth = -1;
        for (int down = 0; down <= MAX_FILL; down++) {
            BlockPos pos = bed.below(down);
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) {
                break;
            }
            if (firm(level, pos, state)) {
                depth = down;
                break;
            }
            if (!state.isAir() && !state.canBeReplaced()) {
                if (!breakable(level, pos)) {
                    return column.core ? Status.IN_THE_WAY : null;
                }
                soft.add(pos);
            }
        }
        if (depth >= 0) {
            for (BlockPos pos : soft) {
                works.add(works.dig, pos);
            }
            for (int down = depth - 1; down >= 0; down--) {
                works.add(works.fill, bed.below(down));
            }
            return null;
        }
        // A bridge: its deck under the line, and every few blocks a pier under the track, down to firm ground.
        if (level.getBlockState(bed).getFluidState().isEmpty() && !level.getBlockState(bed).canBeReplaced()) {
            if (!breakable(level, bed)) {
                return column.core ? Status.IN_THE_WAY : null;
            }
            works.add(works.dig, bed);
        }
        works.add(works.bridge, bed);
        if (column.core && column.pier) {
            for (int down = 1; ; down++) {
                if (down > MAX_PIER) {
                    return Status.TOO_DEEP;
                }
                BlockPos pos = bed.below(down);
                BlockState state = level.getBlockState(pos);
                if (state.getFluidState().isEmpty() && firm(level, pos, state)) {
                    break;
                }
                if (pos.getY() <= level.getMinBuildHeight()) {
                    return Status.TOO_DEEP;
                }
                if (state.getFluidState().isEmpty() && !state.isAir() && !state.canBeReplaced()) {
                    if (!breakable(level, pos)) {
                        return Status.IN_THE_WAY;
                    }
                    works.add(works.dig, pos);
                }
                works.add(works.bridge, pos);
            }
        }
        return null;
    }

    /** Whether it could dig this block out. */
    private static boolean breakable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.hasBlockEntity() && state.getDestroySpeed(level, pos) >= 0;
    }

    /** Whether this is hill over a cutting (rather than the air, or trees, above it). */
    private static boolean cover(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced() && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)
                && !state.is(net.minecraft.world.level.block.Blocks.BARRIER);
    }

    /** Whether this is the ground (for following it): firm, and not a tree or water. */
    private static boolean ground(Level level, BlockPos pos, BlockState state) {
        return state.getFluidState().isEmpty() && cover(state) && firm(level, pos, state);
    }

    /**
     * Which way the ground goes for the next stretch straight on: +1 if it rises at least a block for the last few
     * blocks of a slope's length, -1 if it falls (a dip, not a gap too deep to fill, or water), 0 to stay level.
     */
    private static int slopeAhead(Level level, BlockPos node, TrackAxis axis, int side) {
        int y = node.getY();
        int up = 0;
        int down = 0;
        int checked = 0;
        for (int k = 1; k <= SLOPE_LENGTH; k++) {
            int x = node.getX() + side * axis.dx * k;
            int z = node.getZ() + side * axis.dz * k;
            Integer ground = null;
            for (int yy = y + 6; yy >= y - MAX_FILL; yy--) {
                BlockPos pos = new BlockPos(x, yy, z);
                BlockState state = level.getBlockState(pos);
                if (!state.getFluidState().isEmpty()) {
                    return 0; // water: stays level, to bridge it
                }
                if (ground(level, pos, state)) {
                    ground = yy + 1;
                    break;
                }
            }
            if (ground == null) {
                return 0; // too far down: a gap to bridge
            }
            if (k > SLOPE_LENGTH - 3) {
                checked++;
                up += ground > y ? 1 : 0;
                down += ground < y ? 1 : 0;
            }
        }
        return up == checked ? 1 : down == checked ? -1 : 0;
    }

    /** Sets a block from the store where the line needs one. */
    private void place(BlockPos pos, ItemStack from) {
        if (!from.isEmpty() && from.getItem() instanceof BlockItem block) {
            level().setBlock(pos, block.getBlock().defaultBlockState(), 3);
        }
    }

    /**
     * With masts and wire aboard, puts a mast up beside the line every {@link #MAST_SPACING} blocks or so (on its
     * left) and strings wire to it from the last.
     */
    private void putUpWire(Level level, BlockPos node, BlockPos target, Vec3 dir, boolean street, double length) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        com.reiko.trains.wire.OverheadLines lines = com.reiko.trains.wire.OverheadLines.get(server);
        if (lastMast == null || !lines.hasMast(lastMast)) {
            lastMast = nearestMast(level, node);
            sinceMast = lastMast == null ? 0 : Math.hypot(lastMast.getX() - node.getX(), lastMast.getZ() - node.getZ());
        }
        sinceMast += length;
        if (lastMast != null && sinceMast < MAST_SPACING) {
            return;
        }
        Vec3 left = new Vec3(dir.z, 0, -dir.x).normalize();
        int base = street ? target.getY() + 1 : target.getY();
        BlockPos at = new BlockPos(target.getX() + away(left.x, 2.5), base, target.getZ() + away(left.z, 2.5));
        // Room for it: its column, and its arm out over the track, dug out; firm ground under it.
        List<BlockPos> dig = new ArrayList<>();
        for (int up = 0; up < WIRED_CLEAR_HEIGHT; up++) {
            dig.add(at.above(up));
        }
        for (int k = 1; k <= 2; k++) {
            BlockPos arm = new BlockPos(at.getX() - away(left.x, k), base, at.getZ() - away(left.z, k));
            dig.add(arm.above(WIRED_CLEAR_HEIGHT - 2));
            dig.add(arm.above(WIRED_CLEAR_HEIGHT - 1));
        }
        for (BlockPos pos : dig) {
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty() || !state.isAir() && !breakable(level, pos)) {
                return; // no room here: it tries again further on
            }
        }
        BlockState foot = level.getBlockState(at.below());
        if (!firm(level, at.below(), foot)) {
            return;
        }
        for (BlockPos pos : dig) {
            dig(pos);
        }
        com.reiko.trains.wire.CatenaryMastBlock block = ModBlocks.CATENARY_MAST.get();
        Vec3 wire = com.reiko.trains.wire.CatenaryMastBlock.wireFor(level, at);
        if (wire == null) {
            return;
        }
        Vec3 toTrack = wire.subtract(Vec3.atCenterOf(at));
        BlockState state = block.defaultBlockState().setValue(com.reiko.trains.wire.CatenaryMastBlock.FACING,
                com.reiko.trains.signal.TrackSide.nearestDirection(new Vec3(toTrack.x, 0, toTrack.z)));
        level.setBlock(at, state, 3);
        block.setPlacedBy(level, at, state, null, ItemStack.EMPTY);
        consume(stack -> stack.is(ModItems.CATENARY_MAST.get()), 1);
        masts++;
        level.playSound(null, at, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (lastMast != null && lines.cantConnect(lastMast, at) == null && count(stack -> stack.is(ModItems.OVERHEAD_WIRE.get())) > 0
                && com.reiko.trains.wire.OverheadWireItem.blockedAt(level, lines.wire(lastMast), lines.wire(at)) == null) {
            lines.connect(lastMast, at);
            consume(stack -> stack.is(ModItems.OVERHEAD_WIRE.get()), 1);
            com.reiko.trains.wire.CatenaryMastBlockEntity.refresh(level, lastMast);
            com.reiko.trains.wire.CatenaryMastBlockEntity.refresh(level, at);
            level.playSound(null, at, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8F, 1.2F);
        }
        lastMast = at.immutable();
        sinceMast = 0;
    }

    /** {@code v} times {@code scale}, rounded away from zero at a half (the same either side of the line). */
    private static int away(double v, double scale) {
        return (int) Math.signum(v) * (int) Math.round(Math.abs(v) * scale);
    }

    /** A mast near the end of the line, if there is one (to go on wiring an old line from). */
    @Nullable
    private static BlockPos nearestMast(Level level, BlockPos node) {
        BlockPos best = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (BlockPos pos : BlockPos.betweenClosed(node.offset(-6, -2, -6), node.offset(6, 4, 6))) {
            if (level.getBlockState(pos).is(ModBlocks.CATENARY_MAST.get())) {
                double distance = pos.distSqr(node);
                if (distance < nearest) {
                    nearest = distance;
                    best = pos.immutable();
                }
            }
        }
        return best;
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
        tag.putBoolean("FollowGround", followGround);
        tag.putBoolean("WideBends", wideBends);
        if (lastMast != null) {
            tag.putLong("LastMast", lastMast.asLong());
            tag.putDouble("SinceMast", sinceMast);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registryAccess());
        on = !tag.contains("Laying") || tag.getBoolean("Laying");
        followGround = !tag.contains("FollowGround") || tag.getBoolean("FollowGround");
        wideBends = tag.getBoolean("WideBends");
        lastMast = tag.contains("LastMast") ? BlockPos.of(tag.getLong("LastMast")) : null;
        sinceMast = tag.getDouble("SinceMast");
    }
}
