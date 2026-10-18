package com.mrh0.createaddition.index;

import com.mrh0.createaddition.config.CACommonConfig;
import com.mrh0.createaddition.transfer.EnergyTransferable;
import com.zurrtum.create.api.behaviour.BlockEntityBehaviour;
import com.zurrtum.create.neoforge.transfer.CachedFluidInventoryBehaviour;
import com.zurrtum.create.neoforge.transfer.CachedInventoryBehaviour;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// NeoForge: Fabric API lookups became capabilities. Energy goes through Capabilities.Energy, so Pipez, AE2 and
// every other NeoForge energy mod can connect.
public class CATransfer {

	public static void register() {
		BlockEntityBehaviour.add(CABlockEntities.ROLLING_MILL, be -> new CachedInventoryBehaviour<>(be, mill -> mill.capability));
		BlockEntityBehaviour.add(CABlockEntities.LIQUID_BLAZE_BURNER, be -> new CachedFluidInventoryBehaviour<>(be, burner -> burner.getTank().getCapability()));
	}

	public static void registerCapabilities(RegisterCapabilitiesEvent event) {
		List<BlockEntityType<?>> energyTypes = List.of(
				CABlockEntities.ELECTRIC_MOTOR,
				CABlockEntities.ELECTRIC_PUMP,
				CABlockEntities.SERVO_MOTOR,
				CABlockEntities.ALTERNATOR,
				CABlockEntities.CREATIVE_ENERGY,
				CABlockEntities.SMALL_CONNECTOR,
				CABlockEntities.SMALL_LIGHT_CONNECTOR,
				CABlockEntities.LARGE_CONNECTOR,
				CABlockEntities.TESLA_COIL,
				CABlockEntities.MODULAR_ACCUMULATOR,
				CABlockEntities.PORTABLE_ENERGY_INTERFACE);
		for (BlockEntityType<?> type : energyTypes)
			event.registerBlockEntity(Capabilities.Energy.BLOCK, type, CATransfer::findEnergy);
		event.registerBlockEntity(Capabilities.Item.BLOCK, CABlockEntities.ROLLING_MILL, CachedInventoryBehaviour::get);
		event.registerBlockEntity(Capabilities.Fluid.BLOCK, CABlockEntities.LIQUID_BLAZE_BURNER, CachedFluidInventoryBehaviour::get);
		// Replaces TR Energy's SimpleEnergyItem on the capacitor
		event.registerItem(Capabilities.Energy.ITEM, (stack, access) -> new ItemAccessEnergyHandler(access, CADataComponents.ENERGY,
				CACommonConfig.COMMON.CAPACITOR_CAPACITY.get(),
				CACommonConfig.COMMON.CAPACITOR_CHARGE_RATE.get()), CAItems.CAPACITOR);
	}

	@Nullable
	private static EnergyHandler findEnergy(BlockEntity be, @Nullable Direction direction) {
		if (be instanceof EnergyTransferable transferable)
			return transferable.getEnergyStorage(direction);
		return null;
	}
}
