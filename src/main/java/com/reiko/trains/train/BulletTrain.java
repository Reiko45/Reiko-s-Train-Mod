package com.reiko.trains.train;

import com.reiko.trains.registry.ModItems;
import com.reiko.trains.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Bullet Train's driving car: nine blocks long, white with a blue stripe, with a long streamlined nose and the
 * driver's cab behind it, then seats for eight. Put one at each end of a set, noses out, with Bullet Train Coaches
 * between: driven from either end, it runs both ways without turning, and both cars pull. Like the electric it runs on
 * BuildCraft power (MJ) from its batteries, charged at a charging point; it's the fastest thing on the line, about
 * 100 km/h at the default settings.
 */
public class BulletTrain extends ElectricLocomotive {
    /** What its batteries hold, in MJ: half as much again as the electric's. */
    public static final double BATTERY_CAPACITY = 1_500_000;
    /** Its top speed over the shared setting: about 100 km/h at the default 0.6 blocks a tick. */
    public static final double SPEED_FACTOR = 2.315;
    private static final double SEAT_SIDE = 7.5 / 16;
    private static final double SEAT_UP = 1.25;
    /** The driver in the middle of the cab, then two rows of two either side of the aisle. */
    private static final Seat[] SEATS = {
            new Seat(2.0, 0, SEAT_UP),
            seat(-12, -1), seat(-12, 1), seat(6, -1), seat(6, 1), seat(24, -1), seat(24, 1), seat(42, -1), seat(42, 1)};

    public BulletTrain(EntityType<? extends BulletTrain> type, Level level) {
        super(type, level);
    }

    /** A seat whose cushion is centred {@code x} pixels along the model (the nose is negative). */
    private static Seat seat(int x, int side) {
        return new Seat(-(x + 1) / 16.0, side * SEAT_SIDE, SEAT_UP);
    }

    @Override
    public double halfLength() {
        return 4.5;
    }

    @Override
    public double bogieOffset() {
        return 3.0;
    }

    @Override
    public double mass() {
        return 3.6;
    }

    @Override
    protected float partHeight() {
        return 2.9F;
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
        return 1.7;
    }

    @Override
    protected double appetite() {
        return 1.0;
    }

    /** Its pantograph's on the roof behind the cab. */
    @Override
    protected double pantographForward() {
        return -30.0 / 16;
    }

    @Override
    protected Vec3 fuelInlet() {
        return point(0, 0.95, 1.5);
    }

    @Override
    protected void soundWhistle() {
        Vec3 at = point(-4.3, 0, 1.4);
        level().playSound(null, at.x, at.y, at.z, ModSounds.BULLET_HORN.get(), SoundSource.NEUTRAL, 5.0F, 1.0F);
    }

    @Override
    protected Item getDropItem() {
        return ModItems.BULLET_TRAIN.get();
    }
}
