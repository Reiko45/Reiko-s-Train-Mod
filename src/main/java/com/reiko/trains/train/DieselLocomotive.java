package com.reiko.trains.train;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.registry.ModItems;
import com.reiko.trains.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A diesel road-switcher, seven blocks long, on two bogies: a long hood in front of a high cab, so the driver sees
 * over it either way. It runs on {@code reikotrains:diesel_fuel} (Diesel Fuel canisters) in its own tank, not coal.
 * Quicker away than the steam engines and frugal at rest: its engine idles when it stands. It sounds its two-tone
 * horn instead of a whistle, and its exhaust smokes harder as the throttle opens. Hoppers and wagon loaders can fill
 * its tank: as a container it has one slot, a filler, that takes diesel fuel.
 */
public class DieselLocomotive extends Locomotive implements Container {
    public static final TagKey<Item> DIESEL_FUEL = TagKey.create(Registries.ITEM, ReikoTrains.id("diesel_fuel"));
    /** The tank holds ten canisters. */
    public static final double FUEL_CAPACITY = 10 * 9600;
    /** The driver on the left of the cab, the second man on the right, high up to see over the hood. */
    private static final Seat[] SEATS = {new Seat(-1.31, -0.5, 1.28), new Seat(-1.31, 0.5, 1.28)};

    private final FuelChute filler = new FuelChute(this, stack -> stack.is(DIESEL_FUEL));
    // client
    private int puff;
    private int lastThrottle;
    private int surge;

    public DieselLocomotive(EntityType<? extends DieselLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    protected CarPart[] createParts() {
        CarPart[] plain = super.createParts();
        CarPart[] parts = new CarPart[plain.length];
        for (int i = 0; i < plain.length; i++) {
            parts[i] = new ContainerCarPart(this, plain[i].offset, 2.0F, partHeight());
        }
        return parts;
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
    public boolean acceptsFuel(ItemStack stack) {
        return stack.is(DIESEL_FUEL);
    }

    @Override
    public String outOfFuelLabel() {
        return "hud.reikotrains.no_fuel";
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

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && !filler.isEmpty() && tickCount % 10 == 0) {
            filler.tip();
        }
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

    @Override
    public void destroy(Item dropItem) {
        if (!level().isClientSide) {
            filler.drop(this);
        }
        super.destroy(dropItem);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        filler.save(tag, registryAccess());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        filler.load(tag, registryAccess());
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Container: the filler

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return filler.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == 0 ? filler.get() : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return slot == 0 ? filler.take(amount) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return slot == 0 ? filler.takeAll() : ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot == 0) {
            filler.set(stack);
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return filler.accepts(stack);
    }

    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack) {
        return filler.canTake(stack);
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return false;
    }

    @Override
    public void clearContent() {
        filler.clear();
    }
}
