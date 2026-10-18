package com.mrh0.createaddition.neoforge;

import com.mrh0.createaddition.CreateAddition;
import com.mrh0.createaddition.fluid.CAFluidEntry;
import com.mrh0.createaddition.index.CAFluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * NeoForge requires every mod fluid to return a {@link FluidType}. Properties follow upstream's NeoForge 1.21.1
 * originals (mrh0/createaddition branch 1.21.1).
 */
public final class CAFluidTypes {
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, CreateAddition.MODID);

    private CAFluidTypes() {
    }

    private static void type(CAFluidEntry entry, String name, FluidType.Properties properties) {
        properties.descriptionId("fluid." + CreateAddition.MODID + "." + name).supportsBoating(false);
        entry.fluidType = TYPES.register(name, () -> new FluidType(properties));
    }

    public static void register(IEventBus modBus) {
        type(CAFluids.SEED_OIL_ENTRY, "seed_oil", FluidType.Properties.create().viscosity(2000).density(1400));
        type(CAFluids.BIOETHANOL_ENTRY, "bioethanol", FluidType.Properties.create().viscosity(2500).density(1600));
        TYPES.register(modBus);
    }
}
