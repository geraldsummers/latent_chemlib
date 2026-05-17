package com.gerald.latentchemlib.item;

import com.gerald.latentchemlib.sim.ChemicalState;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class ChemicalCellItem extends Item {
    private static final String STATE_KEY = "chemical_state";

    public ChemicalCellItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static boolean hasState(ItemStack stack) {
        return stack.hasTag() && stack.getOrCreateTag().contains(STATE_KEY);
    }

    public static ChemicalState state(ItemStack stack) {
        return hasState(stack) ? ChemicalState.load(stack.getOrCreateTag().getCompound(STATE_KEY)) : ChemicalState.empty();
    }

    public static ItemStack withState(ItemStack stack, ChemicalState state) {
        ItemStack copy = stack.copy();
        if (state.mass() <= 0.0) {
            copy.removeTagKey(STATE_KEY);
        } else {
            copy.getOrCreateTag().put(STATE_KEY, state.save());
        }
        return copy;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return hasState(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        ChemicalState state = state(stack);
        if (state.mass() <= 0.0) {
            tooltip.add(Component.translatable("tooltip.latent_chemlib.sealed_chemical_cell.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.literal(state.chemicalId()).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.latent_chemlib.sealed_chemical_cell.mass", format(state.mass())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.latent_chemlib.sealed_chemical_cell.temperature", format(state.temperature())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.latent_chemlib.sealed_chemical_cell.density", format(state.density())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.latent_chemlib.sealed_chemical_cell.charge", format(state.charge())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.latent_chemlib.sealed_chemical_cell.energy", format(state.energy())).withStyle(ChatFormatting.GRAY));
    }

    private static String format(double value) {
        return String.format("%.2f", value);
    }
}
