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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * What locomotives run on: the fuels (or fluids, or where the power comes from) down the left, each with a tooltip
 * saying how long it lasts; an arrow; the locomotives that take them on the right; a line or two underneath.
 */
final class LocomotiveFuelCategory implements IRecipeCategory<LocomotiveFuel> {
    static final int WIDTH = 164;
    static final int HEIGHT = 64;
    static final int PER_ROW = 5;
    private static final int ARROW_X = 96;
    private static final int ARROW_Y = 10;
    private static final int ENGINES_X = 126;

    private final IDrawable icon;
    private final IDrawableStatic arrow;

    LocomotiveFuelCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModItems.STEAM_LOCOMOTIVE.get()));
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<LocomotiveFuel> getRecipeType() {
        return ReikoTrainsJeiPlugin.LOCOMOTIVE_FUEL;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.reikotrains.locomotive_fuel");
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
    public void setRecipe(IRecipeLayoutBuilder builder, LocomotiveFuel recipe, IFocusGroup focuses) {
        int i = 0;
        for (LocomotiveFuel.Slot slot : recipe.fuels()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 1 + (i % PER_ROW) * 18, 1 + (i / PER_ROW) * 18)
                    .setStandardSlotBackground()
                    .addItemStacks(slot.items())
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(LocomotiveFuel.shown(slot.note(), true).copy().withStyle(ChatFormatting.GRAY)));
            i++;
        }
        for (LocomotiveFuel.FluidSlot slot : recipe.fluids()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 1 + (i % PER_ROW) * 18, 1 + (i / PER_ROW) * 18)
                    .setStandardSlotBackground()
                    .addFluidStack(slot.fluid(), 1000)
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(LocomotiveFuel.shown(slot.note(), true).copy().withStyle(ChatFormatting.GRAY)));
            i++;
        }
        int j = 0;
        for (ItemStack engine : recipe.engines()) {
            builder.addSlot(RecipeIngredientRole.CATALYST, ENGINES_X + (j % 2) * 18, 1 + (j / 2) * 18)
                    .setStandardSlotBackground()
                    .addItemStack(engine);
            j++;
        }
    }

    @Override
    public void draw(LocomotiveFuel recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        arrow.draw(graphics, ARROW_X, ARROW_Y);
        Font font = Minecraft.getInstance().font;
        int y = HEIGHT - 9 * recipe.lines().size();
        for (java.util.function.DoubleFunction<Component> line : recipe.lines()) {
            graphics.drawString(font, LocomotiveFuel.shown(line, false), 0, y, 0xFF606060, false);
            y += 9;
        }
    }
}
