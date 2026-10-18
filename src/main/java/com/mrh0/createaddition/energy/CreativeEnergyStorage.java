package com.mrh0.createaddition.energy;

import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import com.mrh0.createaddition.neoforge.energy.CAEnergy;
import com.mrh0.createaddition.neoforge.energy.EnergyStorage;

public class CreativeEnergyStorage implements EnergyStorage {

	@Override
	public long insert(long maxAmount, TransactionContext transaction) {
		return 0;
	}

	@Override
	public long extract(long maxAmount, TransactionContext transaction) {
		return maxAmount;
	}

	@Override
	public long getAmount() {
		return Long.MAX_VALUE;
	}

	@Override
	public long getCapacity() {
		return Long.MAX_VALUE;
	}

	@Override
	public boolean supportsExtraction() {
		return true;
	}

	@Override
	public boolean supportsInsertion() {
		return false;
	}

	public void outputToSide(Level world, BlockPos pos, Direction side) {
		EnergyStorage ies = CAEnergy.find(world, pos.relative(side), side.getOpposite());
		if(ies == null)
			return;
		try(Transaction t = Transaction.openRoot()) {
			ies.insert(Integer.MAX_VALUE, t);
			t.commit();
		}
	}
}
