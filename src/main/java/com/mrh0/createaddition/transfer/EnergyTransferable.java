package com.mrh0.createaddition.transfer;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import com.mrh0.createaddition.neoforge.energy.EnergyStorage;

public interface EnergyTransferable {
	@Nullable
	EnergyStorage getEnergyStorage(@Nullable Direction direction);
}
