package com.reiko.trains.client;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.menu.WagonLoaderMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** A wagon loader's screen: its filter, and buttons for loading/unloading and for what the filter does. */
public class WagonLoaderScreen extends AbstractContainerScreen<WagonLoaderMenu> {
    private static final ResourceLocation BACKGROUND = ReikoTrains.id("textures/gui/wagon_loader.png");
    private Button mode;
    private Button filter;

    public WagonLoaderScreen(WagonLoaderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        mode = addRenderableWidget(Button.builder(Component.empty(), b -> press(WagonLoaderMenu.BUTTON_MODE))
                .bounds(leftPos + 7, topPos + 42, 78, 20).build());
        filter = addRenderableWidget(Button.builder(Component.empty(), b -> press(WagonLoaderMenu.BUTTON_FILTER))
                .bounds(leftPos + 91, topPos + 42, 78, 20).build());
        HandbookButton handbook = HandbookButton.create(leftPos + imageWidth - 19, topPos + 3, 14, com.reiko.trains.guide.Handbook.LOADER_FILTERS);
        if (handbook != null) {
            addRenderableWidget(handbook);
        }
        refresh();
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
    }

    /** The buttons say what the loader's doing now (it can change from the server, say on a sneak-click). */
    private void refresh() {
        boolean loading = menu.isLoading();
        mode.setMessage(Component.translatable(loading ? "screen.reikotrains.loader.loading" : "screen.reikotrains.loader.unloading")
                .withStyle(loading ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        mode.setTooltip(Tooltip.create(Component.translatable(loading ? "message.reikotrains.loader.load" : "message.reikotrains.loader.unload")));
        boolean deny = menu.isDeny();
        filter.setMessage(Component.translatable(deny ? "screen.reikotrains.loader.deny" : "screen.reikotrains.loader.allow"));
        filter.setTooltip(Tooltip.create(Component.translatable(deny ? "screen.reikotrains.loader.deny.tooltip" : "screen.reikotrains.loader.allow.tooltip")));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hoveredSlot instanceof WagonLoaderMenu.FilterSlot && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            graphics.renderTooltip(font, Component.translatable("screen.reikotrains.loader.slot"), mouseX, mouseY);
        } else {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }
}
