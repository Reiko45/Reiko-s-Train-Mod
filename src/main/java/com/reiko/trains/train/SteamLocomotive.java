package com.reiko.trains.train;

import com.reiko.trains.registry.ModItems;
import com.reiko.trains.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A tank engine, two blocks wide and six long, with its coal in its own firebox. It puffs smoke and chuffs as it
 * works, four chuffs to a turn of its driving wheels, and wisps smoke standing still with its fire lit.
 */
public class SteamLocomotive extends Locomotive {
    /** The firebox holds 32 coal's worth. */
    public static final double FUEL_CAPACITY = 32 * 1600;
    /** The driver sits on the left of the cab, the fireman on the right. */
    private static final Seat[] SEATS = {new Seat(-1.5, -0.5, 1.06), new Seat(-1.5, 0.5, 1.06)};

    // client
    private double chuffDistance;
    private int idleSmoke;

    public SteamLocomotive(EntityType<? extends SteamLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    public double halfLength() {
        return 3.0;
    }

    @Override
    public double bogieOffset() {
        return 1.75;
    }

    @Override
    public double mass() {
        return 3.0;
    }

    @Override
    protected float partHeight() {
        return 3.0F;
    }

    @Override
    public Seat[] seats() {
        return SEATS;
    }

    @Override
    protected double tankCapacity() {
        return FUEL_CAPACITY;
    }

    @Override
    protected Item getDropItem() {
        return ModItems.STEAM_LOCOMOTIVE.get();
    }

    /** Whether its fire's lit. */
    public boolean isSteaming() {
        return isRunning();
    }

    /** The top of its chimney: blocks forward of its middle, and up. */
    protected Vec3 chimneyOffset() {
        return new Vec3(2.06, 0, 3.05);
    }

    /** How far it goes between chuffs, in blocks: a quarter turn of its driving wheels. */
    protected double chuffSpacing() {
        return 0.69;
    }

    protected float whistlePitch() {
        return 1.0F;
    }

    @Override
    protected void soundWhistle() {
        Vec3 chimney = chimneyOffset();
        Vec3 at = point(chimney.x - 2.7, 0, chimney.z - 0.45);
        level().playSound(null, at.x, at.y, at.z, ModSounds.STEAM_WHISTLE.get(), SoundSource.NEUTRAL, 5.0F, whistlePitch());
    }

    @Override
    protected Vec3 fuelInlet() {
        return point(-1.0, 0, 1.2);
    }

    /** Smoke from the chimney: a puff (and a chuff) every so often while it works, a wisp while it stands. */
    @Override
    protected void clientEffects() {
        if (!isRunning()) {
            return;
        }
        Vec3 offset = chimneyOffset();
        Vec3 chimney = point(offset.x, 0, offset.z);
        double dx = getX() - xo;
        double dz = getZ() - zo;
        double moved = Math.sqrt(dx * dx + dz * dz);
        // On a timetable the throttle isn't notched up: smoke as if half open while it's under way.
        int throttle = isAutomatic() && moved > 0.02 ? MAX_THROTTLE / 2 : getThrottle();
        if (throttle != 0 || moved > 0.05) {
            chuffDistance += Math.max(moved, throttle != 0 ? 0.03 : 0);
            // Four chuffs a turn of the driving wheels.
            if (chuffDistance >= chuffSpacing()) {
                chuffDistance = 0;
                float load = Math.abs(throttle) / (float) MAX_THROTTLE;
                level().playLocalSound(chimney.x, chimney.y, chimney.z, ModSounds.STEAM_CHUFF.get(), SoundSource.NEUTRAL,
                        0.4F + 0.7F * load, 0.85F + random.nextFloat() * 0.2F, false);
                if (load > 0) {
                    for (int i = 0; i < 2 + Math.round(4 * load); i++) {
                        level().addParticle(ParticleTypes.LARGE_SMOKE, chimney.x + random.nextGaussian() * 0.12, chimney.y,
                                chimney.z + random.nextGaussian() * 0.12, random.nextGaussian() * 0.02, 0.16 + random.nextDouble() * 0.1,
                                random.nextGaussian() * 0.02);
                    }
                    level().addParticle(ParticleTypes.CLOUD, chimney.x, chimney.y + 0.2, chimney.z, 0, 0.1, 0);
                }
            }
        } else if (++idleSmoke >= 12) {
            idleSmoke = 0;
            level().addParticle(ParticleTypes.SMOKE, chimney.x, chimney.y, chimney.z, 0, 0.07, 0);
            if (random.nextInt(3) == 0) {
                level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, chimney.x, chimney.y, chimney.z, 0, 0.04, 0);
            }
        }
    }
}
