package net.bandit.mobs_on_demand.registry;

import net.bandit.mobs_on_demand.MobsOnDemandMod;
import net.bandit.mobs_on_demand.board.OrderBoardMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
    public static final MenuType<OrderBoardMenu> ORDER_BOARD = Registry.register(
            BuiltInRegistries.MENU,
            MobsOnDemandMod.id("order_board"),
            new MenuType<>(OrderBoardMenu::new, FeatureFlags.VANILLA_SET));

    private ModMenus() {
    }

    public static void init() {
    }
}
