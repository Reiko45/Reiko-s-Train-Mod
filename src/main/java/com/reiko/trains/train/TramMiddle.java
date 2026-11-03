package com.reiko.trains.train;

import com.reiko.trains.registry.ModItems;
import java.util.List;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The low-floor tram's middle section: three and a half blocks, with a pair of doors and four seats, joined to the
 * driving sections either side by bellows so the tram bends round street corners.
 */
public class TramMiddle extends TrainCarEntity implements StreetRunning, LowFloorTram.Section {
    private static final Seat[] SEATS = {TramCab.seat(-18, -1), TramCab.seat(-18, 1), TramCab.seat(10, -1), TramCab.seat(10, 1)};

    private boolean breakingUp;

    public TramMiddle(EntityType<? extends TramMiddle> type, Level level) {
        super(type, level);
    }

    @Override
    public void markBreakingUp() {
        breakingUp = true;
    }

    @Override
    public void remove(RemovalReason reason) {
        List<TrainCarEntity> set = reason.shouldDestroy() && !breakingUp && !level().isClientSide ? LowFloorTram.sections(this) : List.of();
        super.remove(reason);
        LowFloorTram.removeOthers(this, set);
    }

    @Override
    public double halfLength() {
        return 1.75;
    }

    @Override
    public double bogieOffset() {
        return 0.9;
    }

    @Override
    public double mass() {
        return 0.8;
    }

    @Override
    protected float partHeight() {
        return 2.7F;
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
        return ModItems.LOW_FLOOR_TRAM.get();
    }
}
