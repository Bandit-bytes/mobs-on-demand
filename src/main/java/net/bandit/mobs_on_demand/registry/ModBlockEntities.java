package net.bandit.mobs_on_demand.registry;

import net.bandit.mobs_on_demand.MobsOnDemandMod;
import net.bandit.mobs_on_demand.board.OrderBoardBlockEntity;
import net.bandit.mobs_on_demand.crate.MobCrateBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;

public final class ModBlockEntities {
    public static final BlockEntityType<OrderBoardBlockEntity> ORDER_BOARD = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            MobsOnDemandMod.id("order_board"),
            new BlockEntityType<>(OrderBoardBlockEntity::new, Set.of(ModBlocks.ORDER_BOARD)));

    public static final BlockEntityType<MobCrateBlockEntity> MOB_CRATE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            MobsOnDemandMod.id("mob_crate"),
            new BlockEntityType<>(MobCrateBlockEntity::new, Set.of(ModBlocks.MOB_CRATE)));

    private ModBlockEntities() {
    }

    public static void init() {
    }
}
