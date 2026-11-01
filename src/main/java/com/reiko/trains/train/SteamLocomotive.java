package com.reiko.trains.train;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.config.TrainsConfig;
import com.reiko.trains.registry.ModItems;
import com.reiko.trains.registry.ModSounds;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A tank engine. Fire it up with coal (or anything else in the {@code reikotrains:locomotive_fuel} tag), climb into
 * the cab, and drive it with the throttle (forward and back keys, a notch at a time, reverse included), the brake
 * (jump, held) and the whistle (H). It pulls whatever is coupled behind it, a little slower for every car.
 */
public class SteamLocomotive extends AbstractMinecart implements TrainCar {
    public static final TagKey<Item> FUEL = TagKey.create(Registries.ITEM, ReikoTrains.id("locomotive_fuel"));
    public static final int MIN_THROTTLE = -2;
    public static final int MAX_THROTTLE = 4;
    /** The firebox holds 32 coal's worth. */
    public static final double FUEL_CAPACITY = 32 * 1600;
    /** The driver sits in the middle of the cab, looking out of the front window; the fireman beside the bunker. */
    private static final Seat[] SEATS = {new Seat(-0.62, 0.0, 0.54), new Seat(-0.75, -0.24, 0.54)};
    /** Where the smoke comes out, from the middle of the locomotive: forward and up. */
    private static final double CHIMNEY_FORWARD = 0.78;
    private static final double CHIMNEY_UP = 1.72;

    private static final EntityDataAccessor<Integer> DATA_THROTTLE = SynchedEntityData.defineId(SteamLocomotive.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_BRAKE = SynchedEntityData.defineId(SteamLocomotive.class, EntityDataSerializers.BOOLEAN);
    /** How full the firebox is, 0-100 (only 0 when it's really empty). */
    private static final EntityDataAccessor<Integer> DATA_FUEL = SynchedEntityData.defineId(SteamLocomotive.class, EntityDataSerializers.INT);
    /** Whether the fire's lit: there's fuel (or none is needed) and a driver aboard. */
    private static final EntityDataAccessor<Boolean> DATA_STEAM = SynchedEntityData.defineId(SteamLocomotive.class, EntityDataSerializers.BOOLEAN);

    private final TrainCarState trainState = new TrainCarState();
    private double fuel;
    private int whistleCooldown;
    private int trainLength = 1;
    private int noFuelWarning;
    // client only
    private double chuffDistance;
    private int idleSmoke;
    private float speedKmh;

    public SteamLocomotive(EntityType<? extends SteamLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_THROTTLE, 0);
        builder.define(DATA_BRAKE, false);
        builder.define(DATA_FUEL, 0);
        builder.define(DATA_STEAM, false);
    }

    // --------------------------------------------------------------------------------------------------------------
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

    /** Client: how fast the locomotive is going, for the cab gauges. */
    public float getSpeedKmh() {
        return speedKmh;
    }

    /** The player driving, if anyone is: whoever got in first. */
    @Nullable
    public Player driver() {
        return getFirstPassenger() instanceof Player player ? player : null;
    }

    /** What the driver's keys do; see {@link com.reiko.trains.network.TrainControlPayload}. */
    public void control(int action) {
        switch (action) {
            case com.reiko.trains.network.TrainControlPayload.THROTTLE_UP -> setThrottle(getThrottle() + 1);
            case com.reiko.trains.network.TrainControlPayload.THROTTLE_DOWN -> setThrottle(getThrottle() - 1);
            case com.reiko.trains.network.TrainControlPayload.BRAKE_ON -> setBraking(true);
            case com.reiko.trains.network.TrainControlPayload.BRAKE_OFF -> setBraking(false);
            case com.reiko.trains.network.TrainControlPayload.WHISTLE -> whistle();
            default -> {
            }
        }
    }

    public void whistle() {
        if (whistleCooldown <= 0) {
            whistleCooldown = 30;
            level().playSound(null, getX(), getY() + 1.5, getZ(), ModSounds.STEAM_WHISTLE.get(), SoundSource.NEUTRAL, 4.0F, 1.0F);
        }
    }

    private boolean hasSteam() {
        return fuel > 0 || !TrainsConfig.NEEDS_FUEL.getAsBoolean();
    }

    /** Held in place: the brake's on, or nobody's driving (the handbrake). */
    private boolean isHeld() {
        return isBraking() || driver() == null;
    }

    // --------------------------------------------------------------------------------------------------------------
    // Ticking

    @Override
    public void tick() {
        if (!level().isClientSide) {
            Trains.follow(this);
            drive();
        }
        super.tick();
        if (level().isClientSide) {
            Trains.rollWheels(this);
            clientEffects();
        } else {
            burnFuel();
            if (whistleCooldown > 0) {
                whistleCooldown--;
            }
        }
    }

