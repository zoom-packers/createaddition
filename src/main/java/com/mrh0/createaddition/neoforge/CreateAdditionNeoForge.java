package com.mrh0.createaddition.neoforge;

import com.mrh0.createaddition.CreateAddition;
import com.mrh0.createaddition.compat.computercraft.Peripherals;
import com.mrh0.createaddition.index.CABlocks;
import com.mrh0.createaddition.index.CAItems;
import com.mrh0.createaddition.index.CATransfer;
import com.mrh0.createaddition.item.BiomassPelletItem;
import com.mrh0.createaddition.network.CANetwork;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProviders;
import net.minecraft.world.level.storage.loot.providers.number.floats.ResolvableFloat;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * NeoForge entrypoint, replacing the Fabric {@code main} entrypoint.
 * <p>
 * Blocks and fluids are still registered from Create's {@code CreateRegisterPlugin} hook during vanilla bootstrap
 * ({@code CreateAdditionPlugin}, listed in {@code META-INF/services}). Everything upstream registered in
 * {@code onInitialize} is registered from the first {@link RegisterEvent}, because NeoForge freezes the registries
 * between bootstrap and its register events.
 */
@Mod(CreateAddition.MODID)
public class CreateAdditionNeoForge {
    public CreateAdditionNeoForge(IEventBus modBus, ModContainer container) {
        CreateAddition.onInitialize();
        CAFluidTypes.register(modBus);
        CAGameTests.register(modBus);
        modBus.addListener(RegisterEvent.class, event -> CreateAddition.registerContent());
        modBus.addListener(CreateAdditionNeoForge::registerCapabilities);
        modBus.addListener(RegisterPayloadHandlersEvent.class, CANetwork::register);
        modBus.addListener(CreateAdditionNeoForge::addFuels);
        NeoForge.EVENT_BUS.addListener(CreateAdditionNeoForge::sendRecipes);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        CATransfer.registerCapabilities(event);
        if (CreateAddition.CC_ACTIVE) Peripherals.register(event);
    }

    /**
     * 26.3 replaced {@code FuelValues} (Fabric's FuelValueEvents) with the cooking_fuel component.
     */
    private static void addFuels(ModifyDefaultComponentsEvent event) {
        addFuel(event, CAItems.BIOMASS_PELLET, BiomassPelletItem.BURN_TIME);
        addFuel(event, CABlocks.BIOMASS_PALLET.asItem(), BiomassPelletItem.BURN_TIME * 9);
    }

    private static void addFuel(ModifyDefaultComponentsEvent event, Item item, int burnTime) {
        event.modify(item, (components, context, target) -> components.set(
            DataComponents.COOKING_FUEL,
            new CookingFuel(
                new ResolvableInt.Constant(burnTime),
                ResolvableFloat.fromKey(ContextFloatProviders.COOKING_DEFAULT_SPEED_MULTIPLIER)
            )
        ));
    }

    /**
     * Vanilla 26.x no longer sends recipes to clients. Upstream asked Fabric API to sync them for recipe viewers.
     */
    private static void sendRecipes(OnDatapackSyncEvent event) {
        BuiltInRegistries.RECIPE_TYPE.entrySet().forEach(entry -> {
            if (entry.getKey().identifier().getNamespace().equals(CreateAddition.MODID)) {
                event.sendRecipes(entry.getValue());
            }
        });
    }
}
