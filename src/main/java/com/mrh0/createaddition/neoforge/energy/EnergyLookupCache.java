package com.mrh0.createaddition.neoforge.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * NeoForge: stands in for Fabric's {@code BlockApiCache<EnergyStorage, Direction>}. Fabric's cache asks the provider
 * again on every {@code find}; this does the same with {@code Level#getCapability}, so a neighbour whose handler
 * changes without invalidating its capabilities is still seen correctly.
 */
public final class EnergyLookupCache {
    private final ServerLevel level;
    private final BlockPos pos;

    private EnergyLookupCache(ServerLevel level, BlockPos pos) {
        this.level = level;
        this.pos = pos.immutable();
    }

    public static EnergyLookupCache create(ServerLevel level, BlockPos pos) {
        return new EnergyLookupCache(level, pos);
    }

    public @Nullable EnergyStorage find(@Nullable Direction side) {
        if (!level.isLoaded(pos)) return null;
        return CAEnergy.find(level, pos, side);
    }

    public BlockPos getPos() {
        return pos;
    }
}
