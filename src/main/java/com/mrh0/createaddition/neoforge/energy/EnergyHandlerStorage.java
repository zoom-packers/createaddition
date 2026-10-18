package com.mrh0.createaddition.neoforge.energy;

import com.google.common.primitives.Ints;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * NeoForge: views another mod's {@link EnergyHandler} as an {@link EnergyStorage}. NeoForge handlers have no
 * supportsInsertion/supportsExtraction, so those simulate a 1 FE transfer in a transaction that is never committed.
 */
public record EnergyHandlerStorage(EnergyHandler handler) implements EnergyStorage {
    @Override
    public long insert(long maxAmount, TransactionContext transaction) {
        EnergyPreconditions.notNegative(maxAmount);
        return handler.insert(Ints.saturatedCast(maxAmount), transaction);
    }

    @Override
    public long extract(long maxAmount, TransactionContext transaction) {
        EnergyPreconditions.notNegative(maxAmount);
        return handler.extract(Ints.saturatedCast(maxAmount), transaction);
    }

    @Override
    public long getAmount() {
        return handler.getAmountAsLong();
    }

    @Override
    public long getCapacity() {
        return handler.getCapacityAsLong();
    }

    @Override
    public boolean supportsInsertion() {
        try (Transaction t = Transaction.open(Transaction.getCurrentOpenedTransaction())) {
            return handler.insert(1, t) > 0;
        }
    }

    @Override
    public boolean supportsExtraction() {
        try (Transaction t = Transaction.open(Transaction.getCurrentOpenedTransaction())) {
            return handler.extract(1, t) > 0;
        }
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        return handler.insert(amount, transaction);
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        return handler.extract(amount, transaction);
    }
}
