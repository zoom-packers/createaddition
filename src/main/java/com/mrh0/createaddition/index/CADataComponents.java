package com.mrh0.createaddition.index;

import com.mojang.serialization.Codec;
import com.mrh0.createaddition.CreateAddition;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

// NeoForge: TR Energy kept item energy in its own component; NeoForge's ItemAccessEnergyHandler needs one from us.
public class CADataComponents {
	public static final DataComponentType<Integer> ENERGY = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			CreateAddition.asResource("energy"),
			DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

	public static void register() {
	}
}
