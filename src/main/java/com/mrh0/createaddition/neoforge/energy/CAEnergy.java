package com.mrh0.createaddition.neoforge.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

/**
 * NeoForge: energy lookups and moves, replacing TR Energy's {@code EnergyStorage.SIDED}/{@code ITEM} lookups and
 * {@code EnergyStorageUtil}. Everything goes through NeoForge's {@code Capabilities.Energy}, so Crafts &amp; Additions
 * talks to any mod's energy handler (Pipez, AE2, ...) and they talk to it.
 */
public final class CAEnergy {
    private CAEnergy() {
    }

    public static @Nullable EnergyStorage wrap(@Nullable EnergyHandler handler) {
        if (handler == null) return null;
        if (handler instanceof EnergyStorage storage) return storage;
        return new EnergyHandlerStorage(handler);
    }

    /** {@code EnergyStorage.SIDED.find(level, pos, side)}. */
    public static @Nullable EnergyStorage find(Level level, BlockPos pos, @Nullable Direction side) {
        return wrap(level.getCapability(Capabilities.Energy.BLOCK, pos, side));
    }

    /** {@code EnergyStorage.ITEM.find(stack, context)}. */
    public static @Nullable EnergyStorage find(ItemStack stack, ItemAccess access) {
        if (stack.isEmpty()) return null;
        return wrap(stack.getCapability(Capabilities.Energy.ITEM, access));
    }

    /** TR Energy's {@code EnergyStorageUtil.move}: simulates the extraction, then moves what the target accepts. */
    public static long move(@Nullable EnergyStorage from, @Nullable EnergyStorage to, long maxAmount, @Nullable TransactionContext transaction) {
        if (from == null || to == null) return 0;
        EnergyPreconditions.notNegative(maxAmount);
        long maxExtracted;
        try (Transaction test = Transaction.open(transaction)) {
            maxExtracted = from.extract(maxAmount, test);
        }
        try (Transaction move = Transaction.open(transaction)) {
            long accepted = to.insert(maxExtracted, move);
            if (from.extract(accepted, move) == accepted) {
                move.commit();
                return accepted;
            }
        }
        return 0;
    }
}
