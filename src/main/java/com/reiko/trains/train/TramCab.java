package com.reiko.trains.train;

import com.reiko.trains.registry.ModItems;
import com.reiko.trains.registry.ModSounds;
import java.util.List;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A driving section of the low-floor tram: five blocks long, white with a green band, a big rounded windscreen in its
 * nose, the floor a step above the street, and a pantograph on the roof at its inner end. One at each end of the tram,
 * noses out, so it's driven from either end; both pull. It runs from the wire, with a small battery for gaps; about
 * 50 km/h; and it keeps to street rules (see {@link StreetRunning}). Its bell is an electronic chime.
 */
public class TramCab extends ElectricLocomotive implements StreetRunning, LowFloorTram.Section {
    public static final double BATTERY_CAPACITY = 60_000;
    /** Its top speed over the shared setting: about 50 km/h at the default 0.6 blocks a tick. */
    public static final double SPEED_FACTOR = 1.157;
    private static final double SEAT_SIDE = 7.5 / 16;
    static final double SEAT_UP = 0.9;
    private static final Seat[] SEATS = {new Seat(1.85, 0, 1.0), seat(-6, -1), seat(-6, 1), seat(14, -1), seat(14, 1)};

    private boolean breakingUp;

    public TramCab(EntityType<? extends TramCab> type, Level level) {
        super(type, level);
    }

    /** A seat whose cushion is centred {@code x} pixels along the model (the nose is negative). */
    static Seat seat(int x, int side) {
        return new Seat(-(x + 1) / 16.0, side * SEAT_SIDE, SEAT_UP);
    }

    @Override
    public void markBreakingUp() {
        breakingUp = true;
    }

    @Override
    public void remove(RemovalReason reason) {
        List<TrainCarEntity> set = reason.shouldDestroy() && !breakingUp && !level().isClientSide ? LowFloorTram.sections(this) : List.of();
        super.remove(reason);
        LowFloorTram.removeOthers(this, set);
    }

    @Override
    public double halfLength() {
        return 2.5;
    }

    @Override
    public double bogieOffset() {
        return 1.25;
    }

    @Override
    public double mass() {
        return 1.3;
    }

    @Override
    protected float partHeight() {
        return 2.7F;
    }

    @Override
    public Seat[] seats() {
        return SEATS;
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
        return 0.8;
    }

    @Override
    protected double appetite() {
        return 0.3;
    }

    /** Its pantograph's on the roof over its inner end. */
    @Override
    protected double pantographForward() {
        return -22.0 / 16;
    }

    @Override
    protected Vec3 fuelInlet() {
        return point(0, 0.9, 1.0);
    }

    @Override
    protected void soundWhistle() {
        Vec3 at = point(2.3, 0, 1.0);
        level().playSound(null, at.x, at.y, at.z, ModSounds.TRAM_CHIME.get(), SoundSource.NEUTRAL, 3.0F, 1.0F);
    }

    @Override
    protected Item getDropItem() {
        return ModItems.LOW_FLOOR_TRAM.get();
    }
}
