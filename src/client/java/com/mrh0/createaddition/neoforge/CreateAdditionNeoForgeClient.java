package com.mrh0.createaddition.neoforge;

import com.mrh0.createaddition.CreateAddition;
import com.mrh0.createaddition.CreateAdditionClient;
import com.mrh0.createaddition.event.ClientEventHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * NeoForge client entrypoint, replacing the Fabric {@code client} entrypoint. Upstream's client init refers to
 * Crafts &amp; Additions' items and block entity types, which only exist after the first {@link RegisterEvent}, so it
 * runs there, right after the content (as on Fabric, where the main entrypoint ran first).
 */
@Mod(value = CreateAddition.MODID, dist = Dist.CLIENT)
public class CreateAdditionNeoForgeClient {
    private static boolean initialized;

    public CreateAdditionNeoForgeClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(EventPriority.LOWEST, RegisterEvent.class, event -> {
            if (initialized) return;
            initialized = true;
            CreateAddition.registerContent();
            CreateAdditionClient.onInitializeClient();
        });
        modBus.addListener(CreateAdditionClient::registerPayloadHandlers);
        modBus.addListener(ClientEventHandler::addReloadListeners);
    }
}
