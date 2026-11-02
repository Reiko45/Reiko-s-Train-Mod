package com.reiko.trains.train;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.config.TrainsConfig;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.registry.ModItems;
import com.reiko.trains.registry.ModSounds;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A tank engine, two blocks wide and six long. Fire it up with coal (or anything in the
 * {@code reikotrains:locomotive_fuel} tag), climb into the cab, and drive: the throttle on forward and back (a notch
 * at a time, through neutral into reverse), the brake on jump, steering at junctions on left and right, and the
 * whistle on its own key. It pulls (or pushes) the cars coupled to it, more slowly the heavier the train.
 */
public class SteamLocomotive extends TrainCarEntity {
    public static final TagKey<Item> FUEL = TagKey.create(Registries.ITEM, ReikoTrains.id("locomotive_fuel"));
    public static final int MIN_THROTTLE = -2;
    public static final int MAX_THROTTLE = 4;
    /** The firebox holds 32 coal's worth. */
    public static final double FUEL_CAPACITY = 32 * 1600;
    /** The train weight the configured acceleration is for: the locomotive and two coaches. */
    private static final double REFERENCE_MASS = 6.0;
    /** The driver sits on the left of the cab, the fireman on the right. */
    private static final Seat[] SEATS = {new Seat(-1.44, -0.5, 1.05), new Seat(-1.44, 0.5, 1.05)};

