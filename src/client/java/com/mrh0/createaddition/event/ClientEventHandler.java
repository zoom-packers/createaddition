package com.mrh0.createaddition.event;

import com.mrh0.createaddition.CreateAddition;
import com.mrh0.createaddition.item.WireSpool;
import com.mrh0.createaddition.network.ObservePacketPayload;
import com.mrh0.createaddition.sound.CASoundScapes;
import com.mrh0.createaddition.util.Util;

import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;

public class ClientEventHandler {

	public static boolean clientRenderHeldWire = false;

	public static void register() {
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> onClientTickEnd(Minecraft.getInstance()));
	}

	// NeoForge: client reload listeners are added through AddClientReloadListenersEvent (mod bus)
	public static void addReloadListeners(AddClientReloadListenersEvent event) {
		event.addListener(CreateAddition.asResource("client_resource_reload"),
				(ResourceManagerReloadListener) resourceManager -> CASoundScapes.invalidateAll());
	}

	private static void onClientTickEnd(Minecraft client) {
		ObservePacketPayload.tick();
		CASoundScapes.tick();
		updateHeldWire(client.player);
	}

	private static void updateHeldWire(LocalPlayer player) {
		if (player == null) {
			clientRenderHeldWire = false;
			return;
		}
		ItemStack stack = player.getInventory().getSelectedItem();
		if (stack.isEmpty() || WireSpool.isRemover(stack.getItem())) {
			clientRenderHeldWire = false;
			return;
		}
		clientRenderHeldWire = Util.getWireNodeOfSpools(stack) != null;
	}
}
