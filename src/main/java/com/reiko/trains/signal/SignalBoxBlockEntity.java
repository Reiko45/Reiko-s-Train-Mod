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
 * From the screen they hold signals at red (and let them go) and throw switches.
 */
public class SignalBoxBlockEntity extends BlockEntity {
    public static final int RANGE = 96;
    public static final int HEIGHT = 32;
    /** How often (ticks) it sends players at the screen what's going on, and surveys the track again. */
    private static final int STATE_PERIOD = 4;
    private static final int SURVEY_PERIOD = 100;

    public enum Done {
        HELD, RELEASED, THROWN, LOCKED, NOT_FOUND
    }

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
        for (BlockEntity be : blockEntities()) {
            if (be instanceof TrackBlockEntity track) {
                trackLines(track, lines);
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
        return new SignalBoxMapPayload(worldPosition, lines, stations);
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
                        : signal.showsCaution() ? SignalBoxStatePayload.YELLOW : SignalBoxStatePayload.GREEN;
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
                switchMarks.add(new SignalBoxStatePayload.SwitchMark(pos, x(node), z(node), points, ways.indexOf(set), stand.isLocked()));
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
                tables.add(new SignalBoxStatePayload.TableMark(x(c), z(c), table.angleAt(level.getGameTime())));
            }
        }
        return new SignalBoxStatePayload(worldPosition, signalMarks, switchMarks, cars, tables);
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
        if (stand.isLocked()) {
            return Done.LOCKED;
        }
        int way = stand.next(stand.setting());
        stand.set(way);
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, 0.5F);
        level.playSound(null, stand.node(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.4F, 1.4F);
        return Done.THROWN;
    }

    /** Taken away: it lets go of the signals it was holding (any that aren't loaded let go when they next look). */
    public void releaseAll() {
        for (BlockEntity be : blockEntities()) {
            if (be instanceof SignalBlockEntity signal && worldPosition.equals(signal.heldBy())) {
                signal.hold(null);
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, SignalBoxBlockEntity box) {
        if (!(level instanceof ServerLevel server)) {
            return;
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

    /** For tests: the lines surveyed. */
    public List<float[]> lines() {
        return lines;
    }

    public List<SignalBoxMapPayload.Label> stations() {
        return stations;
    }
}
