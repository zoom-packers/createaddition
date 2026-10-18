package com.mrh0.createaddition.compat.emi;

import com.mrh0.createaddition.CreateAddition;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiRenderable;
import net.minecraft.network.chat.Component;

/**
 * A category titled with the mod's own {@code createaddition.recipe.<name>} keys instead of EMI's
 * {@code emi.category.*} keys.
 */
public class CAEmiCategory extends EmiRecipeCategory {
    public CAEmiCategory(String name, EmiRenderable icon) {
        super(CreateAddition.asResource(name), icon);
    }

    @Override
    public Component getName() {
        return Component.translatable(CreateAddition.MODID + ".recipe." + getId().getPath());
    }
}
