package com.mrh0.createaddition.blocks.connector.base;

import com.mrh0.createaddition.neoforge.energy.CAEnergy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.mrh0.createaddition.neoforge.energy.EnergyStorage;

public enum ConnectorMode implements StringRepresentable {
	Push("push"),
	Pull("pull"),
	None("none"),
	Passive("passive");

	private String name;

	ConnectorMode(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	public ConnectorMode getNext() {
        return switch (this) {
            case None -> Pull;
            case Pull -> Push;
            case Push -> None;
            default -> None;
        };
    }

	public MutableComponent getTooltip() {
        return switch (this) {
            case Passive -> Component.translatable("createaddition.tooltip.energy.passive");
            case None -> Component.translatable("createaddition.tooltip.energy.none");
            case Pull -> Component.translatable("createaddition.tooltip.energy.pull");
            case Push -> Component.translatable("createaddition.tooltip.energy.push");
        };
    }

	public boolean isActive() {
		return this == Push || this == Pull;
	}

	public static ConnectorMode test(Level level, BlockPos pos, Direction face) {
		BlockEntity be = level.getBlockEntity(pos);
		if(be == null) return None;
		EnergyStorage energy = CAEnergy.find(level, pos, face);
		if (energy == null) energy = CAEnergy.find(level, pos, null);
		if (energy == null) return None;

		if(energy.supportsExtraction()) return Pull;
		if(energy.supportsInsertion()) return Push;

		return None;
	}
}