    private void drive() {
        Player driver = driver();
        if (driver == null) {
            if (getThrottle() != 0) {
                setThrottle(0);
            }
            if (isBraking()) {
                setBraking(false);
            }
        }
        if (tickCount % 20 == 0) {
            trainLength = Trains.train(this).size();
        }
        Vec3 motion = getDeltaMovement();
        if (isHeld()) {
            double speed = motion.horizontalDistance();
            double slowed = Math.max(0.0, speed - (0.012 + speed * 0.04));
            double scale = speed < 1.0E-6 ? 0.0 : slowed / speed;
            setDeltaMovement(motion.x * scale, motion.y, motion.z * scale);
            return;
        }
        int throttle = getThrottle();
        if (throttle == 0) {
            return;
        }
        if (!hasSteam()) {
            if (driver != null && --noFuelWarning <= 0) {
                noFuelWarning = 60;
                driver.displayClientMessage(Component.translatable("message.reikotrains.no_fuel"), true);
            }
            return;
        }
        double target = getMaxSpeedWithRail() * throttle / MAX_THROTTLE;
        double accel = TrainsConfig.ACCELERATION.get() / (1.0 + 0.25 * (trainLength - 1));
        double speed = Trains.forwardSpeed(this);
        double next = target > 0 ? (speed < target ? Math.min(target, speed + accel) : speed)
                : (speed > target ? Math.max(target, speed - accel) : speed);
        if (next != speed) {
            Vec3 f = Trains.facing(this);
            setDeltaMovement(motion.add(f.x * (next - speed), 0.0, f.z * (next - speed)));
        }
    }

    private void burnFuel() {
        boolean lit = hasSteam() && driver() != null;
        if (entityData.get(DATA_STEAM) != lit) {
            entityData.set(DATA_STEAM, lit);
        }
        if (!lit || fuel <= 0 || !TrainsConfig.NEEDS_FUEL.getAsBoolean()) {
            return;
        }
        double rate = 0.1 + 0.35 * Math.abs(getThrottle()) / (double) MAX_THROTTLE;
        setFuel(fuel - rate * TrainsConfig.FUEL_USE.get());
    }

    /** Smoke from the chimney: a puff (and a chuff) every so often while it works, a wisp while it stands. */
    private void clientEffects() {
        double dx = getX() - xo;
        double dz = getZ() - zo;
        double moved = Math.sqrt(dx * dx + dz * dz);
        speedKmh = moved > 4.0 ? speedKmh : (float) (moved * 20.0 * 3.6);
        if (!isSteaming()) {
            return;
        }
        Vec3 f = Trains.facing(this);
        double sx = getX() + f.x * CHIMNEY_FORWARD;
        double sy = getY() + CHIMNEY_UP;
        double sz = getZ() + f.z * CHIMNEY_FORWARD;
        int throttle = getThrottle();
        if (throttle != 0) {
            chuffDistance += Math.max(moved, 0.02);
            if (chuffDistance >= 1.2) {
                chuffDistance = 0;
                float load = Math.abs(throttle) / (float) MAX_THROTTLE;
                level().playLocalSound(sx, sy, sz, ModSounds.STEAM_CHUFF.get(), SoundSource.NEUTRAL, 0.5F + 0.5F * load,
                        0.9F + random.nextFloat() * 0.2F, false);
                for (int i = 0; i < 2 + Math.round(3 * load); i++) {
                    level().addParticle(ParticleTypes.LARGE_SMOKE, sx + random.nextGaussian() * 0.08, sy, sz + random.nextGaussian() * 0.08,
                            random.nextGaussian() * 0.02, 0.12 + random.nextDouble() * 0.08, random.nextGaussian() * 0.02);
                }
                level().addParticle(ParticleTypes.CLOUD, sx, sy + 0.1, sz, 0.0, 0.08, 0.0);
            }
        } else if (++idleSmoke >= 14) {
            idleSmoke = 0;
            level().addParticle(ParticleTypes.SMOKE, sx, sy, sz, 0.0, 0.06, 0.0);
            if (random.nextInt(4) == 0) {
                level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, sx, sy, sz, 0.0, 0.03, 0.0);
            }
        }
    }

    @Override
    public double getSlopeAdjustment() {
        return isHeld() ? 0.0 : super.getSlopeAdjustment();
    }

    @Override
    public void moveMinecartOnRail(BlockPos pos) {
        Trains.moveOnRail(this);
    }

    @Override
    protected void applyNaturalSlowdown() {
        Trains.slowDown(this);
    }

    // --------------------------------------------------------------------------------------------------------------
    // Interaction

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        InteractionResult coupler = Trains.useCoupler(this, player, hand);
        if (coupler != InteractionResult.PASS) {
            return coupler;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(FUEL)) {
            return addFuel(player, hand, stack);
        }
        return Trains.board(this, player);
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
        level().playSound(null, getX(), getY() + 0.8, getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.6F, 0.8F);
        player.displayClientMessage(Component.translatable("message.reikotrains.fuel", getFuelPercent()), true);
        return InteractionResult.CONSUME;
    }

    // --------------------------------------------------------------------------------------------------------------
    // Train car

    @Override
    public TrainCarState trainState() {
        return trainState;
    }

    @Override
    public double halfLength() {
        return 19.0 / 16.0;
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
    protected boolean canAddPassenger(Entity passenger) {
        return Trains.hasFreeSeat(this);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        Vec3 seat = Trains.seatPosition(this, passenger);
        return seat != null ? seat : super.getPassengerAttachmentPoint(passenger, dimensions, partialTick);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return Trains.cullingBox(this);
    }

    @Override
    public float getPickRadius() {
        return 0.5F;
    }

    @Override
    public Type getMinecartType() {
        return Type.FURNACE;
    }

    @Override
    protected Item getDropItem() {
        return ModItems.STEAM_LOCOMOTIVE.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(getDropItem());
    }

    @Override
    public void remove(RemovalReason reason) {
        Trains.onRemoved(this, reason);
        super.remove(reason);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        Trains.save(this, tag);
        tag.putDouble("Fuel", fuel);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        Trains.load(this, tag);
        setFuel(tag.getDouble("Fuel"));
    }
}
