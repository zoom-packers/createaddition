package com.mrh0.createaddition.event;

import com.mrh0.createaddition.blocks.liquid_blaze_burner.LiquidBlazeBurnerBlock;
import com.mrh0.createaddition.blocks.portable_energy_interface.PortableEnergyManager;
import com.mrh0.createaddition.energy.network.WireGraph;
import com.mrh0.createaddition.index.CABlocks;
import com.mrh0.createaddition.index.CAItems;
import com.zurrtum.create.AllBlocks;

import com.zurrtum.create.content.processing.burner.BlazeBurnerBlock;
import com.zurrtum.create.content.processing.burner.BlazeBurnerBlockEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;

public class GameEvents {

	public static void initCommon() {
		// NeoForge: Fabric API's lifecycle callbacks are NeoForge events
		NeoForge.EVENT_BUS.addListener(LevelTickEvent.Pre.class, event -> {
			if (event.getLevel() instanceof ServerLevel level) levelTickEvent(level);
		});
		NeoForge.EVENT_BUS.addListener(ServerTickEvent.Pre.class, event -> serverTickEvent(event.getServer()));
		NeoForge.EVENT_BUS.addListener(ChunkEvent.Load.class, event -> {
			if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk)
				chunkLoadEvent(level, chunk, event.isNewChunk());
		});
		NeoForge.EVENT_BUS.addListener(LevelEvent.Unload.class, event -> {
			if (event.getLevel() instanceof ServerLevel level) unloadEvent(level.getServer(), level);
		});
		NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class, event -> {
			InteractionResult result = interact(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
			if (result != InteractionResult.PASS) {
				event.setCanceled(true);
				event.setCancellationResult(result);
			}
		});
	}

	public static void levelTickEvent(ServerLevel level) {
		WireGraph.tick(level);
	}

	public static void serverTickEvent(MinecraftServer server) {
		// Using ServerTick instead of WorldTick because some contraptions can switch worlds.
		PortableEnergyManager.tick();
	}

	public static void chunkLoadEvent(ServerLevel level, LevelChunk chunk, boolean generated) {
		WireGraph.chunkLoaded(level, chunk);
	}

	public static void unloadEvent(MinecraftServer server, ServerLevel level) {
		WireGraph.levelUnloaded(level);
	}

	public static InteractionResult interact(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
		try {
			if(level.isClientSide()) return InteractionResult.PASS;
			BlockPos pos = hitResult.getBlockPos();
			ItemStack item = player.getItemInHand(hand);
			BlockState state = level.getBlockState(pos);
			if(item.getItem() == CAItems.STRAW && level.getBlockEntity(pos) instanceof BlazeBurnerBlockEntity) {
				if(state.is(AllBlocks.BLAZE_BURNER)) {
					BlockState newState = CABlocks.LIQUID_BLAZE_BURNER.defaultBlockState()
							.setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING)
							.setValue(LiquidBlazeBurnerBlock.FACING, state.getValue(BlazeBurnerBlock.FACING));
					level.setBlockAndUpdate(pos, newState);
					if(!player.isCreative())
						item.shrink(1);
					return InteractionResult.SUCCESS;
				}
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return InteractionResult.PASS;
	}
}
