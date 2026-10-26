package me.matl114.registry;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

public class ItemRegistry {
    public static void init() {}

    public static Item TESTITEM;

    static {
        try {
            TESTITEM = Items.register("myitem", Item::new);
        } catch (Throwable e) {
            // 注册表冻结后无法注册测试物品, 生产环境下跳过 (仅开发环境可用)
            TESTITEM = null;
        }
    }
}
