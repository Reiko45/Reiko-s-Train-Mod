package com.reiko.trains.wire;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.config.TrainsConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Every overhead line in a world: the masts (and where each holds the contact wire, over the track), the spans of wire
 * strung between them, the substations feeding them, and the power each line has stored. A line is the masts wired
 * together, however long; its store holds as much as its substations can (each adds to it), and trains under any span
 * of it draw on it. Kept here rather than in the masts, so a train far from the substation still has power, and saved
 * with the world. Lines are worked out again whenever a span or mast comes or goes; a line split in two shares its
 * power out between them, by masts.
 */
public final class OverheadLines extends SavedData {
    private static final String NAME = ReikoTrains.MODID + "_overhead_lines";
    /** How high the contact wire hangs above the rails, in blocks. */
    public static final double WIRE_HEIGHT = 4.5;
    /** The longest span of wire, mast to mast, in blocks. */
    public static final double MAX_SPAN = 32.0;
    /** The most spans one mast holds (two along the line, more at a junction). */
    public static final int MAX_SPANS_PER_MAST = 4;
    /** How far from the wire, sideways, a pantograph still reaches it, in blocks. */
    public static final double REACH = 1.0;

    /** A span of wire between two masts, the lower-numbered one first. */
    public record Span(BlockPos a, BlockPos b) {
        public static Span of(BlockPos one, BlockPos other) {
            return one.asLong() < other.asLong() ? new Span(one.immutable(), other.immutable()) : new Span(other.immutable(), one.immutable());
        }

        public BlockPos other(BlockPos end) {
            return end.equals(a) ? b : a;
        }
    }

    private final Map<BlockPos, Vec3> masts = new HashMap<>();
    private final Set<Span> spans = new LinkedHashSet<>();
    /** Substations, and the mast each feeds. */
    private final Map<BlockPos, BlockPos> substations = new HashMap<>();
    /** Each line's store, in MJ, by the line's first mast (the lowest-numbered). */
    private final Map<BlockPos, Double> energy = new HashMap<>();
    // Worked out from the above:
    private final Map<BlockPos, BlockPos> lineOf = new HashMap<>();
    private final Map<BlockPos, Integer> lineSize = new HashMap<>();
    private final Map<Long, List<Span>> spansByChunk = new HashMap<>();

    private OverheadLines() {
    }

