package com.mrh0.createaddition.item;

import com.mrh0.createaddition.config.CACommonConfig;
import com.mrh0.createaddition.util.Util;

import java.util.function.Consumer;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import com.mrh0.createaddition.index.CADataComponents;

// NeoForge: TR Energy's SimpleEnergyItem is replaced by an ItemAccessEnergyHandler capability (CATransfer) that
// stores the charge in createaddition:energy.
public class CapacitorItem extends Item {

    public CapacitorItem(Properties props) {
        super(props.stacksTo(16));
    }

    public static long getStoredEnergy(ItemStack stack) {
        return stack.getOrDefault(CADataComponents.ENERGY, 0);
    }

    public long getEnergyCapacity(ItemStack stack) {
        return CACommonConfig.COMMON.CAPACITOR_CAPACITY.get();
    }

    public long getEnergyMaxInput(ItemStack stack) {
        return CACommonConfig.COMMON.CAPACITOR_CHARGE_RATE.get();
    }

    public long getEnergyMaxOutput(ItemStack stack) {
        return CACommonConfig.COMMON.CAPACITOR_CHARGE_RATE.get();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Util.getTextComponent(getStoredEnergy(stack), getEnergyCapacity(stack), "⚡"));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getStoredEnergy(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round((float) getStoredEnergy(stack) / getEnergyCapacity(stack) * 13f);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x00BFFF;
    }
}
