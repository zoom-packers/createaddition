package com.mrh0.createaddition;

import com.mrh0.createaddition.config.CACommonConfig;
import com.mrh0.createaddition.event.GameEvents;
import com.mrh0.createaddition.commands.CCApiCommand;
import com.mrh0.createaddition.commands.WireGraphCommand;
import com.mrh0.createaddition.index.CABlockEntities;
import com.mrh0.createaddition.index.CACreativeTabs;
import com.mrh0.createaddition.index.CADataComponents;
import com.mrh0.createaddition.index.CADamageTypes;
import com.mrh0.createaddition.index.CAEffects;
import com.mrh0.createaddition.index.CAFluids;
import com.mrh0.createaddition.index.CAItems;
import com.mrh0.createaddition.index.CARecipes;
import com.mrh0.createaddition.index.CARegistration;
import com.mrh0.createaddition.index.CASounds;
import com.mrh0.createaddition.index.CATransfer;
import com.mrh0.createaddition.trains.schedule.CASchedule;
import com.zurrtum.create.neoforge.Platform;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// NeoForge: no longer the Fabric entrypoint. neoforge.CreateAdditionNeoForge calls these in NeoForge's order.
public class CreateAddition {
    public static final Logger LOGGER = LogManager.getLogger();

    public static final String MODID = "createaddition";

    public static boolean CC_ACTIVE = false;

    // NeoForge: mod construction. Registries are frozen here, so content waits for registerContent().
    public static void onInitialize() {
        CACommonConfig.register();
        CC_ACTIVE = Platform.isModLoaded("computercraft");
        GameEvents.initCommon();
        CCApiCommand.register();
        WireGraphCommand.register();
    }

    private static boolean contentRegistered;

    // NeoForge: runs once, from the first RegisterEvent, while every registry is open.
    public static void registerContent() {
        if (contentRegistered) return;
        contentRegistered = true;
        CreateAdditionPlugin.verifyEarlyRegistrationComplete();
        CADataComponents.register();
        CAItems.register();
        CAFluids.registerItems();
        CACreativeTabs.register();
        CABlockEntities.register();
        CAEffects.register();
        CASounds.register();
        CADamageTypes.register();
        CARecipes.register();
        CATransfer.register();
        CARegistration.register();
        CASchedule.register();
        LOGGER.info("Create Crafts & Additions Initialized!");
    }

    public static Identifier asResource(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