    private static final EntityDataAccessor<Integer> DATA_THROTTLE = SynchedEntityData.defineId(SteamLocomotive.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_BRAKE = SynchedEntityData.defineId(SteamLocomotive.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_STEER = SynchedEntityData.defineId(SteamLocomotive.class, EntityDataSerializers.INT);
    /** How full the firebox is, 0-100 (only 0 when it's really empty). */
    private static final EntityDataAccessor<Integer> DATA_FUEL = SynchedEntityData.defineId(SteamLocomotive.class, EntityDataSerializers.INT);
    /** Whether the fire's lit: there's fuel (or none is needed) and a driver aboard. */
    private static final EntityDataAccessor<Boolean> DATA_STEAM = SynchedEntityData.defineId(SteamLocomotive.class, EntityDataSerializers.BOOLEAN);

    private double fuel;
    private int whistleCooldown;
    private int noFuelWarning;
    // client
    private double chuffDistance;
    private int idleSmoke;

    public SteamLocomotive(EntityType<? extends SteamLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_THROTTLE, 0);
        builder.define(DATA_BRAKE, false);
        builder.define(DATA_STEER, 0);
        builder.define(DATA_FUEL, 0);
        builder.define(DATA_STEAM, false);
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
    public boolean isLocomotive() {
        return true;
    }

    @Override
    protected Item getDropItem() {
        return ModItems.STEAM_LOCOMOTIVE.get();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Controls

    public int getThrottle() {
        return entityData.get(DATA_THROTTLE);
    }

    public void setThrottle(int notch) {
        entityData.set(DATA_THROTTLE, Mth.clamp(notch, MIN_THROTTLE, MAX_THROTTLE));
    }

    public boolean isBraking() {
        return entityData.get(DATA_BRAKE);
    }

    public void setBraking(boolean braking) {
        entityData.set(DATA_BRAKE, braking);
    }

    /** Which way the driver wants to go at the next junction: -1 left, 0 straight on, +1 right. */
    public int getSteer() {
        return entityData.get(DATA_STEER);
    }

    public void setSteer(int steer) {
        entityData.set(DATA_STEER, Mth.clamp(steer, -1, 1));
    }

    public int getFuelPercent() {
        return entityData.get(DATA_FUEL);
    }

    public boolean isSteaming() {
        return entityData.get(DATA_STEAM);
    }

    public double getFuel() {
        return fuel;
    }

    public void setFuel(double ticks) {
        fuel = Mth.clamp(ticks, 0.0, FUEL_CAPACITY);
        entityData.set(DATA_FUEL, fuel <= 0 ? 0 : Mth.clamp(Mth.ceil(fuel / FUEL_CAPACITY * 100.0), 1, 100));
    }

    /** The player driving, if anyone is: whoever got in first. */
    @Nullable
    public Player driver() {
        return getFirstPassenger() instanceof Player player ? player : null;
    }

    /** What the driver's keys do; see {@link TrainControlPayload}. */
    public void control(int action) {
        switch (action) {
            case TrainControlPayload.THROTTLE_UP -> setThrottle(getThrottle() + 1);
            case TrainControlPayload.THROTTLE_DOWN -> setThrottle(getThrottle() - 1);
            case TrainControlPayload.BRAKE_ON -> setBraking(true);
            case TrainControlPayload.BRAKE_OFF -> setBraking(false);
            case TrainControlPayload.WHISTLE -> whistle();
            case TrainControlPayload.STEER_LEFT -> setSteer(-1);
            case TrainControlPayload.STEER_STRAIGHT -> setSteer(0);
            case TrainControlPayload.STEER_RIGHT -> setSteer(1);
            default -> {
            }
        }
    }

    public void whistle() {
        if (whistleCooldown <= 0) {
            whistleCooldown = 30;
            Vec3 at = point(-0.7, 0, 2.6);
            level().playSound(null, at.x, at.y, at.z, ModSounds.STEAM_WHISTLE.get(), SoundSource.NEUTRAL, 5.0F, 1.0F);
        }
    }

    private boolean hasSteam() {
        return fuel > 0 || !TrainsConfig.NEEDS_FUEL.getAsBoolean();
    }

    /** Held in place: the brake's on, or nobody's driving (the handbrake). */
    public boolean isHeld() {
        return isBraking() || driver() == null;
    }

    /**
     * How hard the locomotive pulls the train this tick (blocks per tick per tick, forwards for the locomotive), given
     * how fast it's going (forwards positive) and how heavy the train is. It works up to the throttle notch's speed
     * and then holds it.
     */
    double traction(double speed, double trainMass) {
        int throttle = getThrottle();
        if (throttle == 0 || isHeld() || !hasSteam()) {
            return 0;
        }
        double target = TrainsConfig.MAX_SPEED.get() * throttle / MAX_THROTTLE;
        double pull = TrainsConfig.ACCELERATION.get() * REFERENCE_MASS / trainMass;
        if (target > 0) {
            return speed < target ? Math.min(pull, target - speed + 0.002) : 0;
        }
        return speed > target ? -Math.min(pull, speed - target + 0.002) : 0;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Ticking

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientEffects();
            return;
        }
        Player driver = driver();
        if (driver == null) {
            if (getThrottle() != 0) {
                setThrottle(0);
            }
            if (isBraking()) {
                setBraking(false);
            }
            if (getSteer() != 0) {
                setSteer(0);
            }
        } else if (getThrottle() != 0 && !hasSteam() && --noFuelWarning <= 0) {
            noFuelWarning = 60;
            driver.displayClientMessage(Component.translatable("message.reikotrains.no_fuel"), true);
        }
        boolean lit = hasSteam() && driver != null;
        if (entityData.get(DATA_STEAM) != lit) {
            entityData.set(DATA_STEAM, lit);
        }
        if (lit && fuel > 0 && TrainsConfig.NEEDS_FUEL.getAsBoolean()) {
            double rate = 0.1 + 0.35 * Math.abs(getThrottle()) / (double) MAX_THROTTLE;
            setFuel(fuel - rate * TrainsConfig.FUEL_USE.get());
        }
        if (whistleCooldown > 0) {
            whistleCooldown--;
        }
    }

    /** A point on the locomotive: blocks forward of its middle, to the right, and up from the rails. */
    public Vec3 point(double forward, double side, double up) {
        Vec3 f = forward();
        Vec3 flat = flatForward();
        return new Vec3(getX() + f.x * forward - flat.z * side, getY() + f.y * forward + up, getZ() + f.z * forward + flat.x * side);
    }

    /** Smoke from the chimney: a puff (and a chuff) every so often while it works, a wisp while it stands. */
    private void clientEffects() {
        if (!isSteaming()) {
            return;
        }
        Vec3 chimney = point(2.06, 0, 3.05);
        double dx = getX() - xo;
        double dz = getZ() - zo;
        double moved = Math.sqrt(dx * dx + dz * dz);
        int throttle = getThrottle();
        if (throttle != 0 || moved > 0.05) {
            chuffDistance += Math.max(moved, throttle != 0 ? 0.03 : 0);
            // Four chuffs a turn of the driving wheels (about 2.75 blocks round).
            if (chuffDistance >= 0.69) {
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

    // ----------------------------------------------------------------------------------------------------------------
    // Clicking

    @Override
    protected InteractionResult interactCar(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(FUEL)) {
            return addFuel(player, hand, stack);
        }
        return board(player);
    }

    /** Shovels as much of the stack into the firebox as will fit. */
    private InteractionResult addFuel(Player player, InteractionHand hand, ItemStack stack) {
        int burn = stack.getBurnTime(RecipeType.SMELTING);
        if (burn <= 0) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        int room = (int) ((FUEL_CAPACITY - fuel) / burn);
        int count = Math.min(room, stack.hasCraftingRemainingItem() ? 1 : stack.getCount());
        if (count <= 0) {
            player.displayClientMessage(Component.translatable("message.reikotrains.firebox_full"), true);
            return InteractionResult.CONSUME;
        }
        setFuel(fuel + (double) burn * count);
        if (!player.getAbilities().instabuild) {
            ItemStack remainder = stack.getCraftingRemainingItem();
            stack.shrink(count);
            if (!remainder.isEmpty()) {
                if (stack.isEmpty()) {
                    player.setItemInHand(hand, remainder);
                } else if (!player.addItem(remainder)) {
                    player.drop(remainder, false);
                }
            }
        }
        Vec3 firebox = point(-1.0, 0, 1.2);
        level().playSound(null, firebox.x, firebox.y, firebox.z, SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.6F, 0.8F);
        player.displayClientMessage(Component.translatable("message.reikotrains.fuel", getFuelPercent()), true);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("Fuel", fuel);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setFuel(tag.getDouble("Fuel"));
    }
}
