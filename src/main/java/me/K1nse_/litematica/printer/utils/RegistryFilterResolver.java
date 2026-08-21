package me.K1nse_.litematica.printer.utils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class RegistryFilterResolver {
    private RegistryFilterResolver() {
    }

    public static List<Item> resolveItems(List<String> filters) {
        List<Item> items = new ArrayList<>();
        for (String filter : filters) {
            BuiltInRegistries.ITEM.stream()
                    .filter(item -> FilterUtils.matchName(filter, new ItemStack(item)))
                    .forEach(items::add);
        }
        return items;
    }

    public static Set<Fluid> resolveFluids(List<String> filters) {
        Set<Fluid> fluids = new LinkedHashSet<>();
        for (String filter : filters) {
            BuiltInRegistries.FLUID.stream()
                    .filter(fluid -> FilterUtils.matchName(filter, fluid.defaultFluidState().createLegacyBlock()))
                    .forEach(fluids::add);
        }
        return fluids;
    }
}
