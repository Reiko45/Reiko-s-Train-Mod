package com.reiko.trains.train;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.config.TrainsConfig;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.registry.ModItems;
import com.reiko.trains.registry.ModSounds;
import com.reiko.trains.signal.TrackSide;
import com.reiko.trains.track.TrackPos;
import java.util.List;
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
 * A locomotive: steam or diesel. Fuel it with coal (or anything in the {@code reikotrains:locomotive_fuel} tag),
 * climb into the cab, and drive: the throttle on forward and back (a notch at a time, through neutral into reverse),
 * the brake on jump, steering at junctions on left and right, and the whistle (or horn) on its own key. It pulls (or
 * pushes) the cars coupled to it, more slowly the heavier the train; it can run a timetable by itself, and in a train
 * with other locomotives one is in charge and the rest work with it. Its own fuel is in its tank (a steam engine's
 * firebox), unless it takes it from elsewhere (see {@link #fuelStore()}).
 */
public abstract class Locomotive extends TrainCarEntity implements FuelStore {
    public static final TagKey<Item> FUEL = TagKey.create(Registries.ITEM, ReikoTrains.id("locomotive_fuel"));
    public static final int MIN_THROTTLE = -2;
    public static final int MAX_THROTTLE = 4;
    /** The train weight the configured acceleration is for: the locomotive and two coaches. */
    private static final double REFERENCE_MASS = 6.0;

    private static final EntityDataAccessor<Integer> DATA_THROTTLE = SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_BRAKE = SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_STEER = SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.INT);
    /** How full its fuel is, 0-100 (only 0 when it's really empty; -1 if it has nowhere to get fuel from). */
    private static final EntityDataAccessor<Integer> DATA_FUEL = SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.INT);
    /** Whether it's running (a steam engine's fire is lit): there's fuel (or none is needed), and it's driven. */
    private static final EntityDataAccessor<Boolean> DATA_RUNNING = SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.BOOLEAN);
    /** The train's speed, forwards for the locomotive, in blocks per tick: for the speedometer. */
    private static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.FLOAT);
    /** The next signal ahead, for the gauges: 0 none, else 1 (clear) or 2 (red) times 10000, plus how far in blocks. */
    private static final EntityDataAccessor<Integer> DATA_SIGNAL = SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.INT);
    /** The next station ahead and how far, or the timetable's status, for the gauges. */
    private static final EntityDataAccessor<Component> DATA_STATUS = SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.COMPONENT);
    private static final EntityDataAccessor<Boolean> DATA_AUTO = SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.BOOLEAN);
    /** The next switch ahead: (turn + 2) * 10000 + distance, or 0 if there's none in sight. */
    private static final EntityDataAccessor<Integer> DATA_POINTS = SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.INT);

    private double fuel;
    private final Autopilot autopilot = new Autopilot(this);
    /** When it's one of several locomotives in a train and another is in charge: that one, and until when (game time). */
    @Nullable
    private Locomotive helping;
    private long helpingUntil;
    /** Helping: the notch it works at, the one in charge's turned round if it faces the other way (so not limited to R2). */
    private int assist;
    private int whistleCooldown;
    private int noFuelWarning;

    protected Locomotive(EntityType<? extends Locomotive> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_THROTTLE, 0);
        builder.define(DATA_BRAKE, false);
        builder.define(DATA_STEER, 0);
        builder.define(DATA_FUEL, 0);
        builder.define(DATA_RUNNING, false);
        builder.define(DATA_SPEED, 0.0F);
        builder.define(DATA_SIGNAL, 0);
        builder.define(DATA_STATUS, Component.empty());
        builder.define(DATA_AUTO, false);
        builder.define(DATA_POINTS, 0);
    }

    @Override
    public boolean isLocomotive() {
        return true;
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

    /** How full its firebox (or tender) is, 0-100; -1 for an engine with no tender. */
    public int getFuelPercent() {
        return entityData.get(DATA_FUEL);
    }

    /** Whether it's running (for a steam engine, whether its fire's lit): it has fuel, and it's being driven. */
    public boolean isRunning() {
        return entityData.get(DATA_RUNNING);
    }

    public double getFuel() {
        return fuel;
    }

    @Override
    public double fuel() {
        return fuel;
    }

    @Override
    public double fuelCapacity() {
        return tankCapacity();
    }

    /** How much fuel its own tank (or firebox) holds, in ticks of burning. */
    protected abstract double tankCapacity();

    /** Fills (or empties) its own tank (or firebox); for an engine with a tender, see {@link #fuelStore()}. */
    @Override
    public void setFuel(double ticks) {
        fuel = Mth.clamp(ticks, 0.0, tankCapacity());
        showFuel();
    }

    /** Where its fuel comes from: its own tank (or firebox). Null if it hasn't got any (an express with no tender). */
    @Nullable
    public FuelStore fuelStore() {
        return this;
    }

    private void showFuel() {
        FuelStore store = fuelStore();
        int percent = store == null ? -1 : store.fuelPercent();
        if (entityData.get(DATA_FUEL) != percent) {
            entityData.set(DATA_FUEL, percent);
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // What sort of engine it is

    /** Its top speed, as a share of the server's {@code maxSpeed}. */
    protected double speedFactor() {
        return 1.0;
    }

    /** How hard it pulls, compared with the tank engine. */
    protected double strength() {
        return 1.0;
    }

    /** How fast it burns fuel working hard, compared with the tank engine. */
    protected double appetite() {
        return 1.0;
    }

    /** How much of its fuel's unit one "unit" of burning is: 1 for steam engines (furnace ticks of fuel). */
    protected double burnScale() {
        return 1.0;
    }

    /** How fast it burns fuel standing with the throttle shut, compared with the tank engine. */
    protected double idleAppetite() {
        return 1.0;
    }

    /** Its top speed at full throttle, in blocks per tick. */
    public double maxSpeed() {
        return TrainsConfig.MAX_SPEED.get() * speedFactor();
    }

    /** Plays its whistle or horn. */
    protected abstract void soundWhistle();

    /** What the gauges call its fuel. */
    public String fuelLabel() {
        return "hud.reikotrains.fuel";
    }

    /** What the gauges say when it has no fuel: NO FIRE, say. */
    public String outOfFuelLabel() {
        return "hud.reikotrains.no_fire";
    }

    /** What a timetable's display says when it's run out: the fire's out, say. */
    public String noPowerStatus() {
        return "hud.reikotrains.auto.no_fire";
    }

    /** What the driver's told when they fill it up: how full it is now (with a %s), and that it's full. */
    protected String fuelMessage() {
        return "message.reikotrains.fuel";
    }

    protected String fullMessage() {
        return "message.reikotrains.firebox_full";
    }

    /** Where fuel goes in, for the sound. */
    protected abstract Vec3 fuelInlet();

    /** The player driving, if anyone is: whoever got in first. */
    @Nullable
    public Player driver() {
        return getFirstPassenger() instanceof Player player ? player : null;
    }

    /** What the driver's keys do; see {@link TrainControlPayload}. Working the throttle or brake takes over from the timetable. */
    public void control(int action) {
        boolean takesOver = action == TrainControlPayload.THROTTLE_UP || action == TrainControlPayload.THROTTLE_DOWN
                || action == TrainControlPayload.BRAKE_ON;
        if (takesOver && autopilot.isOn()) {
            autopilot.stop();
            Player driver = driver();
            if (driver != null) {
                driver.displayClientMessage(Component.translatable("message.reikotrains.auto.taken_over"), true);
            }
        }
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
            soundWhistle();
        }
    }

    boolean hasPower() {
        if (!TrainsConfig.NEEDS_FUEL.getAsBoolean()) {
            return true;
        }
        FuelStore store = fuelStore();
        return store != null && store.fuel() > 0;
    }

    /** How hard it can pull a train this heavy, in blocks per tick per tick. */
    double pull(double trainMass) {
        return TrainsConfig.ACCELERATION.get() * REFERENCE_MASS * strength() / trainMass;
    }

    /** Held in place: the brake's on, or nobody's driving (the handbrake). */
    public boolean isHeld() {
        return isBraking() || !isDriven();
    }

    /**
     * Whether it's being driven: someone's at its controls, it's running a timetable, or it's helping another
     * locomotive in the same train that is.
     */
    public boolean isDriven() {
        return isInCharge() || (helping != null && !helping.isRemoved() && level().getGameTime() <= helpingUntil && helping.isInCharge());
    }

    /** Whether it's driven itself: someone's at its controls, or it's running a timetable. */
    public boolean isInCharge() {
        return driver() != null || autopilot.isOn();
    }

    /**
     * Called by the train each tick when another of its locomotives is in charge: this one works with it, its throttle
     * and brake set the same ({@code facing} is +1 if it faces the same way as that one, -1 if not).
     */
    void help(Locomotive leader, int facing) {
        helping = leader;
        helpingUntil = level().getGameTime() + 2;
        if (autopilot.isOn()) {
            autopilot.stop();
        }
        assist = leader.getThrottle() * facing;
        int shown = Mth.clamp(assist, MIN_THROTTLE, MAX_THROTTLE);
        if (getThrottle() != shown) {
            setThrottle(shown);
        }
        if (isBraking() != leader.isBraking()) {
            setBraking(leader.isBraking());
        }
        if (getSteer() != 0) {
            setSteer(0);
        }
    }

    /** The notch it's working at: its throttle's, or when it's helping another engine, that one's (turned round to suit). */
    public int workingThrottle() {
        return helping() != null ? assist : getThrottle();
    }

    /** The locomotive it's helping, if another in its train is in charge. */
    @Nullable
    public Locomotive helping() {
        return helping != null && level().getGameTime() <= helpingUntil ? helping : null;
    }

    public Autopilot autopilot() {
        return autopilot;
    }

    public boolean isAutomatic() {
        return entityData.get(DATA_AUTO);
    }

    /** For the gauges: the next signal ahead, 0 if none in sight, else 1 (clear) or 2 (red). */
    public int signalAspect() {
        return entityData.get(DATA_SIGNAL) / 10000;
    }

    public int signalDistance() {
        return entityData.get(DATA_SIGNAL) % 10000;
    }

    /** For the gauges: the next station and how far, or where the timetable has got to. */
    public Component status() {
        return entityData.get(DATA_STATUS);
    }

    /**
     * Run by the train each tick before it moves (the locomotive is car {@code index}): looks down the line, and works
     * out how it's being driven. On a timetable the autopilot does it; otherwise it's the driver, though (unless the
     * server turns it off) the train won't run past a red signal.
     */
    Drive plan(List<TrainCarEntity> cars, int[] signs, int index, double speed) {
        if (autopilot.isOn()) {
            return autopilot.plan(cars, signs, speed);
        }
        int throttle = getThrottle();
        int direction = Math.abs(speed) > 1.0E-4 ? (speed > 0 ? 1 : -1) : throttle != 0 ? Integer.signum(throttle) * signs[index] : 1;
        int steer = getSteer();
        TrackPos.Chooser chooser = (node, side, options, heading) -> TrackSide.wayOn(level(), node, side, options, heading, steer);
        Lookahead look = Lookahead.look(cars, signs, direction, chooser, null);
        showLookahead(look);
        updateAutoDisplay();
        double limit = TrainsConfig.SIGNALS_STOP_DRIVERS.getAsBoolean() ? Drive.stoppingSpeed(look.signalStop) : Double.POSITIVE_INFINITY;
        return new Drive(direction, null, limit, false, 0);
    }

    /** Puts what's ahead on the gauges: the next signal, and (when not on a timetable) the next station. */
    void showLookahead(Lookahead look) {
        int signal = look.signalDistance < 0 ? 0 : (look.signalClear ? 1 : 2) * 10000 + Mth.clamp((int) Math.round(look.signalDistance / 2) * 2, 0, 9999);
        if (entityData.get(DATA_SIGNAL) != signal) {
            entityData.set(DATA_SIGNAL, signal);
        }
        int points = look.pointsDistance < 0 ? 0 : (look.pointsTurn + 2) * 10000 + Mth.clamp((int) Math.round(look.pointsDistance), 0, 9999);
        if (entityData.get(DATA_POINTS) != points) {
            entityData.set(DATA_POINTS, points);
        }
        if (!autopilot.isOn()) {
            Component status = Component.empty();
            if (look.station != null) {
                int distance = (int) Math.round(look.stationDistance);
                status = Math.abs(distance) <= 2 && Math.abs(entityData.get(DATA_SPEED)) < 0.01
                        ? Component.translatable("hud.reikotrains.station.at", look.station.name())
                        : Component.translatable("hud.reikotrains.station.ahead", look.station.name(), Math.max(0, distance / 5 * 5));
            }
            if (!status.equals(entityData.get(DATA_STATUS))) {
                entityData.set(DATA_STATUS, status);
            }
        }
    }

    /** Puts the timetable's state on the gauges. */
    void updateAutoDisplay() {
        boolean on = autopilot.isOn();
        if (entityData.get(DATA_AUTO) != on) {
            entityData.set(DATA_AUTO, on);
        }
        if (on) {
            Component status = autopilot.status();
            if (!status.equals(entityData.get(DATA_STATUS))) {
                entityData.set(DATA_STATUS, status);
            }
        }
    }

    /** Whether there's a switch in sight ahead (for the gauges). */
    public boolean hasPointsAhead() {
        return entityData.get(DATA_POINTS) != 0;
    }

    /** Which way the next switch ahead is set: -1 left, 0 straight on, +1 right. */
    public int pointsTurn() {
        return entityData.get(DATA_POINTS) / 10000 - 2;
    }

    /** How far ahead the next switch is, in blocks. */
    public int pointsDistance() {
        return entityData.get(DATA_POINTS) % 10000;
    }

    /** The train's speed in km/h, for the speedometer (either way). */
    public float speedKmh() {
        return Math.abs(entityData.get(DATA_SPEED)) * 20 * 3.6F;
    }

    /** Called by the train each tick: its speed, forwards for the locomotive. */
    void reportSpeed(double speed) {
        if (Math.abs(entityData.get(DATA_SPEED) - speed) > 0.002 || (speed == 0 && entityData.get(DATA_SPEED) != 0)) {
            entityData.set(DATA_SPEED, (float) speed);
        }
    }

    /**
     * How hard the locomotive pulls the train this tick (blocks per tick per tick, forwards for the locomotive), given
     * how fast it's going (forwards positive) and how heavy the train is. It works up to the throttle notch's speed
     * and then holds it.
     */
    double traction(double speed, double trainMass) {
        int throttle = workingThrottle();
        if (throttle == 0 || isHeld() || !hasPower()) {
            return 0;
        }
        double target = throttleTarget();
        double pull = pull(trainMass);
        if (target > 0) {
            return speed < target ? Math.min(pull, target - speed + 0.002) : 0;
        }
        return speed > target ? -Math.min(pull, speed - target + 0.002) : 0;
    }

    /** The speed its throttle notch works up to, forwards for it (negative in reverse). */
    double throttleTarget() {
        return maxSpeed() * workingThrottle() / MAX_THROTTLE;
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
        if (!isDriven()) {
            if (getThrottle() != 0) {
                setThrottle(0);
            }
            if (isBraking()) {
                setBraking(false);
            }
            if (getSteer() != 0) {
                setSteer(0);
            }
        } else if (driver != null && getThrottle() != 0 && !hasPower() && --noFuelWarning <= 0) {
            noFuelWarning = 60;
            driver.displayClientMessage(Component.translatable(noFuelMessage()), true);
        }
        boolean lit = hasPower() && isDriven();
        if (entityData.get(DATA_RUNNING) != lit) {
            entityData.set(DATA_RUNNING, lit);
        }
        FuelStore store = fuelStore();
        if (lit && store != null && store.fuel() > 0 && TrainsConfig.NEEDS_FUEL.getAsBoolean()) {
            double rate = 0.1 * idleAppetite() + 0.35 * appetite() * Math.abs(workingThrottle()) / (double) MAX_THROTTLE;
            store.setFuel(store.fuel() - rate * burnScale() * TrainsConfig.FUEL_USE.get());
        }
        showFuel();
        if (whistleCooldown > 0) {
            whistleCooldown--;
        }
    }

    /** What the driver's told when they open the throttle with no fire. */
    protected String noFuelMessage() {
        return "message.reikotrains.no_fuel";
    }

    /** A point on the locomotive: blocks forward of its middle, to the right, and up from the rails. */
    public Vec3 point(double forward, double side, double up) {
        Vec3 f = forward();
        Vec3 flat = flatForward();
        return new Vec3(getX() + f.x * forward - flat.z * side, getY() + f.y * forward + up, getZ() + f.z * forward + flat.x * side);
    }

    /** Client side, every tick: smoke, sounds. */
    protected abstract void clientEffects();

    // ----------------------------------------------------------------------------------------------------------------
    // Clicking

    @Override
    protected InteractionResult interactCar(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (acceptsFuel(stack)) {
            return addFuel(player, hand, stack);
        }
        return board(player);
    }

    /** What it burns: for steam engines, anything in the {@code reikotrains:locomotive_fuel} tag. */
    public boolean acceptsFuel(ItemStack stack) {
        return stack.is(FUEL);
    }

    /** Puts as much of the stack in as will fit. */
    protected InteractionResult addFuel(Player player, InteractionHand hand, ItemStack stack) {
        return shovel(player, hand, stack, this, fuelInlet(), fuelMessage(), fullMessage());
    }

    /** Shovels as much of the stack as will fit into {@code store}, and says how full it is. */
    static InteractionResult shovel(Player player, InteractionHand hand, ItemStack stack, FuelStore store, Vec3 at, String fullness, String full) {
        int burn = stack.getBurnTime(RecipeType.SMELTING);
        if (burn <= 0) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        int room = (int) ((store.fuelCapacity() - store.fuel()) / burn);
        int count = Math.min(room, stack.hasCraftingRemainingItem() ? 1 : stack.getCount());
        if (count <= 0) {
            player.displayClientMessage(Component.translatable(full), true);
            return InteractionResult.CONSUME;
        }
        store.setFuel(store.fuel() + (double) burn * count);
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
        player.level().playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.6F, 0.8F);
        player.displayClientMessage(Component.translatable(fullness, store.fuelPercent()), true);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("Fuel", fuel);
        autopilot.save(tag);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setFuel(tag.getDouble("Fuel"));
        autopilot.load(tag);
        updateAutoDisplay();
    }
}
