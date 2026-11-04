package com.reiko.trains.dev;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.compat.jei.ReikoTrainsJeiPlugin;
import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * DEV ONLY. Opens JEI's pages for the showcase. Kept apart from {@link DevShowcase} (its signatures are free of JEI's
 * types), so the dev client still starts without JEI ({@code -PnoOptionalMods}); then these do nothing.
 */
final class ShowcaseJei {
    private ShowcaseJei() {
    }

    /** JEI's recipes ({@code recipes}) or uses of a stack. */
    static void show(ItemStack stack, boolean recipes) {
        if (ModList.get().isLoaded("jei")) {
            Pages.show(stack, recipes);
        }
    }

    static void fuel() {
        if (ModList.get().isLoaded("jei")) {
            Pages.type(ReikoTrainsJeiPlugin.LOCOMOTIVE_FUEL);
        }
    }

    static void trackLaying() {
        if (ModList.get().isLoaded("jei")) {
            Pages.type(ReikoTrainsJeiPlugin.TRACK_LAYING);
        }
    }

    /** Only loaded once JEI is known to be there. */
    private static final class Pages {
        static void show(ItemStack stack, boolean recipes) {
            IJeiRuntime runtime = ReikoTrainsJeiPlugin.runtime();
            if (runtime == null) {
                ReikoTrains.LOGGER.warn("Showcase book: JEI isn't running");
                return;
            }
            var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(recipes ? RecipeIngredientRole.OUTPUT : RecipeIngredientRole.INPUT,
                    VanillaTypes.ITEM_STACK, stack);
            runtime.getRecipesGui().show(focus);
        }

        static void type(RecipeType<?> type) {
            IJeiRuntime runtime = ReikoTrainsJeiPlugin.runtime();
            if (runtime != null) {
                runtime.getRecipesGui().showTypes(List.of(type));
            }
        }
    }
}
