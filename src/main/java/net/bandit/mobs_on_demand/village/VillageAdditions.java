package net.bandit.mobs_on_demand.village;

import com.mojang.datafixers.util.Pair;
import net.bandit.mobs_on_demand.MobsOnDemandMod;
import net.bandit.mobs_on_demand.config.ModConfig;
import net.bandit.mobs_on_demand.mixin.StructureTemplatePoolAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adds the Ranch Order sign to the vanilla village house pools, so it generates along village streets.
 * Weight scales with each pool's total so the chance per house slot is the same in every village type;
 * {@link net.bandit.mobs_on_demand.mixin.JigsawPlacerMixin} enforces the per-village cap.
 */
public final class VillageAdditions {
    private static final List<String> VILLAGE_TYPES = List.of("plains", "desert", "savanna", "snowy", "taiga");
    private static final Identifier SIGN_TEMPLATE = MobsOnDemandMod.id("village/order_board");

    private VillageAdditions() {
    }

    /** True for the sign's pool element (any instance, in any pool). */
    public static boolean isOrderSign(StructurePoolElement element) {
        return element instanceof SinglePoolElement single && SIGN_TEMPLATE.equals(single.getTemplateLocation());
    }

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(VillageAdditions::addToVillages);
    }

    private static void addToVillages(MinecraftServer server) {
        ModConfig config = ModConfig.get();
        if (config.villageSignsPerVillage <= 0 || config.villageSignChancePercent <= 0) {
            MobsOnDemandMod.LOGGER.info("Ranch Order signs disabled in villages by config");
            return;
        }
        Registry<StructureTemplatePool> pools = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        Holder<StructureProcessorList> noProcessors = server.registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST)
                .getOrThrow(ResourceKey.create(Registries.PROCESSOR_LIST, Identifier.withDefaultNamespace("empty")));

        double share = config.villageSignChancePercent / 100.0;
        for (String type : VILLAGE_TYPES) {
            Identifier poolId = Identifier.withDefaultNamespace("village/" + type + "/houses");
            if (isBlacklisted(config, type, poolId)) {
                MobsOnDemandMod.LOGGER.info("Ranch Order signs blacklisted in {} villages", type);
                continue;
            }
            pools.getOptional(poolId).ifPresentOrElse(
                    pool -> add(pool, noProcessors, share),
                    () -> MobsOnDemandMod.LOGGER.warn("Village pool {} not found; order sign won't generate there", poolId));
        }
    }

    /** Matches "desert", "minecraft:village_desert" (structure id) or "minecraft:village/desert/houses" (pool id). */
    private static boolean isBlacklisted(ModConfig config, String type, Identifier poolId) {
        for (String entry : config.villageBlacklist) {
            String value = entry.trim().toLowerCase(Locale.ROOT);
            if (value.equals(type) || value.equals("minecraft:village_" + type) || value.equals("village_" + type)
                    || value.equals(poolId.toString()) || value.equals(poolId.getPath())) {
                return true;
            }
        }
        return false;
    }

    private static void add(StructureTemplatePool pool, Holder<StructureProcessorList> processors, double share) {
        StructureTemplatePoolAccessor accessor = (StructureTemplatePoolAccessor) pool;
        List<Pair<StructurePoolElement, Integer>> raw = new ArrayList<>(accessor.mobsOnDemand$getRawTemplates());
        int total = raw.stream().mapToInt(Pair::getSecond).sum();
        int weight = Math.max(1, (int) Math.round(total * share / (1 - share)));

        StructurePoolElement element = StructurePoolElement.single(SIGN_TEMPLATE.toString(), processors)
                .apply(StructureTemplatePool.Projection.RIGID);
        raw.add(Pair.of(element, weight));
        accessor.mobsOnDemand$setRawTemplates(raw);
        for (int i = 0; i < weight; i++) {
            accessor.mobsOnDemand$getTemplates().add(element);
        }
    }
}
