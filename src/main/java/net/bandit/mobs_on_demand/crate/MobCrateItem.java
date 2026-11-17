package net.bandit.mobs_on_demand.crate;

import net.bandit.mobs_on_demand.compat.ClaimProtection;
import net.bandit.mobs_on_demand.config.ModConfig;
import net.bandit.mobs_on_demand.registry.ModComponents;
import net.bandit.mobs_on_demand.registry.ModTags;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/**
 * Packs a passive mob into the item and releases it later, keeping all of its data
 * (name, variant, age, villager profession and trades, ...).
 * <p>
 * Empty crate: right-click a mob in {@link ModTags#CAPTURABLE} to capture it, or a block to place the crate.
 * Filled crate: right-click a block to release the mob, or sneak-right-click to place the crate with the mob inside.
 */
public class MobCrateItem extends BlockItem {

    public MobCrateItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static boolean isFilled(ItemStack stack) {
        return stack.has(DataComponents.ENTITY_DATA);
    }

    /**
     * Capture runs in {@link UseEntityCallback} rather than {@code Item#interactLivingEntity} because the latter only
     * fires after the mob's own interaction, so villagers would open trading instead of being crated.
     */
    public static void registerEvents() {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof MobCrateItem) || isFilled(stack) || player.isSpectator()
                    || !(entity instanceof Mob mob) || !mob.typeHolder().is(ModTags.CAPTURABLE)) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS; // the server does the real checks
            }
            return capture(serverPlayer, hand, stack, mob);
        });
    }

    private static InteractionResult capture(ServerPlayer player, InteractionHand hand, ItemStack stack, Mob mob) {
        Component refusal = checkCapture(player, mob);
        if (refusal != null) {
            player.sendOverlayMessage(refusal.copy().withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        prepareForCrating(mob);
        Component label = mob.getName();
        ItemStack filled = MobCrates.createFilled(mob);

        stack.consume(1, player);
        if (stack.isEmpty()) {
            player.setItemInHand(hand, filled);
        } else {
            player.getInventory().placeItemBackInInventory(filled, Prediction.SERVER_ONLY); // drops it if full
        }

        mob.level().playSound(null, mob.blockPosition(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 1.0F, 0.8F);
        mob.discard();
        player.sendOverlayMessage(Component.translatable("message.mobs_on_demand.captured", label));
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Returns why {@code mob} can't be crated by {@code player}, or {@code null} if it can. */
    private static Component checkCapture(ServerPlayer player, Mob mob) {
        if (!mob.isAlive()) {
            return Component.translatable("message.mobs_on_demand.not_capturable");
        }
        if (mob instanceof OwnableEntity ownable && ownable.getOwnerReference() != null
                && !ownable.getOwnerReference().getUUID().equals(player.getUUID())) {
            return Component.translatable("message.mobs_on_demand.not_owner");
        }
        if (mob.isBaby() && !ModConfig.get().allowBabies) {
            return Component.translatable("message.mobs_on_demand.no_babies");
        }
        if (mob.isSleeping() || (mob instanceof AbstractVillager villager && villager.isTrading())) {
            return Component.translatable("message.mobs_on_demand.busy");
        }
        if (!ClaimProtection.canInteract(player, mob)) {
            return Component.translatable("message.mobs_on_demand.claimed");
        }
        return null;
    }

    /** Detaches the mob from the world so the saved data doesn't drag stale links along. */
    private static void prepareForCrating(Mob mob) {
        if (mob instanceof Leashable leashable && leashable.isLeashed()) {
            leashable.dropLeash();
        }
        mob.ejectPassengers();
        mob.stopRiding();
        if (mob instanceof Villager villager) {
            // Free the bed, workstation and bell so other villagers can claim them, and forget the old locations.
            for (var memory : Villager.POI_MEMORIES.keySet()) {
                villager.releasePoi(memory);
                villager.getBrain().eraseMemory(memory);
            }
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        TypedEntityData<EntityType<?>> contents = stack.get(DataComponents.ENTITY_DATA);
        if (contents == null || context.getPlayer() == null || context.getPlayer().isSecondaryUseActive()) {
            return super.useOn(context); // place the crate as a block
        }
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }

        BlockPos clicked = context.getClickedPos();
        BlockPos spawnPos = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty()
                ? clicked
                : clicked.relative(context.getClickedFace());

        Entity entity = contents.type().create(serverLevel, EntitySpawnReason.BUCKET);
        if (entity == null) {
            player.sendOverlayMessage(Component.translatable("message.mobs_on_demand.release_failed").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        contents.loadInto(entity); // keeps the new entity's fresh UUID, so copied crates can't collide
        entity.snapTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, player.getYRot() + 180.0F, 0.0F);
        entity.resetFallDistance();

        if (!ClaimProtection.canInteract(player, entity)) {
            player.sendOverlayMessage(Component.translatable("message.mobs_on_demand.claimed_release").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (!level.noCollision(entity)) {
            player.sendOverlayMessage(Component.translatable("message.mobs_on_demand.no_room").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        serverLevel.addFreshEntity(entity);

        stack.remove(DataComponents.ENTITY_DATA);
        stack.remove(ModComponents.CRATE_LABEL);
        stack.remove(DataComponents.MAX_STACK_SIZE);
        level.playSound(null, spawnPos, SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 1.0F, 1.0F);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public Component getName(ItemStack stack) {
        Component label = label(stack);
        return label == null ? super.getName(stack) : Component.translatable("item.mobs_on_demand.mob_crate.filled", label);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        TypedEntityData<EntityType<?>> contents = stack.get(DataComponents.ENTITY_DATA);
        if (contents == null) {
            tooltip.accept(Component.translatable("tooltip.mobs_on_demand.mob_crate.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.accept(Component.translatable("tooltip.mobs_on_demand.mob_crate.contains", contents.type().getDescription())
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.mobs_on_demand.mob_crate.release").withStyle(ChatFormatting.YELLOW));
    }

    private static Component label(ItemStack stack) {
        Component label = stack.get(ModComponents.CRATE_LABEL);
        if (label != null) {
            return label;
        }
        TypedEntityData<EntityType<?>> contents = stack.get(DataComponents.ENTITY_DATA);
        return contents == null ? null : contents.type().getDescription();
    }
}
