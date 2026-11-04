package com.reiko.trains.guide;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.compat.patchouli.RailwayHandbook;
import com.reiko.trains.config.TrainsConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * The Railway Handbook: the mod's guide book, shown by Patchouli. Everything here is safe to call without Patchouli
 * installed: there's simply no book then.
 *
 * <p>Every player is given one the first time they join a world (once, ever: it's remembered across deaths), it can be
 * crafted from a book and Train Track, and the mod's screens have a button that opens it at their page.
 */
@EventBusSubscriber(modid = ReikoTrains.MODID)
public final class Handbook {
    /** The pages the mod's screens open (their handbook buttons), as entry ids. */
    public static final String TRACK_LAYER = "track/track_layer";
    public static final String SIGNAL_BOX = "signals/signal_box";
    public static final String LOADER_FILTERS = "freight/filters";
    public static final String FREIGHT_WAGON = "wagons/freight_wagon";
    public static final String STATIONS = "stations/stations";
    public static final java.util.List<String> SCREEN_PAGES = java.util.List.of(TRACK_LAYER, SIGNAL_BOX, LOADER_FILTERS, FREIGHT_WAGON, STATIONS);

    /** Set, in the player's persisted data, once they've been given the handbook. */
    static final String GIVEN = "reikotrains_handbook_given";

    private Handbook() {
    }

    /** Whether there's a handbook: whether Patchouli is installed. */
    public static boolean available() {
        return ModList.get() != null && ModList.get().isLoaded("patchouli");
    }

    /** A copy of the handbook, or nothing without Patchouli. */
    public static ItemStack book() {
        return available() ? RailwayHandbook.stack() : ItemStack.EMPTY;
    }

    /** Opens the handbook at {@code entry} (like {@code signals/signal_box}). Client side; does nothing without Patchouli. */
    public static void open(String entry) {
        open(entry, 0);
    }

    /** Opens the handbook at page {@code page} of {@code entry}. Client side; does nothing without Patchouli. */
    public static void open(String entry, int page) {
        if (available()) {
            RailwayHandbook.openAt(ReikoTrains.id(entry), page);
        }
    }

    /** Opens the handbook at its front page. Client side; does nothing without Patchouli. */
    public static void openFront() {
        if (available()) {
            RailwayHandbook.open();
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && TrainsConfig.GIVE_HANDBOOK.getAsBoolean()) {
            giveFirstTime(player);
        }
    }

    /** Gives {@code player} the handbook, unless they've had one before. Returns whether it did. */
    public static boolean giveFirstTime(ServerPlayer player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GIVEN)) {
            return false;
        }
        ItemStack handbook = book();
        if (handbook.isEmpty()) {
            return false;
        }
        if (!player.getInventory().add(handbook)) {
            player.drop(handbook, false);
        }
        persisted.putBoolean(GIVEN, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        return true;
    }

    /** The id of the handbook, for recipes and tests. */
    public static ResourceLocation id() {
        return ReikoTrains.id("railway_handbook");
    }
}
