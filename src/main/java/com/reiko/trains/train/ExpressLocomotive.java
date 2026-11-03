package com.reiko.trains.train;

import com.reiko.trains.registry.ModItems;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A big express engine, a 4-6-0, eight blocks long: faster and stronger than the tank engine, and thirstier. It has
 * no coal of its own: it burns what's in the {@link Tender} coupled to it (either end), so it needs one to run.
 * Shovelling fuel into the engine fills its tender.
 */
public class ExpressLocomotive extends SteamLocomotive {
    /** The driver on the left of the cab, the fireman on the right, out to the sides to see past the boiler. */
    private static final Seat[] SEATS = {new Seat(-2.4, -0.7, 1.3), new Seat(-2.4, 0.7, 1.3)};

    public ExpressLocomotive(EntityType<? extends ExpressLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    public double halfLength() {
        return 4.0;
    }

    @Override
    public double bogieOffset() {
        return 2.6;
    }

    @Override
    public double mass() {
        return 4.5;
    }

    @Override
    protected float partHeight() {
        return 3.3F;
    }

    @Override
    public Seat[] seats() {
        return SEATS;
    }

    /** About 58 km/h at the default settings. */
    @Override
    protected double speedFactor() {
        return 1.35;
    }

    @Override
    protected double strength() {
        return 1.8;
    }

    @Override
    protected double appetite() {
        return 1.6;
    }

    @Override
    protected Vec3 chimneyOffset() {
        return new Vec3(3.2, 0, 3.3);
    }

    /** Its driving wheels are 18 pixels across: about 3.5 blocks round. */
    @Override
    protected double chuffSpacing() {
        return 0.88;
    }

    @Override
    protected float whistlePitch() {
        return 0.78F;
    }

    /** The tender coupled to it, either end, if there is one. */
    @Nullable
    public Tender tender() {
        if (level().isClientSide) {
            return null;
        }
        if (Trains.follower(this) instanceof Tender tender) {
            return tender;
        }
        return Trains.leader(this) instanceof Tender tender ? tender : null;
    }

    @Nullable
    @Override
    public FuelStore fuelStore() {
        return tender();
    }

    /** Its water's in its tender too. */
    @Nullable
    @Override
    public WaterStore waterStore() {
        return tender();
    }

    /** It has no tanks of its own. */
    @Override
    public double water() {
        return 0;
    }

    @Override
    public double waterCapacity() {
        return 0;
    }

    @Override
    protected String noFuelMessage() {
        if (tender() == null) {
            return "message.reikotrains.no_tender";
        }
        return outOfWater() && tender().fuel() > 0 ? "message.reikotrains.no_water_tender" : "message.reikotrains.no_fuel_tender";
    }

    /** Water poured into the engine goes into its tender. */
    @Nullable
    @Override
    protected InteractionResult water(Player player, InteractionHand hand) {
        Tender tender = tender();
        InteractionResult result = WaterTank.fillFromHand(player, hand, tender != null ? tender : this, "message.reikotrains.tender_water");
        if (result != null && tender == null && !level().isClientSide) {
            player.displayClientMessage(Component.translatable("message.reikotrains.no_tender"), true);
        }
        return result;
    }

    @Override
    protected InteractionResult addFuel(Player player, InteractionHand hand, ItemStack stack) {
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Tender tender = tender();
        if (tender == null) {
            player.displayClientMessage(Component.translatable("message.reikotrains.no_tender"), true);
            return InteractionResult.CONSUME;
        }
        return shovel(player, hand, stack, tender, point(-3.0, 0, 1.6), "message.reikotrains.tender_fuel", "message.reikotrains.tender_full");
    }

    @Override
    protected Item getDropItem() {
        return ModItems.EXPRESS_LOCOMOTIVE.get();
    }
}
