package com.reiko.trains.train;

import com.reiko.trains.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A guard's van, for the back of a train: a little wooden van on four wheels with a veranda at one end, a stove
 * inside (it smokes when someone's aboard) and red tail lamps. Its brake is worked with the train's, so a train with
 * one stops quicker; and with no engine on the train, the guard's handbrake holds it.
 */
public class GuardsVan extends TrainCarEntity {
    /** How much stronger a train's brakes are with a van on it (two vans count, at most). */
    public static final double BRAKE_BONUS = 0.5;
    /** The guard by the stove, and a seat by the lookout window. */
    private static final Seat[] SEATS = {new Seat(0.6, -0.45, 1.05), new Seat(-0.6, 0.45, 1.05)};

    private int smoke;

    public GuardsVan(EntityType<? extends GuardsVan> type, Level level) {
        super(type, level);
    }

    @Override
    public double halfLength() {
        return 2.5;
    }

    @Override
    public double bogieOffset() {
        return 1.25;
    }

    /** Ballasted, to make the most of its brake. */
    @Override
    public double mass() {
        return 2.0;
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
    protected InteractionResult interactCar(Player player, InteractionHand hand) {
        return board(player);
    }

    @Override
    protected Item getDropItem() {
        return ModItems.GUARDS_VAN.get();
    }

    /** Smoke from the stove pipe while someone's aboard. */
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && isVehicle() && ++smoke >= 8) {
            smoke = 0;
            Vec3 f = flatForward();
            Vec3 pipe = new Vec3(getX() + f.x * 0.9 + f.z * 0.4, getY() + 3.1, getZ() + f.z * 0.9 - f.x * 0.4);
            level().addParticle(ParticleTypes.SMOKE, pipe.x, pipe.y, pipe.z, 0, 0.05, 0);
            if (random.nextInt(4) == 0) {
                level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, pipe.x, pipe.y, pipe.z, 0, 0.03, 0);
            }
        }
    }
}
