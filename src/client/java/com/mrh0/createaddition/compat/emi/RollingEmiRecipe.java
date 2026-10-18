package com.mrh0.createaddition.compat.emi;

import com.mrh0.createaddition.recipe.rolling.RollingRecipe;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.resources.Identifier;

/** The rolling mill: upstream's JEI {@code RollingMillCategory}. */
public class RollingEmiRecipe extends BasicEmiRecipe {
    public RollingEmiRecipe(Identifier id, RollingRecipe recipe) {
        super(CreateAdditionEmiPlugin.ROLLING, id, CAEmiWidgets.WIDTH, CAEmiWidgets.HEIGHT);
        inputs.add(EmiIngredient.of(recipe.ingredient()));
        recipe.results().forEach(result -> outputs.add(CAEmiWidgets.output(result)));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        CAEmiWidgets.machine(widgets, false);
        widgets.addSlot(inputs.getFirst(), 14, 8);
        widgets.addSlot(outputs.getFirst(), 139, 27).recipeContext(this);
    }
}
