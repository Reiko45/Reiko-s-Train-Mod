package com.reiko.trains.train;

import com.reiko.trains.registry.ModItems;
import com.reiko.trains.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The vintage tram: a single car seven blocks long, red and cream, with an open platform and a controller at each end,
 * so it runs either way without turning: whichever end the driver gets in at leads. A trolley pole on the roof trails
 * behind it to the overhead wire, and swings round when it changes ends. It runs from the wire, with a small battery
 * to get it over junctions and short gaps; about 40 km/h; and it keeps to street rules (see {@link StreetRunning}).
 * Its bell is a foot gong.
 */
public class VintageTram extends ElectricLocomotive implements StreetRunning {
    /** What its battery holds, in MJ: enough for a couple of minutes off the wire. */
    public static final double BATTERY_CAPACITY = 60_000;
    /** Its top speed over the shared setting: about 40 km/h at the default 0.6 blocks a tick. */
    public static final double SPEED_FACTOR = 0.926;
    /** How far the trolley pole's wheel is from the middle of the car, along it, when it's up against the wire. */
    public static final double POLE_REACH = 2.53;
    private static final double SEAT_SIDE = 7.5 / 16;
    private static final double SEAT_UP = 1.25;
    private static final int[] ROWS = {-24, -8, 8, 24};
    private static final Seat[] SEATS_FRONT = seats(1);
    private static final Seat[] SEATS_BACK = seats(-1);

    /** Whether it's driven from its back platform (so its back leads). */
    private static final EntityDataAccessor<Boolean> DATA_BACK_CAB = SynchedEntityData.defineId(VintageTram.class, EntityDataSerializers.BOOLEAN);
    /** Whether its trolley pole trails off its back (true) or its front. */
    private static final EntityDataAccessor<Boolean> DATA_POLE_BACK = SynchedEntityData.defineId(VintageTram.class, EntityDataSerializers.BOOLEAN);

    // client: how far round the pole has swung, 0 (trailing off the back) to 1 (off the front), now and last tick
    private float poleTurn;
    private float poleTurnO;

    public VintageTram(EntityType<? extends VintageTram> type, Level level) {
        super(type, level);
    }

    /** The driver at the controller on the leading platform ({@code end} +1 the front), then four rows of two. */
    private static Seat[] seats(int end) {
        Seat[] seats = new Seat[1 + ROWS.length * 2];
        seats[0] = new Seat(end * 2.95, 0, 1.3);
        for (int i = 0; i < ROWS.length; i++) {
            for (int side = 0; side < 2; side++) {
                seats[1 + i * 2 + side] = new Seat(-(ROWS[i] + 1) / 16.0, (side == 0 ? -1 : 1) * SEAT_SIDE, SEAT_UP);
            }
        }
        return seats;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BACK_CAB, false);
        builder.define(DATA_POLE_BACK, true);
    }

    @Override
    public int cabSign() {
        return entityData.get(DATA_BACK_CAB) ? -1 : 1;
    }

    /** Drives it from the other end (or this one). */
    public void setBackCab(boolean back) {
        entityData.set(DATA_BACK_CAB, back);
    }

    public boolean isPoleBack() {
        return entityData.get(DATA_POLE_BACK);
    }

    /**
     * Getting in as the driver: the end you got in at leads, if it's standing still by itself (not on a timetable, and
     * not coupled to anything that would be left in front of it).
     */
    @Override
    protected InteractionResult board(Player player) {
        if (!level().isClientSide && getPassengers().isEmpty() && !autopilot().isOn() && Math.abs(trainSpeed()) < 0.01
                && Trains.train(this).size() == 1) {
            Vec3 d = player.position().subtract(position());
            Vec3 f = flatForward();
            setBackCab(d.x * f.x + d.z * f.z < 0);
        }
        return super.board(player);
    }

    @Override
    public Seat[] seats() {
        return entityData.get(DATA_BACK_CAB) ? SEATS_BACK : SEATS_FRONT;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            poleTurnO = poleTurn;
            poleTurn = Mth.clamp(poleTurn + (isPoleBack() ? -0.06F : 0.06F), 0.0F, 1.0F);
            return;
        }
        // The pole trails behind whichever way it's going (or, standing, behind the driver's end).
        double speed = trainSpeed();
        boolean back = isPoleBack();
        if (speed > 0.01) {
            back = true;
        } else if (speed < -0.01) {
            back = false;
        } else if (driver() != null && !autopilot().isOn()) {
            back = cabSign() > 0;
        }
        if (back != isPoleBack()) {
            entityData.set(DATA_POLE_BACK, back);
        }
    }

    /** The trolley pole's wheel on the wire, trailing behind. */
    @Override
    protected double pantographForward() {
        return isPoleBack() ? -POLE_REACH : POLE_REACH;
    }

    /** Client: how far round the pole is, 0 trailing off the back to 1 off the front. */
    public float poleTurn(float partialTick) {
        return Mth.lerp(partialTick, poleTurnO, poleTurn);
    }

    @Override
    public double halfLength() {
        return 3.5;
    }

    @Override
    public double bogieOffset() {
        return 2.0;
    }

    @Override
    public double mass() {
        return 1.8;
    }

    @Override
    protected float partHeight() {
        return 2.9F;
    }

    @Override
    protected double tankCapacity() {
        return BATTERY_CAPACITY;
    }

    @Override
    protected double speedFactor() {
        return SPEED_FACTOR;
    }

    @Override
    protected double strength() {
        return 1.1;
    }

    @Override
    protected double appetite() {
        return 0.35;
    }

    @Override
    protected Vec3 fuelInlet() {
        return point(0, 0.95, 1.0);
    }

    @Override
    protected void soundWhistle() {
        Vec3 at = point(cabSign() * 3.0, 0, 0.3);
        level().playSound(null, at.x, at.y, at.z, ModSounds.TRAM_BELL.get(), SoundSource.NEUTRAL, 3.0F, 1.0F);
    }

    @Override
    protected Item getDropItem() {
        return ModItems.VINTAGE_TRAM.get();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("BackCab", entityData.get(DATA_BACK_CAB));
        tag.putBoolean("PoleBack", isPoleBack());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_BACK_CAB, tag.getBoolean("BackCab"));
        entityData.set(DATA_POLE_BACK, !tag.contains("PoleBack") || tag.getBoolean("PoleBack"));
    }
}
