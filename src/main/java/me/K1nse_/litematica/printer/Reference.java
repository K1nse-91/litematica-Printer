package me.K1nse_.litematica.printer;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * 模组核心常量引用类
 * 集中管理模组的全局固定值，避免硬编码和拼写错误
 */
public class Reference {
    public static final Minecraft MINECRAFT = Minecraft.getInstance();
    public static final String MOD_ID = "litematica-printer";
    public static final String MOD_NAME = "Litematica Printer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    //#if MC >= 260300
    //$$ // 26.3 起 COMPOSTABLES 表被移除，回退为常用可堆肥物品清单
    //$$ public static final Item[] COMPOSTABLE_ITEMS = new Item[]{
    //$$         Items.WHEAT_SEEDS, Items.WHEAT, Items.CARROT, Items.POTATO,
    //$$         Items.BEETROOT, Items.BEETROOT_SEEDS, Items.MELON_SLICE, Items.PUMPKIN,
    //$$         Items.PUMPKIN_PIE, Items.SUGAR_CANE, Items.KELP, Items.DRIED_KELP,
    //$$         Items.APPLE, Items.BREAD, Items.BROWN_MUSHROOM, Items.RED_MUSHROOM,
    //$$         Items.CACTUS, Items.SWEET_BERRIES, Items.GLOW_BERRIES, Items.NETHER_WART,
    //$$         Items.BAMBOO, Items.MOSS_BLOCK, Items.SHORT_GRASS, Items.FERN
    //$$ };
    //#else
    public static final Item[] COMPOSTABLE_ITEMS = Arrays.stream(ComposterBlock.COMPOSTABLES.keySet().toArray(ItemLike[]::new)).map(ItemLike::asItem).toArray(Item[]::new);
    //#endif
    public static final Item[] HOE_ITEMS = {Items.DIAMOND_HOE, Items.IRON_HOE, Items.GOLDEN_HOE, Items.NETHERITE_HOE, Items.STONE_HOE, Items.WOODEN_HOE};
    public static final Item[] SHOVEL_ITEMS = {Items.DIAMOND_SHOVEL, Items.IRON_SHOVEL, Items.GOLDEN_SHOVEL, Items.NETHERITE_SHOVEL, Items.STONE_SHOVEL, Items.WOODEN_SHOVEL};
    public static final Item[] AXE_ITEMS = {Items.DIAMOND_AXE, Items.IRON_AXE, Items.GOLDEN_AXE, Items.NETHERITE_AXE, Items.STONE_AXE, Items.WOODEN_AXE};
}