    public static OverheadLines get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(OverheadLines::new, OverheadLines::load), NAME);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Masts and spans

    /** Where the mast at {@code pos} holds the contact wire, or null if there's no mast there. */
    @Nullable
    public Vec3 wire(BlockPos pos) {
        return masts.get(pos);
    }

    public boolean hasMast(BlockPos pos) {
        return masts.containsKey(pos);
    }

    public void addMast(BlockPos pos, Vec3 wire) {
        masts.put(pos.immutable(), wire);
        changed();
    }

    /** Takes a mast down, and any spans it held. Returns those spans. */
    public List<Span> removeMast(BlockPos pos) {
        List<Span> gone = spansAt(pos);
        gone.forEach(spans::remove);
        boolean had = masts.remove(pos) != null;
        substations.values().removeIf(pos::equals);
        if (had || !gone.isEmpty()) {
            changed();
        }
        return gone;
    }

    public Collection<Span> spans() {
        return spans;
    }

    /** The spans a mast holds. */
    public List<Span> spansAt(BlockPos pos) {
        List<Span> at = new ArrayList<>();
        for (Span span : spans) {
            if (span.a().equals(pos) || span.b().equals(pos)) {
                at.add(span);
            }
        }
        return at;
    }

    public boolean connected(BlockPos one, BlockPos other) {
        return spans.contains(Span.of(one, other));
    }

    /** Why two masts can't be wired together, or null if they can. */
    @Nullable
    public String cantConnect(BlockPos one, BlockPos other) {
        Vec3 a = masts.get(one);
        Vec3 b = masts.get(other);
        if (a == null || b == null) {
            return "no_mast";
        }
        if (one.equals(other)) {
            return "same_mast";
        }
        if (connected(one, other)) {
            return "already";
        }
        if (a.distanceTo(b) > MAX_SPAN) {
            return "too_far";
        }
        if (spansAt(one).size() >= MAX_SPANS_PER_MAST || spansAt(other).size() >= MAX_SPANS_PER_MAST) {
            return "full";
        }
        return null;
    }

    /** Strings a span between two masts; false if they can't be (see {@link #cantConnect}). */
    public boolean connect(BlockPos one, BlockPos other) {
        if (cantConnect(one, other) != null) {
            return false;
        }
        spans.add(Span.of(one, other));
        changed();
        return true;
    }

    /** Takes down a span; false if there wasn't one. */
    public boolean disconnect(BlockPos one, BlockPos other) {
        if (!spans.remove(Span.of(one, other))) {
            return false;
        }
        changed();
        return true;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Substations and power

    /** Ties a substation to the mast it feeds. */
    public void attachSubstation(BlockPos substation, BlockPos mast) {
        substations.put(substation.immutable(), mast.immutable());
        changed();
    }

    public void detachSubstation(BlockPos substation) {
        if (substations.remove(substation) != null) {
            changed();
        }
    }

    /** The mast a substation feeds, or null if it isn't tied to one. */
    @Nullable
    public BlockPos fedBy(BlockPos substation) {
        return substations.get(substation);
    }

    /** The line a mast is on (its first mast), or null if there's no mast there. */
    @Nullable
    public BlockPos line(BlockPos mast) {
        return lineOf.get(mast);
    }

    /** How many masts a line has. */
    public int masts(BlockPos line) {
        return lineSize.getOrDefault(line, 0);
    }

    /** MJ a line has stored. */
    public double energy(BlockPos line) {
        return energy.getOrDefault(line, 0.0);
    }

    /** MJ a line can store: as much as its substations hold between them. */
    public double capacity(BlockPos line) {
        int feeding = 0;
        for (BlockPos mast : substations.values()) {
            if (line.equals(lineOf.get(mast))) {
                feeding++;
            }
        }
        return feeding * (double) TrainsConfig.SUBSTATION_CAPACITY.get();
    }

    /** Puts up to {@code mj} into a line's store; returns how much went in. */
    public double give(BlockPos line, double mj) {
        double taken = Math.max(0, Math.min(mj, capacity(line) - energy(line)));
        if (taken > 0) {
            energy.merge(line, taken, Double::sum);
            setDirty();
        }
        return taken;
    }

    /** Takes up to {@code mj} from a line's store; returns how much it had to give. */
    public double take(BlockPos line, double mj) {
        double taken = Math.max(0, Math.min(mj, energy(line)));
        if (taken > 0) {
            energy.put(line, energy(line) - taken);
            setDirty();
        }
        return taken;
    }

    /**
     * The line whose contact wire runs over {@code at} (a pantograph on a train there: its middle, at rail height),
     * close enough sideways for the pantograph to reach and at the right height above it; null if there's none.
     */
    @Nullable
    public BlockPos lineAbove(Vec3 at) {
        List<Span> near = spansByChunk.get(ChunkPos.asLong(((int) Math.floor(at.x)) >> 4, ((int) Math.floor(at.z)) >> 4));
        if (near == null) {
            return null;
        }
        for (Span span : near) {
            Vec3 a = masts.get(span.a());
            Vec3 b = masts.get(span.b());
            double dx = b.x - a.x;
            double dz = b.z - a.z;
            double lengthSq = dx * dx + dz * dz;
            double t = lengthSq < 1.0E-9 ? 0 : Math.max(0, Math.min(1, ((at.x - a.x) * dx + (at.z - a.z) * dz) / lengthSq));
            double side = Math.hypot(a.x + dx * t - at.x, a.z + dz * t - at.z);
            double height = a.y + (b.y - a.y) * t - at.y;
            if (side <= REACH && height > WIRE_HEIGHT - 1.5 && height < WIRE_HEIGHT + 1.5) {
                return lineOf.get(span.a());
            }
        }
        return null;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Working out the lines

    private void changed() {
        // Each old line's power, shared out among the new lines by how many of its masts each has.
        Map<BlockPos, BlockPos> oldLineOf = new HashMap<>(lineOf);
        Map<BlockPos, Integer> oldSize = new HashMap<>(lineSize);
        Map<BlockPos, Double> oldEnergy = new HashMap<>(energy);
        work();
        energy.clear();
        Map<BlockPos, Double> shares = new HashMap<>();
        for (Map.Entry<BlockPos, BlockPos> entry : lineOf.entrySet()) {
            BlockPos old = oldLineOf.get(entry.getKey());
            if (old != null && oldEnergy.containsKey(old)) {
                shares.merge(entry.getValue(), oldEnergy.get(old) / Math.max(1, oldSize.getOrDefault(old, 1)), Double::sum);
            }
        }
        for (Map.Entry<BlockPos, Double> share : shares.entrySet()) {
            double kept = Math.min(share.getValue(), capacity(share.getKey()));
            if (kept > 0) {
                energy.put(share.getKey(), kept);
            }
        }
        setDirty();
    }

    /** Finds the lines (masts wired together) and indexes the spans by the chunks they cross. */
    private void work() {
        lineOf.clear();
        lineSize.clear();
        spansByChunk.clear();
        Map<BlockPos, List<BlockPos>> next = new HashMap<>();
        for (Span span : spans) {
            next.computeIfAbsent(span.a(), k -> new ArrayList<>()).add(span.b());
            next.computeIfAbsent(span.b(), k -> new ArrayList<>()).add(span.a());
        }
        List<BlockPos> order = new ArrayList<>(masts.keySet());
        order.sort((p, q) -> Long.compare(p.asLong(), q.asLong()));
        for (BlockPos start : order) {
            if (lineOf.containsKey(start)) {
                continue;
            }
            // The lowest-numbered mast of the line comes first, so it names the line.
            List<BlockPos> line = new ArrayList<>();
            Deque<BlockPos> open = new ArrayDeque<>();
            open.add(start);
            lineOf.put(start, start);
            while (!open.isEmpty()) {
                BlockPos mast = open.poll();
                line.add(mast);
                for (BlockPos other : next.getOrDefault(mast, List.of())) {
                    if (masts.containsKey(other) && !lineOf.containsKey(other)) {
                        lineOf.put(other, start);
                        open.add(other);
                    }
                }
            }
            lineSize.put(start, line.size());
        }
        for (Span span : spans) {
            Vec3 a = masts.get(span.a());
            Vec3 b = masts.get(span.b());
            if (a == null || b == null) {
                continue;
            }
            // Every chunk the span passes over, and the ones next to it (a pantograph can be a block off it).
            int x0 = ((int) Math.floor(Math.min(a.x, b.x) - REACH)) >> 4;
            int x1 = ((int) Math.floor(Math.max(a.x, b.x) + REACH)) >> 4;
            int z0 = ((int) Math.floor(Math.min(a.z, b.z) - REACH)) >> 4;
            int z1 = ((int) Math.floor(Math.max(a.z, b.z) + REACH)) >> 4;
            for (int cx = x0; cx <= x1; cx++) {
                for (int cz = z0; cz <= z1; cz++) {
                    spansByChunk.computeIfAbsent(ChunkPos.asLong(cx, cz), k -> new ArrayList<>()).add(span);
                }
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Saving

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag mastList = new ListTag();
        for (Map.Entry<BlockPos, Vec3> mast : masts.entrySet()) {
            CompoundTag m = new CompoundTag();
            m.putLong("Pos", mast.getKey().asLong());
            m.putDouble("X", mast.getValue().x);
            m.putDouble("Y", mast.getValue().y);
            m.putDouble("Z", mast.getValue().z);
            mastList.add(m);
        }
        tag.put("Masts", mastList);
        ListTag spanList = new ListTag();
        for (Span span : spans) {
            CompoundTag s = new CompoundTag();
            s.putLong("A", span.a().asLong());
            s.putLong("B", span.b().asLong());
            spanList.add(s);
        }
        tag.put("Spans", spanList);
        ListTag feeds = new ListTag();
        for (Map.Entry<BlockPos, BlockPos> feed : substations.entrySet()) {
            CompoundTag f = new CompoundTag();
            f.putLong("Pos", feed.getKey().asLong());
            f.putLong("Mast", feed.getValue().asLong());
            feeds.add(f);
        }
        tag.put("Substations", feeds);
        ListTag stores = new ListTag();
        for (Map.Entry<BlockPos, Double> store : energy.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putLong("Line", store.getKey().asLong());
            e.putDouble("MJ", store.getValue());
            stores.add(e);
        }
        tag.put("Energy", stores);
        return tag;
    }

    public static OverheadLines load(CompoundTag tag, HolderLookup.Provider registries) {
        OverheadLines lines = new OverheadLines();
        for (Tag t : tag.getList("Masts", Tag.TAG_COMPOUND)) {
            CompoundTag m = (CompoundTag) t;
            lines.masts.put(BlockPos.of(m.getLong("Pos")), new Vec3(m.getDouble("X"), m.getDouble("Y"), m.getDouble("Z")));
        }
        for (Tag t : tag.getList("Spans", Tag.TAG_COMPOUND)) {
            CompoundTag s = (CompoundTag) t;
            lines.spans.add(Span.of(BlockPos.of(s.getLong("A")), BlockPos.of(s.getLong("B"))));
        }
        for (Tag t : tag.getList("Substations", Tag.TAG_COMPOUND)) {
            CompoundTag f = (CompoundTag) t;
            lines.substations.put(BlockPos.of(f.getLong("Pos")), BlockPos.of(f.getLong("Mast")));
        }
        lines.work();
        for (Tag t : tag.getList("Energy", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            BlockPos line = BlockPos.of(e.getLong("Line"));
            if (lines.lineOf.containsKey(line)) {
                lines.energy.put(line, e.getDouble("MJ"));
            }
        }
        return lines;
    }
}
