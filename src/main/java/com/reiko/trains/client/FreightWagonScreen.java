package com.reiko.trains.client;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.menu.FreightWagonMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** The freight wagon's 108 slots, all on screen at once: a double chest's height, twice as wide. */
public class FreightWagonScreen extends AbstractContainerScreen<FreightWagonMenu> {
    private static final ResourceLocation BACKGROUND = ReikoTrains.id("textures/gui/freight_wagon.png");

    public FreightWagonScreen(FreightWagonMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = FreightWagonMenu.WIDTH;
        imageHeight = FreightWagonMenu.HEIGHT;
        inventoryLabelX = FreightWagonMenu.INVENTORY_X;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        HandbookButton handbook = HandbookButton.create(leftPos + imageWidth - 19, topPos + 3, 14, com.reiko.trains.guide.Handbook.FREIGHT_WAGON);
        if (handbook != null) {
            addRenderableWidget(handbook);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 512, 256);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
