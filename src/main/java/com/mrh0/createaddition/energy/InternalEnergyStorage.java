package com.mrh0.createaddition.energy;

import com.mrh0.createaddition.neoforge.energy.EnergyPreconditions;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import com.mrh0.createaddition.neoforge.energy.CAEnergy;
import com.mrh0.createaddition.neoforge.energy.EnergyStorage;

public class InternalEnergyStorage extends SnapshotJournal<Long> implements EnergyStorage {
    public long amount = 0;
    public long capacity;
    public final long maxReceive, maxExtract;

    public InternalEnergyStorage(long capacity) {
        this(capacity, capacity, capacity);
    }

    public InternalEnergyStorage(long capacity, long maxTransfer) {
        this(capacity, maxTransfer, maxTransfer);
    }

    public InternalEnergyStorage(long capacity, long maxReceive, long maxExtract) {
        EnergyPreconditions.notNegative(capacity);
        EnergyPreconditions.notNegative(maxReceive);
        EnergyPreconditions.notNegative(maxExtract);

        this.capacity = capacity;
        this.maxReceive = maxReceive;
        this.maxExtract = maxExtract;
    }

    public InternalEnergyStorage(long capacity, long maxReceive, long maxExtract, long energy) {
        this(capacity, maxReceive, maxExtract);
        this.amount = energy;
    }

    @Override
    protected Long createSnapshot() {
        return amount;
    }

    @Override
    protected void revertToSnapshot(Long snapshot) {
        amount = snapshot;
    }

    public void write(ValueOutput nbt) {
        if (amount > Integer.MAX_VALUE) {
            nbt.putLong("energy", amount);
        } else {
            nbt.putInt("energy", (int) amount);
        }
    }

    public void read(ValueInput nbt) {
        setEnergy(nbt.getLongOr("energy", 0L));
    }

    public long getSpace() {
        return Math.max(getCapacity() - getAmount(), 0);
    }

    @Override
    public boolean supportsExtraction() {
        return maxExtract > 0;
    }

    @Override
    public boolean supportsInsertion() {
        return maxReceive > 0;
    }

    @Override
    public long insert(long maxAmount, TransactionContext transaction) {
        EnergyPreconditions.notNegative(maxAmount);

        long inserted = Math.min(maxReceive, Math.min(maxAmount, capacity - amount));

        if (inserted > 0) {
            updateSnapshots(transaction);
            amount += inserted;
            return inserted;
        }

        return 0;
    }

    @Override
    public long extract(long maxAmount, TransactionContext transaction) {
        EnergyPreconditions.notNegative(maxAmount);

        long extracted = Math.min(maxExtract, Math.min(maxAmount, amount));

        if (extracted > 0) {
            updateSnapshots(transaction);
            amount -= extracted;
            return extracted;
        }

        return 0;
    }

    public long internalConsumeEnergy(long consume) {
        long oenergy = amount;
        amount = Math.max(0, amount - consume);
        return oenergy - amount;
    }

    public long internalProduceEnergy(long produce) {
        long oenergy = amount;
        amount = Math.min(capacity, amount + produce);
        return oenergy - amount;
    }

    public void setEnergy(long energy) {
    	this.amount = energy;
    }

    public void setCapacity(long capacity) {
    	this.capacity = capacity;
    }

    @Override
    public long getAmount() {
        return amount;
    }

    @Override
    public long getCapacity() {
        return capacity;
    }

    @Deprecated
    public void outputToSide(Level world, BlockPos pos, Direction side, int max) {
		EnergyStorage ies = CAEnergy.find(world, pos.relative(side), side.getOpposite());
		if(ies == null)
			return;
        try(Transaction t = Transaction.openRoot()) {
            long ext = this.extract(max, t);
            this.insert(ext - ies.insert(ext, t), t);
            t.commit();
        }
    }

    @Override
    public String toString() {
    	return getAmount() + "/" + getCapacity() + " <-" + maxExtract + " ->" + maxReceive;
    }
}
