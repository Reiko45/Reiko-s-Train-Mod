package com.reiko.trains.client;

import com.reiko.trains.guide.Handbook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * A small button with the Railway Handbook on it, for a corner of the mod's screens: it closes the screen (as Escape
 * would, so a station's name is still saved) and opens the handbook at that screen's page. Only made when Patchouli is
 * installed: see {@link #add}.
 */
public final class HandbookButton extends Button {
    private final String entry;
    private ItemStack icon = ItemStack.EMPTY;

    private HandbookButton(int x, int y, int size, String entry) {
        super(x, y, size, size, Component.translatable("gui.reikotrains.handbook"), button -> {
        }, DEFAULT_NARRATION);
        this.entry = entry;
        setTooltip(Tooltip.create(Component.translatable("gui.reikotrains.handbook.hint")));
    }

    /** A handbook button at {@code x, y}, {@code size} square, opening {@code entry}; or null without Patchouli. */
    public static HandbookButton create(int x, int y, int size, String entry) {
        return Handbook.available() ? new HandbookButton(x, y, size, entry) : null;
    }

    @Override
    public void onPress() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            minecraft.screen.onClose();
        }
        Handbook.open(entry);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int size = getWidth();
        setMessage(Component.empty());
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        setMessage(Component.translatable("gui.reikotrains.handbook"));
        if (icon.isEmpty()) {
            icon = Handbook.book();
        }
        float scale = (size - 2) / 16.0F;
        graphics.pose().pushPose();
        graphics.pose().translate(getX() + 1, getY() + 1, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.renderItem(icon, 0, 0);
        graphics.pose().popPose();
    }
}
