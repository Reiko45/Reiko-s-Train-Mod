package com.reiko.trains.train;

import com.reiko.trains.registry.ModItems;
import com.reiko.trains.registry.ModSounds;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A tank engine, two blocks wide and six long, with its coal in its own firebox and its water in its own side tanks.
 * It uses water as fast as coal (a full load of each lasts the same), and can't work without (unless the server says
 * steam engines don't need water); fill it at a water tower, or with buckets. It puffs smoke and chuffs as it works,
 * four chuffs to a turn of its driving wheels, and wisps smoke standing still with its fire lit.
 */
public class SteamLocomotive extends Locomotive implements WaterStore {
    /** The firebox holds 32 coal's worth. */
    public static final double FUEL_CAPACITY = 32 * 1600;
    /** Its side tanks hold 16 buckets: as long as a full firebox. */
    public static final double WATER_CAPACITY = 16_000;
    /** For the gauges: how full its water is, 0-100, or -1 if it doesn't use any or has nowhere to get it. */
    private static final EntityDataAccessor<Integer> DATA_WATER = SynchedEntityData.defineId(SteamLocomotive.class, EntityDataSerializers.INT);

    /** Water in its own tanks, in millibuckets: engines come from the works with them full. */
    private double water = WATER_CAPACITY;
    /** The driver sits on the left of the cab, the fireman on the right. */
    private static final Seat[] SEATS = {new Seat(-1.5, -0.5, 1.06), new Seat(-1.5, 0.5, 1.06)};

    // client
    private double chuffDistance;
    private int idleSmoke;

    public SteamLocomotive(EntityType<? extends SteamLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_WATER, 100);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Water

    @Override
    public double water() {
        return water;
    }

    /** How much water its own tanks hold (none, for an engine whose water is in its tender). */
    @Override
    public double waterCapacity() {
        return WATER_CAPACITY;
    }

    @Override
    public void setWater(double millibuckets) {
        water = Mth.clamp(millibuckets, 0.0, waterCapacity());
    }

    /** Where its water comes from: its own tanks (an express's tender). Null if it's got nowhere. */
    @Nullable
    public WaterStore waterStore() {
        return this;
    }

    /** Whether it's run out of water (and needs it). */
    public boolean outOfWater() {
        if (!WaterStore.needed()) {
            return false;
        }
        WaterStore store = waterStore();
        return store == null || store.water() <= 0;
    }

    @Override
    public int waterPercent() {
        return entityData.get(DATA_WATER);
    }

    /** Burning coal boils water away: as much of a full load of water as of a full load of coal. */
    @Override
    protected void burn(double amount) {
        FuelStore fuel = fuelStore();
        WaterStore store = waterStore();
        if (WaterStore.needed() && fuel != null && fuel.fuel() > 0 && store != null && store.waterCapacity() > 0) {
            store.setWater(store.water() - amount * store.waterCapacity() / fuel.fuelCapacity());
        }
        super.burn(amount);
    }

    /** No water, no steam. */
    @Override
    boolean hasPower() {
        return super.hasPower() && !outOfWater();
    }

    @Override
    protected String noFuelMessage() {
        return outOfWater() && super.hasPower() ? "message.reikotrains.no_water" : super.noFuelMessage();
    }

    @Override
    public String noPowerStatus() {
        return outOfWater() && super.hasPower() ? "hud.reikotrains.auto.no_water" : super.noPowerStatus();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            WaterStore store = waterStore();
            int percent = !WaterStore.needed() || store == null ? -1 : store.waterFill();
            if (entityData.get(DATA_WATER) != percent) {
                entityData.set(DATA_WATER, percent);
            }
        }
    }

    /** A bucket (or other container) of water fills its tanks. */
    @Override
    protected InteractionResult interactCar(Player player, InteractionHand hand) {
        InteractionResult watered = water(player, hand);
        return watered != null ? watered : super.interactCar(player, hand);
    }

    /** Fills its water from what the player holds, if it's water; null if it isn't. */
    @Nullable
    protected InteractionResult water(Player player, InteractionHand hand) {
        return WaterTank.fillFromHand(player, hand, this, "message.reikotrains.water");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("Water", water);
    }

    /** An engine from before steam engines needed water comes with its tanks full. */
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        water = tag.contains("Water") ? Mth.clamp(tag.getDouble("Water"), 0.0, WATER_CAPACITY) : WATER_CAPACITY;
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
