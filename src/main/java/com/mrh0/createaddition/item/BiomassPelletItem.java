package com.mrh0.createaddition.item;

import net.minecraft.world.item.Item;

public class BiomassPelletItem extends Item {
	// NeoForge: the pellet and the pallet get cooking_fuel components in CreateAdditionNeoForge#addFuels
	public static final int BURN_TIME = 6400;

	public BiomassPelletItem(Properties props) {
		super(props);
	}
}
