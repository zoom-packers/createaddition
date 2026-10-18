package com.mrh0.createaddition.commands;

import java.net.URI;

import com.mojang.brigadier.CommandDispatcher;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

public class CCApiCommand {
	public static void register() {
		NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> register(event.getDispatcher()));
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispather) {
		dispather.register(Commands.literal("cca_api").requires(Commands.hasPermission(Commands.LEVEL_ALL))
			.executes(context -> {
				Player p =  context.getSource().getPlayerOrException();
				String link = "https://github.com/mrh0/createaddition/blob/main/COMPUTERCRAFT.md";
				MutableComponent text = Component.translatable("createaddition.command.cca_api.link");
				text.withStyle(style -> {
					return style.applyFormats(ChatFormatting.AQUA, ChatFormatting.UNDERLINE)
							.withHoverEvent(new HoverEvent.ShowText(Component.literal(link)))
							.withClickEvent(new ClickEvent.OpenUrl(URI.create(link)));
				});
				p.sendSystemMessage(text);
				return 1;
			}
		));
	}
}
