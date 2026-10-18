package com.mrh0.createaddition.neoforge.energy;

/**
 * NeoForge: Fabric's {@code StoragePreconditions.notNegative} for longs ({@code TransferPreconditions} only takes ints).
 */
public final class EnergyPreconditions {
    private EnergyPreconditions() {
    }

    public static void notNegative(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Expected a non-negative value, got " + value);
        }
    }
}
