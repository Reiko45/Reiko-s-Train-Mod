package com.reiko.trains.signal;

import com.reiko.trains.menu.SignalBoxMenu;
import com.reiko.trains.network.SignalBoxMapPayload;
import com.reiko.trains.network.SignalBoxStatePayload;
import com.reiko.trains.registry.ModBlockEntities;
import com.reiko.trains.track.EdgeRef;
import com.reiko.trains.track.StreetTrackBlock;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.track.TrackBlock;
import com.reiko.trains.track.TrackBlockEntity;
import com.reiko.trains.track.TrackEdge;
import com.reiko.trains.track.TrackGraph;
import com.reiko.trains.track.TrackPos;
import com.reiko.trains.train.TrainCarEntity;
import com.reiko.trains.train.Trains;
import com.reiko.trains.turntable.TurntableBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A signal box's workings. It looks after everything on the railway within {@link #RANGE} blocks of it (and
 * {@link #HEIGHT} up or down), in loaded chunks: it surveys the track there (every few seconds while anyone's looking),
 * and sends players at its screen the map and, five times a second, the signals, switches, trains and turntables on it.
 * From the screen they hold signals at red (and let them go), throw switches, set routes from one signal to another
 * (the switches between set and held for it until a train has gone through), and turn turntables. Track it has seen
 * stays on its map (faded) when its chunks unload. A comparator beside it reads how many trains are in its area.
 */
public class SignalBoxBlockEntity extends BlockEntity {
    public static final int RANGE = 96;
    public static final int HEIGHT = 32;
    /** How often (ticks) it sends players at the screen what's going on, and surveys the track again. */
    private static final int STATE_PERIOD = 4;
    private static final int SURVEY_PERIOD = 100;

    public enum Done {
        HELD, RELEASED, THROWN, LOCKED, NOT_FOUND, ROUTE_SET, ROUTE_CANCELLED, NO_ROUTE, TURNING, TURNTABLE_BUSY
    }

    /** A route set from the box: from a signal to the next one along, the switch stands it holds, and the track between. */
    static final class Route {
        final BlockPos entry;
        final BlockPos exit;
        final List<BlockPos> stands;
        final float[] points;
        /** Whether a train has come into it yet (once it's gone through, the route's let go). */
        boolean entered;

        Route(BlockPos entry, BlockPos exit, List<BlockPos> stands, float[] points) {
            this.entry = entry.immutable();
            this.exit = exit.immutable();
            this.stands = stands;
            this.points = points;
        }
    }

    /** How far along the track a route can reach (blocks). */
    private static final double ROUTE_REACH = 3.0 * RANGE;
    private final List<Route> routes = new ArrayList<>();
    /** The track lines it has seen, by the chunk each is in: lines in chunks not loaded now are shown faded. */
    private final java.util.Map<Long, List<float[]>> seen = new java.util.HashMap<>();
    private List<float[]> faded = List.of();
    /** How many trains there are in its area (for comparators), counted every second. */
    private int trainCount;

    private List<float[]> lines = List.of();
    private List<SignalBoxMapPayload.Label> stations = List.of();
    private final List<BlockPos> signals = new ArrayList<>();
    private final List<BlockPos> switches = new ArrayList<>();
    private final List<BlockPos> turntables = new ArrayList<>();
    private int surveyAge = SURVEY_PERIOD;

    public SignalBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SIGNAL_BOX.get(), pos, state);
    }

    /** Whether a place is within the box's reach. */
    public boolean covers(BlockPos pos) {
        int dx = pos.getX() - worldPosition.getX();
        int dz = pos.getZ() - worldPosition.getZ();
        return dx * dx + dz * dz <= RANGE * RANGE && Math.abs(pos.getY() - worldPosition.getY()) <= HEIGHT;
    }

    private boolean covers(Vec3 at) {
        double dx = at.x - (worldPosition.getX() + 0.5);
        double dz = at.z - (worldPosition.getZ() + 0.5);
        return dx * dx + dz * dz <= RANGE * RANGE && Math.abs(at.y - worldPosition.getY()) <= HEIGHT;
    }

    private float x(Vec3 at) {
        return (float) (at.x - (worldPosition.getX() + 0.5));
    }

    private float z(Vec3 at) {
        return (float) (at.z - (worldPosition.getZ() + 0.5));
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Surveying

    /** The block entities in loaded chunks within reach. */
    private List<BlockEntity> blockEntities() {
        List<BlockEntity> found = new ArrayList<>();
        if (!(level instanceof ServerLevel server)) {
            return found;
        }
        int cx = worldPosition.getX() >> 4;
        int cz = worldPosition.getZ() >> 4;
        int r = (RANGE >> 4) + 1;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                LevelChunk chunk = server.getChunkSource().getChunkNow(x, z);
                if (chunk != null) {
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (covers(be.getBlockPos())) {
                            found.add(be);
                        }
                    }
                }
            }
        }
        return found;
    }

    /** Surveys the track round the box: its lines, stations, and where the signals, switches and turntables are. */
    public void survey() {
        List<float[]> lines = new ArrayList<>();
        List<SignalBoxMapPayload.Label> stations = new ArrayList<>();
        signals.clear();
        switches.clear();
        turntables.clear();
        java.util.Map<Long, List<float[]>> fresh = new java.util.HashMap<>();
        for (BlockEntity be : blockEntities()) {
            if (be instanceof TrackBlockEntity track) {
                List<float[]> found = new ArrayList<>();
                trackLines(track, found);
                lines.addAll(found);
                fresh.computeIfAbsent(net.minecraft.world.level.ChunkPos.asLong(be.getBlockPos()), c -> new ArrayList<>()).addAll(found);
            } else if (be instanceof SignalBlockEntity signal && signal.isAttached()) {
                signals.add(signal.getBlockPos());
            } else if (be instanceof SwitchBlockEntity) {
                switches.add(be.getBlockPos());
            } else if (be instanceof TurntableBlockEntity) {
                turntables.add(be.getBlockPos());
            } else if (be instanceof StationBlockEntity station && station.isAttached()) {
                Vec3 at = TrackGraph.point(level, station.node());
                stations.add(new SignalBoxMapPayload.Label(station.name(), x(at), z(at)));
            }
        }
        this.lines = lines;
        this.stations = stations;
        // What it saw before in chunks that aren't loaded now stays, faded; loaded chunks show what's there now.
        if (level instanceof ServerLevel server) {
            seen.keySet().removeIf(chunk -> server.getChunkSource().getChunkNow(net.minecraft.world.level.ChunkPos.getX(chunk),
                    net.minecraft.world.level.ChunkPos.getZ(chunk)) != null);
            List<float[]> faded = new ArrayList<>();
            for (List<float[]> old : seen.values()) {
                faded.addAll(old);
            }
            this.faded = faded;
            seen.putAll(fresh);
            setChanged();
        }
        surveyAge = 0;
    }

    /**
     * A track block's lines: if it's the first of a straight run (the block behind it isn't track in line with it), the
     * run as one line; and the curves it draws, each as a line of points every half block.
     */
    private void trackLines(TrackBlockEntity track, List<float[]> out) {
        BlockState state = track.getBlockState();
        if (!(state.getBlock() instanceof TrackBlock)) {
            return;
        }
        BlockPos pos = track.getBlockPos();
        TrackAxis axis = state.getValue(TrackBlock.AXIS);
        boolean street = state.getBlock() instanceof StreetTrackBlock;
        BlockPos behind = pos.offset(-axis.dx, 0, -axis.dz);
        boolean joinedBehind = TrackGraph.axis(level, behind) == axis && covers(behind) && level.getBlockEntity(behind) instanceof TrackBlockEntity;
        if (!joinedBehind) {
            BlockPos end = pos;
            for (int i = 0; i < 2 * RANGE + 8; i++) {
                BlockPos next = end.offset(axis.dx, 0, axis.dz);
                if (TrackGraph.axis(level, next) != axis || !covers(next) || !(level.getBlockEntity(next) instanceof TrackBlockEntity)) {
                    break;
                }
                end = next;
            }
            Vec3 a = TrackEdge.node(pos, street);
            Vec3 b = TrackEdge.node(end, TrackGraph.street(level, end));
            if (!end.equals(pos)) {
                out.add(new float[] {x(a), z(a), x(b), z(b)});
            }
        }
        for (EdgeRef edge : track.ownedEdges()) {
            TrackEdge shape = edge.geometry();
            int pieces = Math.max(1, (int) Math.ceil(shape.length() * 2));
            float[] points = new float[(pieces + 1) * 2];
            for (int i = 0; i <= pieces; i++) {
                Vec3 p = shape.point(shape.length() * i / pieces);
                points[2 * i] = x(p);
                points[2 * i + 1] = z(p);
            }
            out.add(points);
        }
    }

    public SignalBoxMapPayload mapPayload() {
        return new SignalBoxMapPayload(worldPosition, lines, stations, faded);
    }

    /** What's going on round the box now. */
    public SignalBoxStatePayload statePayload() {
        List<SignalBoxStatePayload.SignalMark> signalMarks = new ArrayList<>();
        for (BlockPos pos : signals) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof SignalBlockEntity signal && signal.isAttached()) {
                TrackAxis axis = TrackGraph.axis(level, signal.node());
                Vec3 node = TrackGraph.point(level, signal.node());
                Vec3 out = axis.out(signal.side());
                // The lamp beside the track on the signal's side of it.
                Vec3 across = new Vec3(-out.z, 0, out.x);
                Vec3 toBlock = Vec3.atCenterOf(pos).subtract(node);
                double side = toBlock.x * across.x + toBlock.z * across.z >= 0 ? 1 : -1;
                Vec3 lamp = node.add(across.scale(side * 1.6));
                BlockState state = signal.getBlockState();
                int aspect = !state.getValue(SignalBlock.CLEAR) ? SignalBoxStatePayload.RED
                        : signal.showsCaution() ? SignalBoxStatePayload.YELLOW
                        : signal.showsAdvance() ? SignalBoxStatePayload.DOUBLE_YELLOW : SignalBoxStatePayload.GREEN;
                signalMarks.add(new SignalBoxStatePayload.SignalMark(pos, x(lamp), z(lamp), x(node), z(node), (float) out.x, (float) out.z,
                        aspect, signal.heldBy() != null));
            }
        }
        List<SignalBoxStatePayload.SwitchMark> switchMarks = new ArrayList<>();
        for (BlockPos pos : switches) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof SwitchBlockEntity stand) {
                List<EdgeRef> ways = stand.ways();
                if (ways.size() < 2) {
                    continue;
                }
                TrackAxis axis = TrackGraph.axis(level, stand.node());
                EdgeRef set = stand.way(ways, axis.out(stand.side()));
                // Each way out, a few blocks along it (on along the track, past the next block), to draw.
                float[] points = new float[ways.size() * SignalBoxStatePayload.WAY_POINTS * 2];
                for (int i = 0; i < ways.size(); i++) {
                    TrackPos along = new TrackPos(ways.get(i), 0, true);
                    for (int j = 0; j < SignalBoxStatePayload.WAY_POINTS; j++) {
                        along.walk(level, 1.5, TrackPos.STRAIGHTEST);
                        Vec3 p = along.position();
                        int k = 2 * (i * SignalBoxStatePayload.WAY_POINTS + j);
                        points[k] = x(p);
                        points[k + 1] = z(p);
                    }
                }
                Vec3 node = TrackGraph.point(level, stand.node());
                switchMarks.add(new SignalBoxStatePayload.SwitchMark(pos, x(node), z(node), points, ways.indexOf(set), stand.isHeld()));
            }
        }
        List<SignalBoxStatePayload.CarMark> cars = new ArrayList<>();
        for (TrainCarEntity car : Trains.loadedCars(level)) {
            if (!car.isRemoved() && car.isOnTrack() && covers(car.position())) {
                Vec3 f = car.flatForward();
                cars.add(new SignalBoxStatePayload.CarMark(car.getId(), x(car.position()), z(car.position()), (float) f.x, (float) f.z,
                        (float) car.halfLength(), car.isLocomotive()));
            }
        }
        List<SignalBoxStatePayload.TableMark> tables = new ArrayList<>();
        for (BlockPos pos : turntables) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof TurntableBlockEntity table) {
                Vec3 c = table.centre();
                tables.add(new SignalBoxStatePayload.TableMark(pos, x(c), z(c), table.angleAt(level.getGameTime()), (float) table.radius()));
            }
        }
        List<SignalBoxStatePayload.RouteMark> routeMarks = new ArrayList<>();
        for (Route route : routes) {
            routeMarks.add(new SignalBoxStatePayload.RouteMark(route.entry, route.exit, route.points));
        }
        return new SignalBoxStatePayload(worldPosition, signalMarks, switchMarks, cars, tables, routeMarks);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Working the railway

    /** Holds a signal at red, or (if it's held) lets it go. */
    public Done toggleSignal(BlockPos pos) {
        if (!covers(pos) || !level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof SignalBlockEntity signal)) {
            return Done.NOT_FOUND;
        }
        boolean hold = signal.heldBy() == null;
        signal.hold(hold ? worldPosition : null);
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, hold ? 0.6F : 0.75F);
        return hold ? Done.HELD : Done.RELEASED;
    }

    /** Throws a switch to its next way (as clicking its stand does), unless redstone's holding it. */
    public Done throwSwitch(BlockPos pos) {
        if (!covers(pos) || !level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof SwitchBlockEntity stand)) {
            return Done.NOT_FOUND;
        }
        if (stand.isHeld()) {
            return Done.LOCKED;
        }
        int way = stand.next(stand.setting());
        stand.set(way);
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, 0.5F);
        level.playSound(null, stand.node(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.4F, 1.4F);
        return Done.THROWN;
    }

    /**
     * Sets a route from the signal at {@code entryPos} to the signal at {@code exitPos}: the next signal along the track
     * from it, the way the trains go, by any of the ways at the junctions between. The switch stands between are set to
     * send trains that way, and held there until a train has gone through (or the route's cancelled). If a route is
     * already set from that signal, it's cancelled instead.
     */
    public Done setRoute(BlockPos entryPos, BlockPos exitPos) {
        for (Route route : routes) {
            if (route.entry.equals(entryPos)) {
                release(route);
                routes.remove(route);
                setChanged();
                level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, 0.75F);
                return Done.ROUTE_CANCELLED;
            }
        }
        if (!covers(entryPos) || !covers(exitPos) || !(level.getBlockEntity(entryPos) instanceof SignalBlockEntity entry) || !entry.isAttached()
                || !(level.getBlockEntity(exitPos) instanceof SignalBlockEntity exit) || !exit.isAttached() || entryPos.equals(exitPos)) {
            return Done.NOT_FOUND;
        }
        List<EdgeRef> path = findRoute(entry, exit);
        if (path == null) {
            return Done.NO_ROUTE;
        }
        // The switches on the way: each must be (or be set to) the way the route goes.
        List<SwitchBlockEntity> stands = new ArrayList<>();
        List<EdgeRef> wanted = new ArrayList<>();
        List<List<EdgeRef>> choices = new ArrayList<>();
        for (EdgeRef edge : path) {
            List<EdgeRef> options = TrackGraph.edgesFrom(level, edge.from(), edge.fromSide());
            if (options.size() < 2) {
                continue;
            }
            SwitchBlockEntity stand = TrackSide.switchAt(level, edge.from(), edge.fromSide());
            if (stand == null) {
                continue; // no switch here: trains steer, or pick their way
            }
            if ((stand.isLocked() && !stand.sendsAlong(edge, options)) || (stand.routeBy() != null && !stand.routeBy().equals(worldPosition))) {
                return Done.LOCKED;
            }
            if (stand.routeBy() != null && !stand.sendsAlong(edge, options)) {
                return Done.LOCKED; // held for another of this box's routes, the other way
            }
            stands.add(stand);
            wanted.add(edge);
            choices.add(options);
        }
        List<BlockPos> held = new ArrayList<>();
        for (int i = 0; i < stands.size(); i++) {
            if (stands.get(i).holdForRoute(wanted.get(i), choices.get(i), worldPosition)) {
                held.add(stands.get(i).getBlockPos().immutable());
            }
        }
        List<Float> points = new ArrayList<>();
        for (EdgeRef edge : path) {
            TrackEdge shape = edge.geometry();
            int pieces = Math.max(1, (int) Math.ceil(shape.length()));
            for (int i = points.isEmpty() ? 0 : 1; i <= pieces; i++) {
                Vec3 p = shape.point(shape.length() * i / pieces);
                points.add(x(p));
                points.add(z(p));
            }
        }
        float[] line = new float[Math.min(points.size(), 4096)];
        for (int i = 0; i < line.length; i++) {
            line[i] = points.get(i);
        }
        routes.add(new Route(entryPos, exitPos, held, line));
        setChanged();
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, 0.5F);
        for (BlockPos stand : held) {
            level.playSound(null, stand, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.4F, 1.4F);
        }
        return Done.ROUTE_SET;
    }

    /**
     * The way along the track from {@code entry} (leaving its track block the way it guards) to {@code exit}, facing the
     * same way, without passing another signal: the edges in order. Null if there's none within reach.
     */
    @javax.annotation.Nullable
    private List<EdgeRef> findRoute(SignalBlockEntity entry, SignalBlockEntity exit) {
        record Step(BlockPos node, int side, List<EdgeRef> path, double length) {
        }
        java.util.ArrayDeque<Step> open = new java.util.ArrayDeque<>();
        java.util.Set<Long> seenNodes = new java.util.HashSet<>();
        open.add(new Step(entry.node(), entry.side(), List.of(), 0));
        int expansions = 0;
        while (!open.isEmpty() && expansions++ < 4000) {
            Step step = open.poll();
            for (EdgeRef edge : TrackGraph.edgesFrom(level, step.node(), step.side())) {
                double length = step.length() + edge.geometry().length();
                if (length > ROUTE_REACH) {
                    continue;
                }
                List<EdgeRef> path = new ArrayList<>(step.path());
                path.add(edge);
                int out = -edge.toSide();
                SignalBlockEntity signal = TrackSide.signal(level, edge.to(), out);
                if (signal == exit) {
                    return path;
                }
                if (signal != null || !seenNodes.add(edge.to().asLong() * 2 + (out > 0 ? 1 : 0))) {
                    continue; // another signal: the end of this one's section
                }
                open.add(new Step(edge.to(), out, path, length));
            }
        }
        return null;
    }

    /** Lets go of a route's switches. */
    private void release(Route route) {
        for (BlockPos pos : route.stands) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof SwitchBlockEntity stand) {
                stand.releaseRoute(worldPosition);
            }
        }
    }

    /** Lets go of routes a train has gone through: it came into the entry signal's section, and has left it again. */
    private void checkRoutes() {
        boolean changed = false;
        for (java.util.Iterator<Route> it = routes.iterator(); it.hasNext(); ) {
            Route route = it.next();
            if (!level.isLoaded(route.entry)) {
                continue;
            }
            if (!(level.getBlockEntity(route.entry) instanceof SignalBlockEntity entry)) {
                release(route);
                it.remove();
                changed = true;
                continue;
            }
            if (entry.isOccupied()) {
                if (!route.entered) {
                    route.entered = true;
                    changed = true;
                }
            } else if (route.entered) {
                release(route);
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            setChanged();
            sendState();
        }
    }

    /** Turns a turntable to its next track round, clockwise, as clicking it does. */
    public Done turnTurntable(BlockPos pos) {
        if (!covers(pos) || !level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof TurntableBlockEntity table)) {
            return Done.NOT_FOUND;
        }
        return table.turnNext(1) == TurntableBlockEntity.Result.TURNING ? Done.TURNING : Done.TURNTABLE_BUSY;
    }

    /** The routes set (entry and exit signals), for tests. */
    public List<BlockPos[]> routes() {
        List<BlockPos[]> out = new ArrayList<>();
        for (Route route : routes) {
            out.add(new BlockPos[] {route.entry, route.exit});
        }
        return out;
    }

    /** How many trains it counted in its area, for comparators (15 at most). */
    public int trainCount() {
        return trainCount;
    }

    private void countTrains() {
        java.util.Set<TrainCarEntity> heads = new java.util.HashSet<>();
        for (TrainCarEntity car : Trains.loadedCars(level)) {
            if (!car.isRemoved() && car.isOnTrack() && covers(car.position())) {
                TrainCarEntity head = Trains.head(car);
                heads.add(head == null ? car : head);
            }
        }
        int count = Math.min(15, heads.size());
        if (count != trainCount) {
            trainCount = count;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    /** Taken away: it lets go of the signals and switches it was holding (any that aren't loaded let go when they next look). */
    public void releaseAll() {
        for (BlockEntity be : blockEntities()) {
            if (be instanceof SignalBlockEntity signal && worldPosition.equals(signal.heldBy())) {
                signal.hold(null);
            }
        }
        for (Route route : routes) {
            release(route);
        }
        routes.clear();
    }

    // ----------------------------------------------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, SignalBoxBlockEntity box) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        long time = level.getGameTime() + pos.hashCode();
        if (time % 10 == 0 && !box.routes.isEmpty()) {
            box.checkRoutes();
        }
        if (time % 20 == 0) {
            box.countTrains();
        }
        List<ServerPlayer> watching = new ArrayList<>();
        for (ServerPlayer player : server.players()) {
            if (player.containerMenu instanceof SignalBoxMenu menu && menu.pos().equals(pos)) {
                watching.add(player);
            }
        }
        if (watching.isEmpty()) {
            box.surveyAge = SURVEY_PERIOD;
            return;
        }
        boolean fresh = ++box.surveyAge >= SURVEY_PERIOD;
        if (fresh) {
            box.survey();
        }
        for (ServerPlayer player : watching) {
            SignalBoxMenu menu = (SignalBoxMenu) player.containerMenu;
            if (fresh || !menu.mapSent()) {
                PacketDistributor.sendToPlayer(player, box.mapPayload());
                menu.sentMap();
            }
        }
        if (fresh || level.getGameTime() % STATE_PERIOD == 0) {
            SignalBoxStatePayload payload = box.statePayload();
            for (ServerPlayer player : watching) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    /** Sends what's going on now to the players at the screen (after they've done something), not waiting for the next time. */
    public void sendState() {
        if (level instanceof ServerLevel server) {
            SignalBoxStatePayload payload = statePayload();
            for (ServerPlayer player : server.players()) {
                if (player.containerMenu instanceof SignalBoxMenu menu && menu.pos().equals(worldPosition)) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            }
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        net.minecraft.nbt.ListTag routeList = new net.minecraft.nbt.ListTag();
        for (Route route : routes) {
            net.minecraft.nbt.CompoundTag r = new net.minecraft.nbt.CompoundTag();
            r.putLong("Entry", route.entry.asLong());
            r.putLong("Exit", route.exit.asLong());
            r.putLongArray("Stands", route.stands.stream().mapToLong(BlockPos::asLong).toArray());
            r.put("Points", floats(route.points));
            r.putBoolean("Entered", route.entered);
            routeList.add(r);
        }
        tag.put("Routes", routeList);
        net.minecraft.nbt.ListTag seenList = new net.minecraft.nbt.ListTag();
        for (java.util.Map.Entry<Long, List<float[]>> chunk : seen.entrySet()) {
            net.minecraft.nbt.CompoundTag c = new net.minecraft.nbt.CompoundTag();
            c.putLong("Chunk", chunk.getKey());
            net.minecraft.nbt.ListTag lines = new net.minecraft.nbt.ListTag();
            for (float[] line : chunk.getValue()) {
                lines.add(floats(line));
            }
            c.put("Lines", lines);
            seenList.add(c);
        }
        tag.put("Seen", seenList);
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        routes.clear();
        for (net.minecraft.nbt.Tag t : tag.getList("Routes", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            net.minecraft.nbt.CompoundTag r = (net.minecraft.nbt.CompoundTag) t;
            List<BlockPos> stands = new ArrayList<>();
            for (long l : r.getLongArray("Stands")) {
                stands.add(BlockPos.of(l));
            }
            Route route = new Route(BlockPos.of(r.getLong("Entry")), BlockPos.of(r.getLong("Exit")), stands, floats(r.getIntArray("Points")));
            route.entered = r.getBoolean("Entered");
            routes.add(route);
        }
        seen.clear();
        List<float[]> faded = new ArrayList<>();
        for (net.minecraft.nbt.Tag t : tag.getList("Seen", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            net.minecraft.nbt.CompoundTag c = (net.minecraft.nbt.CompoundTag) t;
            List<float[]> lines = new ArrayList<>();
            for (net.minecraft.nbt.Tag line : c.getList("Lines", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
                lines.add(floats(((net.minecraft.nbt.IntArrayTag) line).getAsIntArray()));
            }
            seen.put(c.getLong("Chunk"), lines);
            faded.addAll(lines);
        }
        this.faded = faded;
    }

    private static net.minecraft.nbt.IntArrayTag floats(float[] values) {
        int[] bits = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            bits[i] = Float.floatToIntBits(values[i]);
        }
        return new net.minecraft.nbt.IntArrayTag(bits);
    }

    private static float[] floats(int[] bits) {
        float[] values = new float[bits.length];
        for (int i = 0; i < bits.length; i++) {
            values[i] = Float.intBitsToFloat(bits[i]);
        }
        return values;
    }

    /** For tests: the lines remembered from chunks that aren't loaded, shown faded. */
    public List<float[]> faded() {
        return faded;
    }

    /** For tests: the lines surveyed. */
    public List<float[]> lines() {
        return lines;
    }

    public List<SignalBoxMapPayload.Label> stations() {
        return stations;
    }
}
