package com.reiko.trains.compat.jei;

import com.reiko.trains.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** The track-laying wagon: what it lays, and what it fills dips with, in a grid; the wagon on the right; a line or two under. */
final class TrackLayingCategory implements IRecipeCategory<TrackLaying> {
    static final int WIDTH = 160;
    static final int HEIGHT = 76;
    static final int PER_ROW = 6;
    private static final int ARROW_X = 114;
    private static final int ARROW_Y = 10;
    private static final int WAGON_X = 142;

    private final IDrawable icon;
    private final IDrawableStatic arrow;

    TrackLayingCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModItems.TRACK_LAYER.get()));
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<TrackLaying> getRecipeType() {
        return ReikoTrainsJeiPlugin.TRACK_LAYING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("item.reikotrains.track_layer");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, TrackLaying recipe, IFocusGroup focuses) {
        int i = 0;
        for (ItemStack item : recipe.items()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 1 + (i % PER_ROW) * 18, 1 + (i / PER_ROW) * 18)
                    .setStandardSlotBackground()
                    .addItemStack(item);
            i++;
        }
        builder.addSlot(RecipeIngredientRole.CATALYST, WAGON_X, ARROW_Y)
                .setStandardSlotBackground()
                .addItemStack(new ItemStack(ModItems.TRACK_LAYER.get()));
    }

    @Override
    public void draw(TrackLaying recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        arrow.draw(graphics, ARROW_X - 2, ARROW_Y + 1);
        Font font = Minecraft.getInstance().font;
        int y = HEIGHT - 9 * recipe.lines().size();
        for (Component line : recipe.lines()) {
            graphics.drawString(font, line, 0, y, 0xFF606060, false);
            y += 9;
        }
    }
}
