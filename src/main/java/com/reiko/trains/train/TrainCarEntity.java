package com.reiko.trains.train;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.item.CouplerItem;
import com.reiko.trains.track.EdgeRef;
import com.reiko.trains.track.TrackPos;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * A train car on the mod's own track. It rides on two bogies, one towards each end, each a {@link TrackPos}; the
 * car sits between them, turned and tilted to match, so a long car swings through a curve like a real one. The
 * first car of a train (the one with nothing coupled in front) moves the whole train each tick: see {@link Trains}.
 */
public abstract class TrainCarEntity extends VehicleEntity {
    /** A seat: blocks forward of the car's middle (negative is behind), to the right, and up from the rails. */
    public record Seat(double forward, double side, double up) {
    }

    /** How the bogies are turned against the body (degrees), for drawing them on curves. */
    private static final EntityDataAccessor<Float> DATA_FRONT_BOGIE = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_REAR_BOGIE = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.FLOAT);
    /** The turntable it's going round on (its pivot), if it is; how far along the bridge it is, and whether it faces its front end. */
    private static final EntityDataAccessor<java.util.Optional<BlockPos>> DATA_TURNTABLE = SynchedEntityData.defineId(TrainCarEntity.class,
            EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Float> DATA_TURN_ALONG = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_TURN_FRONT = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.BOOLEAN);

    private final CarPart[] parts;
    @Nullable
    TrackPos front;
    @Nullable
    TrackPos rear;
    /** The car coupled in front, which this one follows. */
    @Nullable
    UUID leader;
    /** The car coupled behind. */
    @Nullable
    UUID follower;
    /** Whether this car faces the other way from the car it follows. */
    boolean flipped;
    @Nullable
    Entity leaderCache;
    @Nullable
    Entity followerCache;
    /** For the first car of a train: the train's speed, blocks per tick, positive the way this car faces. */
    double speed;
    /** For the first car: which way the front of the train went at each junction, for the rest to follow. */
    final Map<Long, EdgeRef> routes = new HashMap<>();
    /** Ticks spent waiting for a coupled car that isn't loaded. */
    int waiting;

    // client
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private float lerpYRot;
    private float lerpXRot;
    private double rolled;
    private double rolledO;

    protected TrainCarEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.parts = createParts();
        // Like the ender dragon: the parts' ids follow the car's, so client and server agree on them.
        setId(ENTITY_COUNTER.getAndAdd(parts.length + 1) + 1);
        this.blocksBuilding = true;
    }

    /** A row of hitbox sections, about two blocks apart, along the car. */
    protected CarPart[] createParts() {
        int count = Math.max(1, (int) Math.round(halfLength() * 2 / 1.7));
        CarPart[] made = new CarPart[count];
        double span = halfLength() - 1.0;
        for (int i = 0; i < count; i++) {
            double offset = count == 1 ? 0 : -span + 2 * span * i / (count - 1);
            made[i] = new CarPart(this, offset, 2.0F, partHeight());
        }
        return made;
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        if (parts != null) {
            for (int i = 0; i < parts.length; i++) {
                parts[i].setId(id + i + 1);
            }
        }
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FRONT_BOGIE, 0.0F);
        builder.define(DATA_REAR_BOGIE, 0.0F);
        builder.define(DATA_TURNTABLE, java.util.Optional.empty());
        builder.define(DATA_TURN_ALONG, 0.0F);
        builder.define(DATA_TURN_FRONT, true);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // What kind of car this is

    /** From the middle of the car to the face of its buffers, in blocks. */
    public abstract double halfLength();

    /** From the middle of the car to each bogie, in blocks. */
    public abstract double bogieOffset();

    /** How heavy the car is, against an empty coach's 1.5. */
    public abstract double mass();

    /** How tall the car's hitbox is, in blocks. */
    protected abstract float partHeight();

    public Seat[] seats() {
        return new Seat[0];
    }

    /** How much of a load to draw (a wagon's crates, a tender's coal): 0 for none. */
    public int renderLoad() {
        return 0;
    }

    public boolean isLocomotive() {
        return false;
    }

    /** What clicking the car does, once the coupler's had its say. */
    protected abstract InteractionResult interactCar(Player player, InteractionHand hand);

    // ----------------------------------------------------------------------------------------------------------------
    // On the track

    public boolean isOnTrack() {
        return front != null && rear != null;
    }

    @Nullable
    public TrackPos front() {
        return front;
    }

    @Nullable
    public TrackPos rear() {
        return rear;
    }

    /** Puts the car on the track with its bogies here, and lines it up with them. */
    public void setBogies(TrackPos front, TrackPos rear) {
        this.front = front;
        this.rear = rear;
        updatePose();
        xo = xOld = getX();
        yo = yOld = getY();
        zo = zOld = getZ();
        yRotO = getYRot();
        xRotO = getXRot();
    }

    /** The way the car faces, tilted on slopes. */
    public Vec3 forward() {
        return calculateViewVector(getXRot(), getYRot());
    }

    /** The way the car faces, flat. */
    public Vec3 flatForward() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    /** Sine of the slope the car's on: positive when its front is higher. */
    public double slope() {
        return forward().y;
    }

    public UUID leaderId() {
        return leader;
    }

    public UUID followerId() {
        return follower;
    }

    public boolean isFlipped() {
        return flipped;
    }

    /** Gives the train this car is in a shove: sets it going at {@code speed} blocks per tick, the way this car faces. */
    public void shove(double speed) {
        TrainCarEntity head = Trains.head(this);
        if (head != null) {
            head.speed = speed * Trains.sign(head, this);
        }
    }

    /** The speed of the train this car is in, in blocks per tick, positive the way this car faces (server). */
    public double trainSpeed() {
        TrainCarEntity head = Trains.head(this);
        return head == null ? 0 : head.speed * Trains.sign(head, this);
    }

    /** Puts the car where its bogies are: halfway between them, facing from the back one to the front one. */
    void updatePose() {
        if (front == null || rear == null) {
            return;
        }
        Vec3 f = front.position();
        Vec3 r = rear.position();
        Vec3 d = f.subtract(r);
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        if (flat > 1.0E-6) {
            setYRot((float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG));
            setXRot((float) (-Mth.atan2(d.y, flat) * Mth.RAD_TO_DEG));
        }
        setPos((f.x + r.x) / 2, (f.y + r.y) / 2, (f.z + r.z) / 2);
        float frontYaw = bogieYaw(front.facing());
        float rearYaw = bogieYaw(rear.facing());
        if (Math.abs(frontYaw - entityData.get(DATA_FRONT_BOGIE)) > 0.5F) {
            entityData.set(DATA_FRONT_BOGIE, frontYaw);
        }
        if (Math.abs(rearYaw - entityData.get(DATA_REAR_BOGIE)) > 0.5F) {
            entityData.set(DATA_REAR_BOGIE, rearYaw);
        }
        updateParts();
    }

    /** How far (degrees) a bogie facing {@code facing} is turned from the body: positive is to the left. */
    private float bogieYaw(Vec3 facing) {
        Vec3 f = flatForward();
        double along = facing.x * f.x + facing.z * f.z;
        double right = facing.x * -f.z + facing.z * f.x;
        return (float) (Mth.atan2(-right, along) * Mth.RAD_TO_DEG);
    }

    public float frontBogieYaw() {
        return entityData.get(DATA_FRONT_BOGIE);
    }

    public float rearBogieYaw() {
        return entityData.get(DATA_REAR_BOGIE);
    }

    private void updateParts() {
        Vec3 f = forward();
        for (CarPart part : parts) {
            part.xo = part.getX();
            part.yo = part.getY();
            part.zo = part.getZ();
            part.setPos(getX() + f.x * part.offset, getY() + f.y * part.offset, getZ() + f.z * part.offset);
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Going round on a turntable

    /** The pivot of the turntable it's going round on, or null if it isn't. */
    @Nullable
    public BlockPos carriedBy() {
        return entityData.get(DATA_TURNTABLE).orElse(null);
    }

    /** Starts going round on a turntable: it stops being moved along the track, and goes where the bridge takes it. */
    public void carry(BlockPos pivot, double along, boolean front) {
        entityData.set(DATA_TURN_ALONG, (float) along);
        entityData.set(DATA_TURN_FRONT, front);
        entityData.set(DATA_TURNTABLE, java.util.Optional.of(pivot.immutable()));
        speed = 0;
    }

    /** Stops going round (on track again, or the turntable's gone). */
    public void uncarry() {
        if (carriedBy() != null) {
            entityData.set(DATA_TURNTABLE, java.util.Optional.empty());
        }
    }

    /** Back on the track after going round: on the bridge, where it's been taken, standing. */
    public void settleOn(TrackPos front, TrackPos rear) {
        uncarry();
        setBogies(front, rear);
        speed = 0;
        routes.clear();
    }

    /** Puts it where a turntable's taken it, level, facing that way. */
    public void poseCarried(Vec3 at, float yaw) {
        setPos(at.x, at.y, at.z);
        setYRot(yaw);
        setXRot(0);
        updateParts();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Ticking

    @Override
    public void tick() {
        if (getHurtTime() > 0) {
            setHurtTime(getHurtTime() - 1);
        }
        if (getDamage() > 0) {
            setDamage(getDamage() - 1);
        }
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        if (!isOnTrack()) {
            if (tickCount > 1) {
                // A car with no track under it (one from before the mod had its own track): back to an item.
                ReikoTrains.LOGGER.info("Train car {} isn't on track, dropping it as an item", getUUID());
                destroy(getDropItem());
            }
            return;
        }
        BlockPos turntable = carriedBy();
        if (turntable != null && com.reiko.trains.turntable.TurntableBlockEntity.hold(this, turntable, entityData.get(DATA_TURN_ALONG),
                entityData.get(DATA_TURN_FRONT))) {
            return;
        }
        if (leader == null) {
            Trains.tickTrain(this);
        }
        pushAside();
    }

    private void clientTick() {
        BlockPos turntable = carriedBy();
        if (turntable != null && level().getBlockEntity(turntable) instanceof com.reiko.trains.turntable.TurntableBlockEntity table) {
            // Going round: where the bridge (as this client draws it) has taken it, so it stays put on it.
            lerpSteps = 0;
            double time = level().getGameTime();
            poseCarried(table.carriedPosition(entityData.get(DATA_TURN_ALONG), time), table.carriedYaw(entityData.get(DATA_TURN_FRONT), time));
            rolledO = rolled;
            return;
        }
        if (lerpSteps > 0) {
            double x = getX() + (lerpX - getX()) / lerpSteps;
            double y = getY() + (lerpY - getY()) / lerpSteps;
            double z = getZ() + (lerpZ - getZ()) / lerpSteps;
            setYRot(getYRot() + Mth.wrapDegrees(lerpYRot - getYRot()) / lerpSteps);
            setXRot(getXRot() + (lerpXRot - getXRot()) / lerpSteps);
            lerpSteps--;
            setPos(x, y, z);
        }
        updateParts();
        double dx = getX() - xo;
        double dy = getY() - yo;
        double dz = getZ() - zo;
        double moved = Math.sqrt(dx * dx + dy * dy + dz * dz);
        Vec3 f = forward();
        rolledO = rolled;
        if (moved < 4.0) {
            rolled += dx * f.x + dy * f.y + dz * f.z >= 0 ? moved : -moved;
        }
        if (Math.abs(rolled) > 1.0E5) {
            rolled %= 1000.0;
            rolledO = rolled;
        }
    }

    /** Client: how far the car has rolled (blocks, forwards positive), for turning its wheels. */
    public float rolled(float partialTick) {
        return (float) Mth.lerp(partialTick, rolledO, rolled);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpXRot = xRot;
        lerpSteps = steps + 1;
    }

    @Override
    public double lerpTargetX() {
        return lerpSteps > 0 ? lerpX : getX();
    }

    @Override
    public double lerpTargetY() {
        return lerpSteps > 0 ? lerpY : getY();
    }

    @Override
    public double lerpTargetZ() {
        return lerpSteps > 0 ? lerpZ : getZ();
    }

    @Override
    public float lerpTargetYRot() {
        return lerpSteps > 0 ? lerpYRot : getYRot();
    }

    @Override
    public float lerpTargetXRot() {
        return lerpSteps > 0 ? lerpXRot : getXRot();
    }

    /** A moving car nudges people and animals off the track. */
    private void pushAside() {
        if (Math.abs(trainSpeedFast()) < 0.02) {
            return;
        }
        Vec3 f = flatForward();
        for (CarPart part : parts) {
            for (Entity entity : level().getEntities(this, part.getBoundingBox(), e -> e instanceof LivingEntity && !e.isPassenger()
                    && !(e instanceof PartEntity<?>))) {
                Vec3 away = entity.position().subtract(part.position());
                double side = away.x * -f.z + away.z * f.x;
                double push = side >= 0 ? 0.25 : -0.25;
                entity.push(-f.z * push, 0.05, f.x * push);
            }
        }
    }

    /** The train's speed if this is its first car, without looking anything up; zero otherwise. */
    private double trainSpeedFast() {
        if (leader == null) {
            return speed;
        }
        return leaderCache instanceof TrainCarEntity car ? car.trainSpeedFast() : 0;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(halfLength() + 0.5, 1.0, halfLength() + 0.5);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double range = 160 * getViewScale();
        return distance < range * range;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Clicking, seats

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof CouplerItem) {
            return CouplerItem.useOnCar(stack, player, this);
        }
        if (stack.getItem() instanceof com.reiko.trains.item.TimetableItem) {
            return com.reiko.trains.item.TimetableItem.useOnCar(stack, player, this);
        }
        return interactCar(player, hand);
    }

    /** Gets in, if there's a free seat. */
    protected InteractionResult board(Player player) {
        if (player.isSecondaryUseActive() || getPassengers().size() >= seats().length) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < seats().length;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        int index = getPassengers().indexOf(passenger);
        Seat[] seats = seats();
        if (index < 0 || index >= seats.length) {
            return new Vec3(0, 1, 0);
        }
        return seatOffset(seats[index]);
    }

    private Vec3 seatOffset(Seat seat) {
        Vec3 f = forward();
        Vec3 flat = flatForward();
        return new Vec3(f.x * seat.forward() - flat.z * seat.side(), f.y * seat.forward() + seat.up(),
                f.z * seat.forward() + flat.x * seat.side());
    }

    /** Passengers turn with the car, so going round a bend doesn't swing your view. */
    @Override
    protected void positionRider(Entity passenger, MoveFunction callback) {
        super.positionRider(passenger, callback);
        if (level().isClientSide && passenger instanceof Player player && player.isLocalPlayer()) {
            float turn = Mth.wrapDegrees(getYRot() - yRotO);
            player.setYRot(player.getYRot() + turn);
            player.setYHeadRot(player.getYHeadRot() + turn);
        }
    }

    /** Gets off to the side of the car, by the seat. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        int index = getPassengers().indexOf(passenger);
        Seat seat = index >= 0 && index < seats().length ? seats()[index] : new Seat(0, 0, 0);
        Vec3 flat = flatForward();
        Vec3 right = new Vec3(-flat.z, 0, flat.x);
        double first = seat.side() >= 0 ? 1 : -1;
        for (double side : new double[]{first, -first}) {
            for (double along : new double[]{0, 1, -1}) {
                Vec3 spot = position().add(flat.scale(seat.forward() + along)).add(right.scale(side * 1.7));
                for (int dy = 0; dy >= -1; dy--) {
                    BlockPos pos = BlockPos.containing(spot.x, getY() + dy + 0.1, spot.z);
                    Vec3 safe = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), pos, true);
                    if (safe != null) {
                        return safe;
                    }
                }
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Breaking, saving

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!level().isClientSide) {
            Trains.loaded(this);
        }
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        Trains.unloaded(this);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && !level().isClientSide) {
            Trains.uncouple(this);
        }
        super.remove(reason);
    }

    /** Comes off the track: the car drops as an item (and anything it carries with it). */
    public void derail() {
        if (level() instanceof ServerLevel && !isRemoved()) {
            for (Entity passenger : getPassengers()) {
                if (passenger instanceof Player player) {
                    player.displayClientMessage(Component.translatable("message.reikotrains.derailed"), true);
                }
            }
            destroy(getDropItem());
        }
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(getDropItem());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (front != null && rear != null) {
            tag.put("FrontBogie", front.save());
            tag.put("RearBogie", rear.save());
        }
        if (leader != null) {
            tag.putUUID("CoupledFront", leader);
        }
        if (follower != null) {
            tag.putUUID("CoupledBack", follower);
        }
        tag.putBoolean("Flipped", flipped);
        tag.putDouble("Speed", speed);
        BlockPos turntable = carriedBy();
        if (turntable != null) {
            tag.putLong("Turntable", turntable.asLong());
            tag.putFloat("TurnAlong", entityData.get(DATA_TURN_ALONG));
            tag.putBoolean("TurnFront", entityData.get(DATA_TURN_FRONT));
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("FrontBogie") && tag.contains("RearBogie")) {
            front = TrackPos.load(tag.getCompound("FrontBogie"));
            rear = TrackPos.load(tag.getCompound("RearBogie"));
        }
        leader = tag.hasUUID("CoupledFront") ? tag.getUUID("CoupledFront") : null;
        follower = tag.hasUUID("CoupledBack") ? tag.getUUID("CoupledBack") : null;
        flipped = tag.getBoolean("Flipped");
        speed = tag.getDouble("Speed");
        if (tag.contains("Turntable")) {
            carry(BlockPos.of(tag.getLong("Turntable")), tag.getFloat("TurnAlong"), tag.getBoolean("TurnFront"));
        }
    }
}
