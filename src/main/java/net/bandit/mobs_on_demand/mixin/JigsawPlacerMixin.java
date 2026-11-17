package net.bandit.mobs_on_demand.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bandit.mobs_on_demand.config.ModConfig;
import net.bandit.mobs_on_demand.village.VillageAdditions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Caps how many Ranch Order signs one village can get: once the structure being generated already holds
 * {@link ModConfig#villageSignsPerVillage} signs, the sign is dropped from the candidates for further house slots.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement$Placer")
public abstract class JigsawPlacerMixin {
    @Shadow
    @Final
    private List<? super PoolElementStructurePiece> pieces;

    @WrapOperation(
            method = "tryPlacingChildren",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/pools/StructureTemplatePool;getShuffledTemplates(Lnet/minecraft/util/RandomSource;)Ljava/util/List;"))
    private List<StructurePoolElement> mobsOnDemand$capOrderSigns(StructureTemplatePool pool, RandomSource random,
                                                                  Operation<List<StructurePoolElement>> original) {
        List<StructurePoolElement> candidates = original.call(pool, random);
        if (candidates.stream().noneMatch(VillageAdditions::isOrderSign)) {
            return candidates;
        }
        int placed = 0;
        for (Object piece : pieces) {
            if (piece instanceof PoolElementStructurePiece poolPiece && VillageAdditions.isOrderSign(poolPiece.getElement())) {
                placed++;
            }
        }
        if (placed < ModConfig.get().villageSignsPerVillage) {
            return candidates;
        }
        return candidates.stream().filter(element -> !VillageAdditions.isOrderSign(element)).toList();
    }
}
