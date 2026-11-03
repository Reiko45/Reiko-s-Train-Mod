package com.reiko.trains.train;

import com.reiko.trains.config.TrainsConfig;
import com.reiko.trains.wire.OverheadLines;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import com.reiko.trains.registry.ModItems;
import com.reiko.trains.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A battery-electric locomotive, seven and a half blocks long, with a cab at each end. It runs on BuildCraft power
 * (MJ), kept in its batteries and charged at a charging point beside the track (which engines, kinesis pipes or other
 * mods' cables feed). The quickest and strongest engine there is, quiet but for the hum of its motors, and it uses
 * next to nothing standing still; but its batteries hold far less than a diesel's tank, so it needs charging often.
 * Under a live overhead line it raises its pantograph and runs from the wire instead, charging its batteries as it
 * goes (see {@link OverheadLines}).
 */
public class ElectricLocomotive extends Locomotive {
    /** What its batteries hold, in MJ: about twenty minutes at full power. */
    public static final double BATTERY_CAPACITY = 1_000_000;
    /** MJ it uses per "unit" of the shared burn rate: about 40 MJ a tick at full power, a third of an MJ standing. */
    private static final double BURN_SCALE = 160;
    /** The driver and the second man, side by side in the front cab. */
    private static final Seat[] SEATS = {new Seat(2.8, -0.45, 1.0), new Seat(2.8, 0.45, 1.0)};

    private static final EntityDataAccessor<Boolean> DATA_WIRED = SynchedEntityData.defineId(ElectricLocomotive.class,
            EntityDataSerializers.BOOLEAN);
    /** The live overhead line it's under (the line's first mast), or null. */
    @Nullable
    private BlockPos line;

    // client
    private int spark;
    /** How far up its pantograph is, 0 (down) to 1 (up against the wire), now and last tick. */
    private float pantograph;
    private float pantographO;

    public ElectricLocomotive(EntityType<? extends ElectricLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_WIRED, false);
    }

    @Override
    public double halfLength() {
        return 3.75;
    }

    /** How far forwards of its middle its pantograph is, in blocks. */
    protected double pantographForward() {
        return 23.0 / 16;
    }

    /** Whether it's under a live overhead line, running from the wire. */
    public boolean isOnWire() {
        return entityData.get(DATA_WIRED);
    }

    /** Client: how far up its pantograph is, 0 to 1. */
    public float pantograph(float partialTick) {
        return Mth.lerp(partialTick, pantographO, pantograph);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            pantographO = pantograph;
            pantograph = Mth.clamp(pantograph + (isOnWire() ? 0.08F : -0.08F), 0.0F, 1.0F);
            return;
        }
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        OverheadLines lines = OverheadLines.get(server);
        if ((tickCount + getId()) % 5 == 0) {
            line = lines.lineAbove(position().add(flatForward().scale(pantographForward())));
        }
        boolean live = line != null && lines.energy(line) >= 1.0;
        if (live) {
            // Charging from the wire as it goes.
            double want = Math.min(TrainsConfig.WIRE_CHARGE_RATE.get(), room());
            if (want > 0) {
                setFuel(fuel() + lines.take(line, want));
            }
        }
        if (entityData.get(DATA_WIRED) != live) {
            entityData.set(DATA_WIRED, live);
        }
    }

    /** Under a live wire, it runs from the wire (its batteries make up any shortfall). */
    @Override
    protected void burn(double amount) {
        if (line != null && level() instanceof ServerLevel server) {
            amount -= OverheadLines.get(server).take(line, amount);
        }
        if (amount > 1.0E-9) {
            super.burn(amount);
        }
    }

    /** Flat batteries don't stop it under a live wire. */
    @Override
    boolean hasPower() {
        return super.hasPower() || (line != null && level() instanceof ServerLevel server && OverheadLines.get(server).energy(line) >= 1.0);
    }

    @Override
    public double bogieOffset() {
        return 2.5;
    }

    @Override
    public double mass() {
        return 4.2;
    }

    @Override
    protected float partHeight() {
        return 3.1F;
    }

    @Override
    public Seat[] seats() {
        return SEATS;
    }

    @Override
    protected double tankCapacity() {
        return BATTERY_CAPACITY;
    }

    /** What its batteries hold, in MJ. */
    public double batteryCapacity() {
        return tankCapacity();
    }

    /** About 60 km/h at the default settings. */
    @Override
    protected double speedFactor() {
        return 1.4;
    }

    @Override
    protected double strength() {
        return 1.8;
    }

    @Override
    protected double appetite() {
        return 0.7;
    }

    /** Standing with the throttle shut it uses next to nothing: no engine to keep running. */
    @Override
    protected double idleAppetite() {
        return 0.02;
    }

    @Override
    protected double burnScale() {
        return BURN_SCALE;
    }

    /** It takes no fuel by hand: it's charged at a charging point. */
    @Override
    public boolean acceptsFuel(ItemStack stack) {
        return false;
    }

    /** Puts up to {@code mj} MJ into its batteries; returns how much went in. */
    public double charge(double mj) {
        double taken = Math.max(0, Math.min(mj, tankCapacity() - fuel()));
        if (taken > 0) {
            setFuel(fuel() + taken);
        }
        return taken;
    }

    /** Room left in its batteries, in MJ. */
    public double room() {
        return Math.max(0, tankCapacity() - fuel());
    }

    /** Clicking it with something it can't use tells you how charged it is, and where to charge it. */
    @Override
    protected InteractionResult interactCar(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.isEmpty() && stack.getBurnTime(net.minecraft.world.item.crafting.RecipeType.SMELTING) > 0) {
            if (!level().isClientSide) {
                player.displayClientMessage(Component.translatable("message.reikotrains.electric_charge", fuelPercent()), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.interactCar(player, hand);
    }

    @Override
    public String fuelLabel() {
        return isOnWire() ? "hud.reikotrains.wire" : "hud.reikotrains.charge";
    }

    @Override
    public String outOfFuelLabel() {
        return "hud.reikotrains.flat";
    }

    @Override
    public String noPowerStatus() {
        return "hud.reikotrains.auto.flat";
    }

    @Override
    protected String fuelMessage() {
        return "message.reikotrains.electric_charge";
    }

    @Override
    protected String fullMessage() {
        return "message.reikotrains.electric_full";
    }

    @Override
    protected String noFuelMessage() {
        return "message.reikotrains.electric_flat";
    }

    @Override
    protected Vec3 fuelInlet() {
        return point(0, 0.95, 1.75);
    }

    @Override
    protected void soundWhistle() {
        Vec3 at = point(-2.9, 0, 3.1);
        level().playSound(null, at.x, at.y, at.z, ModSounds.ELECTRIC_HORN.get(), SoundSource.NEUTRAL, 5.0F, 1.0F);
    }

    @Override
    protected Item getDropItem() {
        return ModItems.ELECTRIC_LOCOMOTIVE.get();
    }

    /** No smoke: now and then, working hard, a spark from under the bogies. */
    @Override
    protected void clientEffects() {
        if (!isRunning()) {
            return;
        }
        float load = Math.abs(getThrottle()) / (float) MAX_THROTTLE;
        if (++spark < 8 || random.nextFloat() > load * 0.3F) {
            return;
        }
        spark = 0;
        double along = (random.nextBoolean() ? 1 : -1) * bogieOffset();
        Vec3 at = point(along, (random.nextBoolean() ? 1 : -1) * 0.45, 0.1);
        level().addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, random.nextGaussian() * 0.05, 0.05, random.nextGaussian() * 0.05);
    }

    /** How fast it's going, 0-1 of its top speed, for its motors' hum; and whether they're live at all. */
    public float motorSpeed() {
        if (!isRunning()) {
            return 0;
        }
        double moved = Math.hypot(getX() - xo, getZ() - zo);
        return (float) Math.min(1.0, moved / maxSpeed());
    }
}
