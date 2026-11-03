package com.reiko.trains.train;

import com.reiko.trains.registry.ModItems;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * A livestock wagon: a roofed van with slatted sides and six stalls, for cows, sheep, pigs, horses and any other
 * animal. Lead animals up to it and click it, and they go in (you keep the leads); sneak-click it empty-handed and they
 * come out beside you. A wagon loader beside the track loads the animals standing around it into a train's livestock
 * wagons, or lets them out behind it. Aboard, they come to no harm from walls, falls or crowding, and they ride facing
 * along the wagon.
 */
public class LivestockWagon extends TrainCarEntity {
    public static final int STALLS = 6;
    /** How high the wagon's floor is above the rails, in blocks. */
    private static final double FLOOR = 17.0 / 16;
    /** Three rows of two, along the wagon. */
    private static final Seat[] SEATS = {
            new Seat(1.6, -0.45, FLOOR), new Seat(1.6, 0.45, FLOOR),
            new Seat(0, -0.45, FLOOR), new Seat(0, 0.45, FLOOR),
            new Seat(-1.6, -0.45, FLOOR), new Seat(-1.6, 0.45, FLOOR)};
    /** How far from the player leading them animals on leads can be, and still be put in. */
    private static final double LEAD_REACH = 10;

    public LivestockWagon(EntityType<? extends LivestockWagon> type, Level level) {
        super(type, level);
    }

    @Override
    public double halfLength() {
        return 2.75;
    }

    @Override
    public double bogieOffset() {
        return 1.75;
    }

    /** Heavier the more animals are aboard. */
    @Override
    public double mass() {
        return 1.2 + 0.3 * animals();
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
    protected Item getDropItem() {
        return ModItems.LIVESTOCK_WAGON.get();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // The animals

    /** Whether this can go in a livestock wagon: an animal, not someone's pet, not already riding or being ridden. */
    public static boolean canCarry(Entity entity) {
        return entity instanceof Animal animal && animal.isAlive() && !animal.isPassenger() && !animal.isVehicle()
                && !(animal instanceof TamableAnimal pet && pet.isTame());
    }

    /** How many animals are aboard. */
    public int animals() {
        return getPassengers().size();
    }

    public boolean hasRoom() {
        return animals() < STALLS;
    }

    /** The animals aboard. */
    public List<Animal> livestock() {
        return getPassengers().stream().filter(Animal.class::isInstance).map(Animal.class::cast).toList();
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof Animal && super.canAddPassenger(passenger);
    }

    /** Puts an animal in a free stall; false if there isn't one (or it can't go). */
    public boolean load(Animal animal) {
        if (!hasRoom() || !canCarry(animal)) {
            return false;
        }
        return animal.startRiding(this, true);
    }

    /** Lets an animal out, standing at {@code at}. */
    public void unload(Animal animal, Vec3 at) {
        animal.stopRiding();
        animal.teleportTo(at.x, at.y, at.z);
        animal.setDeltaMovement(Vec3.ZERO);
    }

    /** They ride facing along the wagon. */
    @Override
    protected void positionRider(Entity passenger, MoveFunction callback) {
        super.positionRider(passenger, callback);
        if (passenger instanceof Mob mob) {
            float yaw = getYRot();
            mob.setYRot(yaw);
            mob.setYBodyRot(yaw);
            mob.setYHeadRot(yaw);
        }
    }

    /**
     * Sneak-clicking it empty-handed lets the animals out beside you. Clicking it while leading animals puts them in (as
     * many as there's room for), and gives back the leads. Otherwise it says how many stalls are taken.
     */
    @Override
    protected InteractionResult interactCar(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive() && player.getItemInHand(hand).isEmpty()) {
            if (level().isClientSide) {
                return InteractionResult.SUCCESS;
            }
            List<Animal> aboard = livestock();
            for (Animal animal : aboard) {
                unload(animal, player.position());
            }
            player.displayClientMessage(Component.translatable("message.reikotrains.livestock.out", aboard.size()), true);
            if (!aboard.isEmpty()) {
                level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.NEUTRAL, 0.8F, 0.8F);
            }
            return InteractionResult.CONSUME;
        }
        List<Animal> led = level().getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(LEAD_REACH),
                animal -> animal.isLeashed() && animal.getLeashHolder() == player && canCarry(animal));
        if (!led.isEmpty()) {
            if (level().isClientSide) {
                return InteractionResult.SUCCESS;
            }
            int boarded = 0;
            for (Animal animal : led) {
                if (!hasRoom()) {
                    break;
                }
                animal.dropLeash(true, false);
                if (!player.getAbilities().instabuild && !player.addItem(new ItemStack(Items.LEAD))) {
                    player.drop(new ItemStack(Items.LEAD), false);
                }
                if (load(animal)) {
                    boarded++;
                }
            }
            player.displayClientMessage(boarded > 0
                    ? Component.translatable("message.reikotrains.livestock.in", boarded, animals(), STALLS)
                    : Component.translatable("message.reikotrains.livestock.full"), true);
            if (boarded > 0) {
                level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.NEUTRAL, 0.8F, 0.8F);
            }
            return InteractionResult.CONSUME;
        }
        if (!level().isClientSide) {
            player.displayClientMessage(Component.translatable("message.reikotrains.livestock.stalls", animals(), STALLS), true);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Animals aboard come to no harm from walls, falls, crowding or flying into things. */
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().getVehicle() instanceof LivestockWagon && (event.getSource().is(DamageTypes.IN_WALL)
                || event.getSource().is(DamageTypes.CRAMMING) || event.getSource().is(DamageTypeTags.IS_FALL)
                || event.getSource().is(DamageTypes.FLY_INTO_WALL))) {
            event.setCanceled(true);
        }
    }
}
