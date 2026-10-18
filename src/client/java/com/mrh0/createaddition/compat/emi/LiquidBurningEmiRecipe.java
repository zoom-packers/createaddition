package com.mrh0.createaddition.compat.emi;

import com.mrh0.createaddition.CreateAddition;
import com.mrh0.createaddition.index.CAItems;
import com.mrh0.createaddition.recipe.liquid_burning.LiquidBurningRecipe;
import com.mrh0.createaddition.util.Util;
import com.zurrtum.create.client.foundation.gui.AllGuiTextures;
import com.zurrtum.create.client.foundation.utility.CreateLang;
import com.zurrtum.create.content.processing.recipe.HeatCondition;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.util.List;

/**
 * Fuel for a blaze burner that was given a straw: upstream's JEI {@code LiquidBurningCategory}. The fuel goes in by
 * bucket or by pipe, so the page shows the straw, the buckets and the fluid. The burn time is per bucket.
 */
public class LiquidBurningEmiRecipe extends BasicEmiRecipe {
    private final LiquidBurningRecipe recipe;
    private final EmiIngredient straw = EmiStack.of(CAItems.STRAW);
    private final EmiIngredient buckets;
    private final EmiIngredient fluid;

    public LiquidBurningEmiRecipe(Identifier id, LiquidBurningRecipe recipe) {
        super(CreateAdditionEmiPlugin.LIQUID_BURNING, id, CAEmiWidgets.WIDTH, CAEmiWidgets.HEIGHT);
        this.recipe = recipe;
        this.buckets = EmiIngredient.of(recipe.fluidIngredient().getMatchingFluids().stream()
            .map(Fluid::getBucket)
            .map(ItemStack::new)
            .filter(bucket -> !bucket.isEmpty())
            .map(EmiStack::of)
            .toList());
        this.fluid = CAEmiWidgets.fluid(recipe.fluidIngredient().getMatchingFluidStacks(), recipe.fluidIngredient().amount());
        // The burner keeps its straw
        catalysts.add(straw);
        if (!buckets.isEmpty()) inputs.add(buckets);
        inputs.add(fluid);
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        HeatCondition heat = recipe.superheated() ? HeatCondition.SUPERHEATED : HeatCondition.HEATED;
        widgets.addSlot(straw, 31, 2).catalyst(true);
        widgets.addSlot(buckets, 51, 2);
        widgets.addSlot(fluid, 71, 2);

        CAEmiWidgets.texture(widgets, AllGuiTextures.JEI_HEAT_BAR, 4, 30);
        CAEmiWidgets.texture(widgets, AllGuiTextures.JEI_LIGHT, 81, 38);
        CAEmiWidgets.blazeBurner(widgets, heat.visualizeAsBlazeBurner(), 91, 19);
        CAEmiWidgets.texture(widgets, AllGuiTextures.JEI_DOWN_ARROW, 91, 8);
        widgets.addText(CreateLang.translateDirect(heat.getTranslationKey()), 9, 36, 0xFF000000 | heat.getColor(), true);

        widgets.addText(Component.literal(Util.formatTime(recipe.burnTime())), 136, 36, CAEmiWidgets.TEXT_COLOR, true);
        widgets.addTooltipText(List.of(
            Component.translatable(CreateAddition.MODID + ".recipe.liquid_burning.burn_time"),
            Component.translatable(CreateAddition.MODID + ".recipe.liquid_burning.ticks", String.format("%,d", recipe.burnTime()))
        ), 134, 34, 30, 12);
    }
}
