package net.bandit.mobs_on_demand.client;

import net.bandit.mobs_on_demand.board.OrderBoardBlockEntity;
import net.bandit.mobs_on_demand.board.OrderBoardMenu;
import net.bandit.mobs_on_demand.crate.MobCrates;
import net.bandit.mobs_on_demand.delivery.DeliveryOffer;
import net.bandit.mobs_on_demand.delivery.ShopCatalog;
import net.bandit.mobs_on_demand.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Catalog on the left (scroll, click to select, Order button), queue and delivered crates on the right.
 * Drawn with plain fills in the vanilla container palette, so no GUI texture is needed yet.
 */
public class OrderBoardScreen extends AbstractContainerScreen<OrderBoardMenu> {
    private static final int WIDTH = 248;
    private static final int HEIGHT = 224;

    private static final int LIST_X = 8;
    private static final int LIST_Y = 18;
    private static final int LIST_W = 128;
    private static final int ROW_H = 20;
    private static final int VISIBLE_ROWS = 4;
    private static final int COST_Y = LIST_Y + ROW_H * VISIBLE_ROWS + 4;
    private static final int QUEUE_X = 146;
    private static final int QUEUE_Y = 18;
    private static final int QUEUE_W = 94;
    private static final int QUEUE_ROW_H = 13;

    private static final int TEXT = 0xFF404040;
    private static final int TEXT_DIM = 0xFF6F6F6F;
    private static final int PANEL = 0xFFC6C6C6;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int SHADOW = 0xFF373737;
    private static final int HIGHLIGHT = 0xFFFFFFFF;

    /** Shop index (as the server knows it) paired with the offer; offers the client couldn't decode are left out. */
    private record Listed(int index, DeliveryOffer offer) {
    }

    private List<Listed> offers = List.of();
    private int scroll;
    private int selected = -1;
    private Button orderButton;

    public OrderBoardScreen(OrderBoardMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        inventoryLabelX = OrderBoardMenu.INVENTORY_X;
        inventoryLabelY = OrderBoardMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        refreshOffers();
        orderButton = addRenderableWidget(Button.builder(Component.translatable("gui.mobs_on_demand.order"), b -> order())
                .bounds(leftPos + LIST_X + LIST_W - 40, topPos + COST_Y, 40, 18)
                .build());
        updateOrderButton();
    }

