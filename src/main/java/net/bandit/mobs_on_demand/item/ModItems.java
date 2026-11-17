package net.bandit.mobs_on_demand.item;

import net.bandit.mobs_on_demand.MobsOnDemandMod;
import net.bandit.mobs_on_demand.crate.MobCrateItem;
import net.bandit.mobs_on_demand.registry.ModBlocks;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;

public final class ModItems {
    public static final Item MOB_CRATE = register("mob_crate", p -> new MobCrateItem(ModBlocks.MOB_CRATE, p), new Item.Properties().stacksTo(16));

    public static final CreativeModeTab MOD_TAB = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            MobsOnDemandMod.id("main"),
            FabricCreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.mobs_on_demand.main"))
                    .icon(() -> new ItemStack(MOB_CRATE))
                    .displayItems((parameters, output) -> {
                        output.accept(MOB_CRATE);
                        output.accept(ModBlocks.ORDER_BOARD);
                    })
                    .build());

    private ModItems() {
    }

    private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MobsOnDemandMod.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
    }

    /** Forces class loading so the static registrations above run during mod init. */
    public static void init() {
    }
}
