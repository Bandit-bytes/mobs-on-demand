package net.bandit.mobs_on_demand.item;

import net.bandit.mobs_on_demand.compat.ClaimProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.function.Consumer;

/** Base for items that call every mob of type {@code T} within range to the player. */
public abstract class AttractorItem<T extends Mob> extends Item {
    private static final double RADIUS = 128.0;
    private static final int COOLDOWN_TICKS = 100;

    private final Class<T> targetType;

    protected AttractorItem(Class<T> targetType, Properties properties) {
        super(properties);
        this.targetType = targetType;
    }

    /** Make {@code mob} head towards {@code player}. */
    protected abstract void attract(T mob, Player player);

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        level.playSound(null, player.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 1.0F);

        AABB searchBox = player.getBoundingBox().inflate(RADIUS);
        for (T mob : level.getEntitiesOfClass(targetType, searchBox, mob -> ClaimProtection.canAttract(serverPlayer, mob))) {
            attract(mob, player);
        }

        player.getCooldowns().addCooldown(player.getItemInHand(hand), COOLDOWN_TICKS);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(getDescriptionId() + ".tooltip1").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable(getDescriptionId() + ".tooltip2").withStyle(ChatFormatting.YELLOW));
    }
}
