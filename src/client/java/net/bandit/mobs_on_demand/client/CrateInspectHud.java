package net.bandit.mobs_on_demand.client;

import net.bandit.mobs_on_demand.MobsOnDemandMod;
import net.bandit.mobs_on_demand.crate.MobCrateBlockEntity;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** While sneaking and looking at a placed, filled Mob Crate, shows what's inside just below the crosshair. */
public final class CrateInspectHud {
    private CrateInspectHud() {
    }

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, MobsOnDemandMod.id("crate_inspect"), CrateInspectHud::extract);
    }

    private static void extract(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !minecraft.player.isShiftKeyDown()
                || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK
                || !(minecraft.level.getBlockEntity(hit.getBlockPos()) instanceof MobCrateBlockEntity crate)) {
            return;
        }
        EntityType<?> type = crate.contentType();
        Component label = crate.contentLabel();
        if (type == null || label == null) {
            return;
        }

        Component title = Component.translatable("hud.mobs_on_demand.crate_contains", label);
        Component subtitle = label.getString().equals(type.getDescription().getString()) ? null : type.getDescription();

        int centerX = g.guiWidth() / 2;
        int top = g.guiHeight() / 2 + 12;
        int width = Math.max(minecraft.font.width(title), subtitle == null ? 0 : minecraft.font.width(subtitle)) + 8;
        int height = subtitle == null ? 13 : 23;
        g.fill(centerX - width / 2, top - 2, centerX + width / 2, top - 2 + height, 0xA0100810);
        g.centeredText(minecraft.font, title, centerX, top, 0xFFFFFFFF);
        if (subtitle != null) {
            g.centeredText(minecraft.font, subtitle, centerX, top + 10, 0xFFB8B8B8);
        }
    }
}
