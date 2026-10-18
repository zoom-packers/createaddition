package com.mrh0.createaddition.compat.emi;

import com.mrh0.createaddition.CreateAddition;
import com.mrh0.createaddition.recipe.charging.ChargingRecipe;
import com.mrh0.createaddition.util.Util;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/** A tesla coil charging the item under it: upstream's JEI {@code ChargingCategory}. */
public class ChargingEmiRecipe extends BasicEmiRecipe {
    private final ChargingRecipe recipe;

    public ChargingEmiRecipe(Identifier id, ChargingRecipe recipe) {
        super(CreateAdditionEmiPlugin.CHARGING, id, CAEmiWidgets.WIDTH, CAEmiWidgets.HEIGHT);
        this.recipe = recipe;
        inputs.add(EmiIngredient.of(recipe.ingredient()));
        recipe.results().forEach(result -> outputs.add(CAEmiWidgets.output(result)));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        CAEmiWidgets.machine(widgets, true);
        widgets.addSlot(inputs.getFirst(), 14, 8);
        widgets.addSlot(outputs.getFirst(), 139, 27).recipeContext(this);
        widgets.addText(Component.literal(Util.format(recipe.energy()) + "⚡"), 86, 9, CAEmiWidgets.TEXT_COLOR, true);
        widgets.addTooltipText(List.of(
            Component.translatable(CreateAddition.MODID + ".recipe.charging.energy", String.format("%,d", recipe.energy())),
            Component.translatable(CreateAddition.MODID + ".recipe.charging.max_charge_rate", String.format("%,d", recipe.maxChargeRate()))
        ), 84, 7, 48, 12);
    }
}
