package com.mrh0.createaddition.blocks.creative_energy;

import java.util.EnumMap;

import com.mrh0.createaddition.energy.CreativeEnergyStorage;
import com.mrh0.createaddition.transfer.EnergyTransferable;
import com.zurrtum.create.content.logistics.crate.CrateBlockEntity;

import com.mrh0.createaddition.neoforge.energy.EnergyLookupCache;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import com.mrh0.createaddition.neoforge.energy.EnergyStorage;

public class CreativeEnergyBlockEntity extends CrateBlockEntity implements EnergyTransferable {

	protected final CreativeEnergyStorage energy;
	private final EnumMap<Direction, EnergyLookupCache> escacheMap = new EnumMap<>(Direction.class);

	public CreativeEnergyBlockEntity(BlockEntityType<?> tileEntityTypeIn, BlockPos pos, BlockState state) {
		super(tileEntityTypeIn, pos, state);
		energy = new CreativeEnergyStorage();
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null) return;
		if (level.isClientSide()) return;

		for (Direction d : Direction.values()) {
			EnergyStorage ies = getCachedEnergy(d);
			if (ies == null) continue;
			try(Transaction t = Transaction.openRoot()) {
				ies.insert(Integer.MAX_VALUE, t);
				t.commit();
			}
		}
	}

	@Nullable
	public EnergyStorage getCachedEnergy(Direction side) {
		if(!(getLevel() instanceof ServerLevel serverLevel))
			return null;
		EnergyLookupCache cache = escacheMap.computeIfAbsent(side,
				side1 -> EnergyLookupCache.create(serverLevel, getBlockPos().relative(side1)));
		return cache.find(side.getOpposite());
	}

	@Override
	public EnergyStorage getEnergyStorage(@Nullable Direction direction) {
		return energy;
	}
}
