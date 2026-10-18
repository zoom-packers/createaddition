package com.mrh0.createaddition.neoforge.energy;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * NeoForge: replaces Team Reborn Energy's {@code EnergyStorage}. Crafts &amp; Additions keeps counting energy in longs
 * with TR Energy's method names, and every storage is also a NeoForge {@link EnergyHandler}, so it can be exposed
 * through {@code Capabilities.Energy} as is. The int overloads saturate.
 */
public interface EnergyStorage extends EnergyHandler {
    EnergyStorage EMPTY = new EnergyStorage() {
        @Override
        public long insert(long maxAmount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public long extract(long maxAmount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public long getAmount() {
            return 0;
        }

        @Override
        public long getCapacity() {
            return 0;
        }

        @Override
        public boolean supportsInsertion() {
            return false;
        }

        @Override
        public boolean supportsExtraction() {
            return false;
        }
    };

    long insert(long maxAmount, TransactionContext transaction);

    long extract(long maxAmount, TransactionContext transaction);

    long getAmount();

    long getCapacity();

    default boolean supportsInsertion() {
        return true;
    }

    default boolean supportsExtraction() {
        return true;
    }

    @Override
    default long getAmountAsLong() {
        return getAmount();
    }

    @Override
    default long getCapacityAsLong() {
        return getCapacity();
    }

    @Override
    default int insert(int amount, TransactionContext transaction) {
        return (int) Math.min(Integer.MAX_VALUE, insert((long) amount, transaction));
    }

    @Override
    default int extract(int amount, TransactionContext transaction) {
        return (int) Math.min(Integer.MAX_VALUE, extract((long) amount, transaction));
    }
}
