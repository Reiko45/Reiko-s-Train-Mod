package com.reiko.trains.train;

import com.reiko.trains.registry.ModItems;
import com.reiko.trains.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * A diesel road-switcher, seven blocks long, on two bogies: a long hood in front of a high cab, so the driver sees
 * over it either way. It runs on diesel (see {@link DieselFuel}: BuildCraft Reborn's Dense Fuel and Mixed Heavy
 * Fuels, the same as the car mod's diesel vehicles), kept in its tank as energy. Fill it from a bucket or any fluid
 * container, through pipes, or from BuildCraft tanks beside a wagon loader. Quicker away than the steam engines and
 * frugal at rest: its engine idles when it stands. It sounds its two-tone horn instead of a whistle, and its exhaust
 * smokes harder as the throttle opens.
 */
public class DieselLocomotive extends Locomotive {
    /** The tank holds 16 buckets of Dense Fuel (more of a weaker diesel fits by the bucket, but goes less far). */
    public static final int TANK_BUCKETS = 16;
    public static final double FUEL_CAPACITY = TANK_BUCKETS * DieselFuel.BUCKET_ENERGY;
    /** MJ it burns per "unit" of the shared burn rate: at full throttle, a bucket of Dense Fuel lasts about 5 minutes. */
    private static final double BURN_SCALE = 200;
    /** The driver on the left of the cab, the second man on the right, high up to see over the hood. */
    private static final Seat[] SEATS = {new Seat(-1.31, -0.5, 1.28), new Seat(-1.31, 0.5, 1.28)};

    private final DieselTank tank = new DieselTank(this);
    // client
    private int puff;
    private int lastThrottle;
    private int surge;

    public DieselLocomotive(EntityType<? extends DieselLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    public double halfLength() {
        return 3.6;
    }

    @Override
    public double bogieOffset() {
        return 2.4;
    }

    @Override
    public double mass() {
        return 4.0;
    }

    @Override
    protected float partHeight() {
        return 3.3F;
    }

    @Override
    public Seat[] seats() {
        return SEATS;
    }

    @Override
    protected double tankCapacity() {
        return FUEL_CAPACITY;
    }

    /** About 52 km/h at the default settings. */
    @Override
    protected double speedFactor() {
        return 1.2;
    }

    @Override
    protected double strength() {
        return 1.6;
    }

    @Override
    protected double appetite() {
        return 0.8;
    }

    @Override
    protected double idleAppetite() {
        return 0.3;
    }

    @Override
    protected double burnScale() {
        return BURN_SCALE;
    }

    /** It takes no solid fuel: see {@link #interactCar}. */
    @Override
    public boolean acceptsFuel(ItemStack stack) {
        return false;
    }

    /** Its tank, for buckets, pipes and wagon loaders. */
    public DieselTank tank() {
        return tank;
    }

    /** Clicking it with a bucket (or any fluid container) of diesel fills its tank. */
    @Override
    protected InteractionResult interactCar(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        var fluid = FluidUtil.getFluidContained(stack);
        if (fluid.isEmpty()) {
            return super.interactCar(player, hand);
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!DieselFuel.isDiesel(fluid.get())) {
            player.displayClientMessage(Component.translatable("message.reikotrains.not_diesel"), true);
        } else if (FluidUtil.interactWithFluidHandler(player, hand, tank)) {
            Vec3 at = fuelInlet();
            level().playSound(null, at.x, at.y, at.z, SoundEvents.BUCKET_EMPTY, SoundSource.NEUTRAL, 0.8F, 1.0F);
            player.displayClientMessage(Component.translatable(fuelMessage(), fuelPercent()), true);
        } else {
            player.displayClientMessage(Component.translatable(fullMessage()), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public String outOfFuelLabel() {
        return "hud.reikotrains.no_fuel";
    }

    @Override
    public String noPowerStatus() {
        return "hud.reikotrains.auto.no_fuel";
    }

    @Override
    protected String fuelMessage() {
        return "message.reikotrains.diesel_fuel";
    }

    @Override
    protected String fullMessage() {
        return "message.reikotrains.diesel_full";
    }

    @Override
    protected String noFuelMessage() {
        return "message.reikotrains.no_diesel";
    }

    @Override
    protected Vec3 fuelInlet() {
        return point(-0.2, 0.9, 0.6);
    }

    @Override
    protected void soundWhistle() {
        Vec3 at = point(-1.1, 0, 3.5);
        level().playSound(null, at.x, at.y, at.z, ModSounds.DIESEL_HORN.get(), SoundSource.NEUTRAL, 5.0F, 1.0F);
    }

    @Override
    protected Item getDropItem() {
        return ModItems.DIESEL_LOCOMOTIVE.get();
    }

    /**
     * Exhaust from the two stacks on the hood: a light haze idling, thicker the harder it works, and a black cloud
     * when the throttle opens up.
     */
    @Override
    protected void clientEffects() {
        if (!isRunning()) {
            lastThrottle = 0;
            return;
        }
        int throttle = Math.abs(isAutomatic() && Math.abs(getX() - xo) + Math.abs(getZ() - zo) > 0.02 ? MAX_THROTTLE / 2 : getThrottle());
        if (throttle > lastThrottle) {
            surge = 14;
        }
        lastThrottle = throttle;
        float load = throttle / (float) MAX_THROTTLE;
        int interval = surge > 0 ? 1 : Math.max(2, 10 - Math.round(7 * load));
        if (surge > 0) {
            surge--;
        }
        if (++puff < interval) {
            return;
        }
        puff = 0;
        for (double forward : new double[]{0.95, 0.15}) {
            Vec3 stack = point(forward, 0, 2.75);
            if (surge > 0) {
                level().addParticle(ParticleTypes.LARGE_SMOKE, stack.x, stack.y, stack.z, random.nextGaussian() * 0.02, 0.2, random.nextGaussian() * 0.02);
            } else if (load > 0) {
                level().addParticle(ParticleTypes.SMOKE, stack.x, stack.y, stack.z, random.nextGaussian() * 0.01, 0.12 + 0.12 * load, random.nextGaussian() * 0.01);
                if (random.nextFloat() < load * 0.5F) {
                    level().addParticle(ParticleTypes.LARGE_SMOKE, stack.x, stack.y, stack.z, 0, 0.12, 0);
                }
            } else if (random.nextInt(3) == 0) {
                level().addParticle(ParticleTypes.SMOKE, stack.x, stack.y, stack.z, 0, 0.06, 0);
            }
        }
    }

    /** How hard the engine's working, 0-1, for its sound: the throttle (or a timetable's), when it's running. */
    public float engineLoad() {
        if (!isRunning()) {
            return 0;
        }
        float moving = (float) Math.min(1.0, Math.abs(getX() - xo) + Math.abs(getZ() - zo));
        return isAutomatic() ? Math.min(1.0F, moving * 3) : Math.abs(getThrottle()) / (float) MAX_THROTTLE;
    }
}
