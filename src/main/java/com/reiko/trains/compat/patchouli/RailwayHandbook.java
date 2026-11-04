package com.reiko.trains.compat.patchouli;

import com.reiko.trains.ReikoTrains;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import vazkii.patchouli.api.PatchouliAPI;

/**
 * The Railway Handbook, through Patchouli's API. Only {@link com.reiko.trains.guide.Handbook} calls this, and only once
 * it has checked that Patchouli is installed, so this class (and Patchouli's) is never loaded without it.
 */
public final class RailwayHandbook {
    /** The book's id: its book.json is {@code data/reikotrains/patchouli_books/railway_handbook/book.json}. */
    public static final ResourceLocation ID = ReikoTrains.id("railway_handbook");

    private RailwayHandbook() {
    }

    /** A copy of the book. */
    public static ItemStack stack() {
        return PatchouliAPI.get().getBookStack(ID);
    }

    /** Opens the book at page {@code page} of {@code entry}. Client side. */
    public static void openAt(ResourceLocation entry, int page) {
        PatchouliAPI.get().openBookEntry(ID, entry, page);
    }

    /** Opens the book at its front page. Client side. */
    public static void open() {
        PatchouliAPI.get().openBookGUI(ID);
    }
}
