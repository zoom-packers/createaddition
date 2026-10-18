package com.mrh0.createaddition.compat.emi;

import com.mrh0.createaddition.index.CABlocks;
import com.mrh0.createaddition.index.CAItems;
import com.mrh0.createaddition.index.CARecipes;
import com.zurrtum.create.AllItems;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * NeoForge: recipe viewer pages for SEMI, ZP5's EMI port. Replaces upstream's Fabric-only Create REI Viewer plugin
 * (compat/viewer); the layouts follow upstream's NeoForge 1.21.1 JEI categories. EMI finds this class through the
 * annotation, so nothing else loads it and the mod still runs without EMI.
 */
@EmiEntrypoint
public class CreateAdditionEmiPlugin implements EmiPlugin {
    public static final CAEmiCategory CHARGING = new CAEmiCategory("charging", EmiStack.of(CABlocks.TESLA_COIL));
    public static final CAEmiCategory ROLLING = new CAEmiCategory("rolling", EmiStack.of(CABlocks.ROLLING_MILL));
    public static final CAEmiCategory LIQUID_BURNING = new CAEmiCategory("liquid_burning", EmiStack.of(AllItems.BLAZE_BURNER));
    // Create's sequenced assembly registers its steps as recipes too; they belong on the sequence's own page
    private static final Pattern SEQUENCE_STEP = Pattern.compile("sequenced_assembly_\\d+_.+_\\d+");

    @Override
    public void register(EmiRegistry registry) {
        registry.addCategory(CHARGING);
        registry.addCategory(ROLLING);
        registry.addCategory(LIQUID_BURNING);

        registry.addWorkstation(CHARGING, EmiStack.of(CABlocks.TESLA_COIL));
        registry.addWorkstation(ROLLING, EmiStack.of(CABlocks.ROLLING_MILL));
        // The liquid blaze burner has no item of its own: it is a blaze burner given a straw
        registry.addWorkstation(LIQUID_BURNING, EmiStack.of(AllItems.BLAZE_BURNER));
        registry.addWorkstation(LIQUID_BURNING, EmiStack.of(CAItems.STRAW));

        // The recipes reach the client through CreateAdditionNeoForge#sendRecipes
        RecipeMap recipes = registry.getRecipeMap();
        if (recipes == null) return;
        forEach(recipes, CARecipes.CHARGING_TYPE, holder -> {
            if (standalone(holder)) registry.addRecipe(new ChargingEmiRecipe(holder.id().identifier(), holder.value()));
        });
        forEach(recipes, CARecipes.ROLLING_TYPE, holder ->
            registry.addRecipe(new RollingEmiRecipe(holder.id().identifier(), holder.value())));
        forEach(recipes, CARecipes.LIQUID_BURNING_TYPE, holder -> {
            if (!holder.value().fluidIngredient().getMatchingFluids().isEmpty())
                registry.addRecipe(new LiquidBurningEmiRecipe(holder.id().identifier(), holder.value()));
        });
    }

    private static <I extends RecipeInput, T extends Recipe<I>> void forEach(RecipeMap recipes, RecipeType<T> type, Consumer<RecipeHolder<T>> action) {
        recipes.byType(type).forEach(action);
    }

    private static boolean standalone(RecipeHolder<?> holder) {
        Identifier id = holder.id().identifier();
        return !id.getNamespace().equals("create") || !SEQUENCE_STEP.matcher(id.getPath()).matches();
    }
}