    private void refreshOffers() {
        List<Listed> listed = new ArrayList<>();
        List<ShopCatalog.Entry> entries = ClientShop.entries();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).offer() != null) {
                listed.add(new Listed(i, entries.get(i).offer()));
            }
        }
        offers = listed;
        if (selected >= offers.size()) {
            selected = -1;
        }
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private DeliveryOffer selectedOffer() {
        return selected >= 0 && selected < offers.size() ? offers.get(selected).offer() : null;
    }

    private boolean free() {
        return minecraft.player.hasInfiniteMaterials();
    }

    private void updateOrderButton() {
        DeliveryOffer offer = selectedOffer();
        orderButton.active = offer != null
                && menu.queueSize() < OrderBoardBlockEntity.MAX_QUEUE
                && (free() || offer.canAfford(minecraft.player.getInventory(), menu.requiresCrate()));
    }

    private void order() {
        if (selected >= 0 && selected < offers.size()) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, offers.get(selected).index());
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (offers.size() != ClientShop.entries().stream().filter(e -> e.offer() != null).count()) {
            refreshOffers(); // the server reloaded its shop while the screen was open
        }
        updateOrderButton();
    }

    // Rendering

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        raised(g, x, y, WIDTH, HEIGHT);

        extractCatalog(g, mouseX, mouseY);
        extractCost(g);
        extractQueue(g);

        for (var slot : menu.slots) {
            sunken(g, x + slot.x - 1, y + slot.y - 1, 18, 18);
        }
    }

    private void extractCatalog(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int listLeft = leftPos + LIST_X;
        int listTop = topPos + LIST_Y;
        sunken(g, listLeft - 1, listTop - 1, LIST_W + 2, ROW_H * VISIBLE_ROWS + 2);

        if (offers.isEmpty()) {
            g.text(font, Component.translatable("gui.mobs_on_demand.no_offers"), listLeft + 4, listTop + 6, HIGHLIGHT, false);
            return;
        }
        int hovered = rowAt(mouseX, mouseY);
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= offers.size()) {
                break;
            }
            DeliveryOffer offer = offers.get(index).offer();
            int rowTop = listTop + row * ROW_H;
            if (index == selected) {
                g.fill(listLeft, rowTop, listLeft + LIST_W, rowTop + ROW_H, 0xFFB4C6DC);
            } else if (index == hovered) {
                g.fill(listLeft, rowTop, listLeft + LIST_W, rowTop + ROW_H, 0xFFA4A4A4);
            }
            g.item(offer.iconStack(), listLeft + 2, rowTop + 2);
            String name = font.plainSubstrByWidth(offer.displayName().getString(), LIST_W - 24);
            g.text(font, name, listLeft + 21, rowTop + 2, TEXT, false);
            g.text(font, formatTime(offer.deliverySeconds()), listLeft + 21, rowTop + 11, TEXT_DIM, false);
        }

        int maxScroll = maxScroll();
        if (maxScroll > 0) {
            int trackHeight = ROW_H * VISIBLE_ROWS;
            int thumbHeight = Math.max(8, trackHeight * VISIBLE_ROWS / offers.size());
            int thumbTop = listTop + (trackHeight - thumbHeight) * scroll / maxScroll;
            g.fill(listLeft + LIST_W - 3, thumbTop, listLeft + LIST_W, thumbTop + thumbHeight, SHADOW);
        }
    }

    private void extractCost(GuiGraphicsExtractor g) {
        DeliveryOffer offer = selectedOffer();
        int left = leftPos + LIST_X;
        int top = topPos + COST_Y + 1;
        if (offer == null) {
            g.text(font, Component.translatable("gui.mobs_on_demand.select_offer"), left, top + 5, TEXT_DIM, false);
            return;
        }
        Inventory inventory = minecraft.player.getInventory();
        int x = left;
        for (DeliveryOffer.Cost cost : offer.cost()) {
            ItemStack stack = cost.displayStack();
            boolean enough = free() || countMatching(inventory, cost) >= cost.count();
            extractCostItem(g, stack, x, top, String.valueOf(cost.count()), enough);
            x += 18;
        }
        if (menu.requiresCrate() && !free()) {
            boolean hasCrate = inventory.getNonEquipmentItems().stream()
                    .anyMatch(MobCrates::isEmptyCrate);
            extractCostItem(g, new ItemStack(ModItems.MOB_CRATE), x, top, "1", hasCrate);
        }
    }

    private void extractCostItem(GuiGraphicsExtractor g, ItemStack stack, int x, int y, String count, boolean enough) {
        if (!enough) {
            g.fill(x, y, x + 16, y + 16, 0x80FF4040);
        }
        g.item(stack, x, y);
        g.itemDecorations(font, stack, x, y, count);
    }

    private void extractQueue(GuiGraphicsExtractor g) {
        int left = leftPos + QUEUE_X;
        int top = topPos + QUEUE_Y;
        int height = QUEUE_ROW_H * OrderBoardBlockEntity.MAX_QUEUE + 2;
        sunken(g, left - 1, top - 1, QUEUE_W + 2, height + 2);

        int size = Math.min(menu.queueSize(), OrderBoardBlockEntity.MAX_QUEUE);
        if (size <= 0) {
            g.text(font, Component.translatable("gui.mobs_on_demand.no_orders"), left + 3, top + 4, HIGHLIGHT, false);
            return;
        }
        for (int i = 0; i < size; i++) {
            int rowTop = top + 2 + i * QUEUE_ROW_H;
            DeliveryOffer offer = ClientShop.get(menu.queuedOfferId(i));
            String name = offer == null ? "?" : offer.displayName().getString();
            int seconds = menu.queuedSecondsLeft(i);
            Component status = seconds > 0
                    ? Component.literal(formatTime(seconds))
                    : Component.translatable(menu.outputFull() ? "gui.mobs_on_demand.waiting_for_space" : "gui.mobs_on_demand.arriving");
            int statusWidth = font.width(status.getString());
            g.text(font, font.plainSubstrByWidth(name, QUEUE_W - statusWidth - 8), left + 3, rowTop + 2, HIGHLIGHT, false);
            g.text(font, status, left + QUEUE_W - statusWidth - 2, rowTop + 2, seconds > 0 ? 0xFFE0E0E0 : 0xFFFFE070, false);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        g.text(font, Component.translatable("gui.mobs_on_demand.orders"), QUEUE_X, titleLabelY, TEXT, false);
        g.text(font, Component.translatable("gui.mobs_on_demand.deliveries"), OrderBoardMenu.OUTPUT_X - 1, OrderBoardMenu.OUTPUT_Y - 10, TEXT, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int row = rowAt(mouseX, mouseY);
        if (row >= 0) {
            DeliveryOffer offer = offers.get(row).offer();
            List<Component> lines = new ArrayList<>();
            lines.add(offer.displayName());
            lines.add(Component.translatable("gui.mobs_on_demand.delivery_time", formatTime(offer.deliverySeconds())).withStyle(ChatFormatting.GRAY));
            for (DeliveryOffer.Cost cost : offer.cost()) {
                lines.add(Component.literal(cost.count() + "x ").append(cost.displayStack().getHoverName()).withStyle(ChatFormatting.GRAY));
            }
            if (menu.requiresCrate()) {
                lines.add(Component.translatable("gui.mobs_on_demand.crate_needed").withStyle(ChatFormatting.DARK_GRAY));
            }
            g.setTooltipForNextFrame(lines.stream().map(Component::getVisualOrderText).toList(), mouseX, mouseY);
        }
    }

    // Input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int row = rowAt(event.x(), event.y());
        if (row >= 0) {
            selected = row;
            updateOrderButton();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isOverList(mouseX, mouseY)) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean isOverList(double mouseX, double mouseY) {
        double localX = mouseX - leftPos - LIST_X;
        double localY = mouseY - topPos - LIST_Y;
        return localX >= 0 && localX < LIST_W && localY >= 0 && localY < ROW_H * VISIBLE_ROWS;
    }

    private int rowAt(double mouseX, double mouseY) {
        if (!isOverList(mouseX, mouseY)) {
            return -1;
        }
        int index = scroll + (int) ((mouseY - topPos - LIST_Y) / ROW_H);
        return index < offers.size() ? index : -1;
    }

    private int maxScroll() {
        return Math.max(0, offers.size() - VISIBLE_ROWS);
    }

    // Helpers

    private static int countMatching(Inventory inventory, DeliveryOffer.Cost cost) {
        int total = 0;
        for (ItemStack stack : inventory.getNonEquipmentItems()) {
            if (!stack.isEmpty() && cost.ingredient().test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static String formatTime(int seconds) {
        return seconds >= 3600
                ? String.format("%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                : String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /** Vanilla-style raised panel. */
    private static void raised(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
        g.fill(x + 1, y + 1, x + w - 2, y + 3, HIGHLIGHT);
        g.fill(x + 1, y + 1, x + 3, y + h - 2, HIGHLIGHT);
        g.fill(x + 2, y + h - 3, x + w - 1, y + h - 1, 0xFF555555);
        g.fill(x + w - 3, y + 2, x + w - 1, y + h - 1, 0xFF555555);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, PANEL);
    }

    /** Vanilla-style inset area (slots, lists). */
    private static void sunken(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SLOT);
        g.fill(x, y, x + w - 1, y + 1, SHADOW);
        g.fill(x, y, x + 1, y + h - 1, SHADOW);
        g.fill(x + 1, y + h - 1, x + w, y + h, HIGHLIGHT);
        g.fill(x + w - 1, y + 1, x + w, y + h, HIGHLIGHT);
    }
}
