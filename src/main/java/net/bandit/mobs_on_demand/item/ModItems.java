package net.bandit.mobs_on_demand.item;

import net.bandit.mobs_on_demand.MobsOnDemandMod;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.function.Function;

public final class ModItems {
    public static final Item CHICKEN_ATTRACTOR = register("chicken_attractor", p -> new MobAttractorItem<>(Chicken.class, p));
    public static final Item COW_ATTRACTOR = register("cow_attractor", p -> new MobAttractorItem<>(Cow.class, p));
    public static final Item SHEEP_ATTRACTOR = register("sheep_attractor", p -> new MobAttractorItem<>(Sheep.class, p));
    public static final Item PIG_ATTRACTOR = register("pig_attractor", p -> new MobAttractorItem<>(Pig.class, p));
    public static final Item VILLAGER_ATTRACTOR = register("villager_attractor", VillagerAttractorItem::new);

    public static final CreativeModeTab MOD_TAB = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            MobsOnDemandMod.id("main"),
            FabricCreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.mobs_on_demand.main"))
                    .icon(() -> new ItemStack(CHICKEN_ATTRACTOR))
                    .displayItems((parameters, output) -> {
                        output.accept(CHICKEN_ATTRACTOR);
                        output.accept(COW_ATTRACTOR);
                        output.accept(SHEEP_ATTRACTOR);
                        output.accept(PIG_ATTRACTOR);
                        output.accept(VILLAGER_ATTRACTOR);
                    })
                    .build());

    private ModItems() {
    }

    private static Item register(String name, Function<Item.Properties, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MobsOnDemandMod.id(name));
        Item.Properties properties = new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.EPIC);
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties));
    }

    /** Forces class loading so the static registrations above run during mod init. */
    public static void init() {
    }
}
