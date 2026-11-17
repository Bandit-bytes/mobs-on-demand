package net.bandit.mobs_on_demand.screenshots;

import net.bandit.mobs_on_demand.board.BoardPart;
import net.bandit.mobs_on_demand.board.OrderBoardBlock;
import net.bandit.mobs_on_demand.board.OrderBoardBlockEntity;
import net.bandit.mobs_on_demand.client.OrderBoardScreen;
import net.bandit.mobs_on_demand.config.ModConfig;
import net.bandit.mobs_on_demand.crate.MobCrateBlock;
import net.bandit.mobs_on_demand.crate.MobCrates;
import net.bandit.mobs_on_demand.delivery.ShopCatalog;
import net.bandit.mobs_on_demand.registry.ModBlocks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;

import java.lang.reflect.Field;
import java.nio.file.Path;

/**
 * Renders the README screenshots into docs/images: {@code ./gradlew runClientGameTest}.
 * Not a real test; it only builds scenes and photographs them.
 */
public class ReadmeScreenshots implements FabricClientGameTest {
    private static final Path OUT = Path.of(System.getProperty("mobs_on_demand.screenshotDir", "screenshots"));
    private static final int WIDTH = 1600;
    private static final int HEIGHT = 900;

    @Override
    public void runTest(ClientGameTestContext context) {
        // More likely village signs so the village shot finds one quickly (the per-village cap still applies).
        ModConfig.get().villageSignChancePercent = 40.0;

        context.getInput().resizeWindow(WIDTH, HEIGHT);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getServer().runCommand("time set 6000");
            world.getServer().runCommand("weather clear");
            world.getServer().runCommand("gamerule spawn_mobs false");
            world.getConnection().waitForChunksRender();

            int ground = world.getServer().computeOnServer(server ->
                    server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
            BlockPos origin = new BlockPos(0, ground, 0);

            ranchScene(context, world, origin);
            orderScreen(context, world, origin);
            crateInspect(context, world, origin.offset(40, 0, 0));
            villageScene(context, world, ground);
        }
    }

    private static void shot(ClientGameTestContext context, String name) {
        context.waitTicks(20);
        context.runOnClient(client -> {
            client.gui.toastManager().clear();
            client.gui.hud.getChat().clearMessages(false);
        });
        context.waitTicks(2);
        context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(OUT));
    }

    /** Sets the game mode without the chat message the command would print. */
    private static void gameMode(TestSingleplayerContext world, GameType type) {
        world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().forEach(player -> player.setGameMode(type)));
    }

    private static void hideHud(ClientGameTestContext context, boolean hide) {
        context.runOnClient(client -> {
            if (client.gui.hud.isHidden() != hide) {
                client.gui.hud.toggle();
            }
        });
    }

    private static void cmd(TestSingleplayerContext world, String command) {
        world.getServer().runCommand(command);
    }

    private static String xyz(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    /** Places a complete order sign whose front faces {@code facing}, master cell at {@code master}. */
    private static void placeSign(ServerLevel level, BlockPos master, Direction facing) {
        BlockState base = ModBlocks.ORDER_BOARD.defaultBlockState().setValue(OrderBoardBlock.FACING, facing);
        Direction right = facing.getCounterClockWise();
        for (BoardPart part : BoardPart.values()) {
            level.setBlock(master.above(part.row).relative(right, part.column),
                    base.setValue(OrderBoardBlock.PART, part), Block.UPDATE_CLIENTS);
        }
    }

    private static void placeCrate(ServerLevel level, BlockPos pos, Direction facing, ItemStack contents) {
        boolean filled = contents != null;
        level.setBlock(pos, ModBlocks.MOB_CRATE.defaultBlockState()
                .setValue(MobCrateBlock.FACING, facing).setValue(MobCrateBlock.FILLED, filled), Block.UPDATE_ALL);
        if (filled && level.getBlockEntity(pos) != null) {
            var crate = level.getBlockEntity(pos);
            crate.applyComponentsFromItemStack(contents);
            crate.setChanged();
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_ALL);
        }
    }

    private static ItemStack crateOf(ServerLevel level, EntityType<? extends net.minecraft.world.entity.Mob> type, String name) {
        var mob = type.create(level, EntitySpawnReason.COMMAND);
        if (name != null) {
            mob.setCustomName(Component.literal(name));
        }
        return MobCrates.createFilled(mob);
    }

    private static void ranchScene(ClientGameTestContext context, TestSingleplayerContext world, BlockPos o) {
        int x = o.getX(), y = o.getY(), z = o.getZ();
        cmd(world, "fill " + (x - 12) + " " + y + " " + (z - 10) + " " + (x + 12) + " " + (y + 8) + " " + (z + 14) + " air");
        cmd(world, "fill " + (x - 12) + " " + (y - 1) + " " + (z - 10) + " " + (x + 12) + " " + (y - 1) + " " + (z + 14) + " grass_block");
        cmd(world, "fill " + (x - 12) + " " + (y - 1) + " " + (z + 2) + " " + (x + 12) + " " + (y - 1) + " " + (z + 3) + " dirt_path");

        world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            placeSign(level, o, Direction.SOUTH);
            placeCrate(level, o.offset(-2, 0, 0), Direction.SOUTH, crateOf(level, EntityTypes.PIG, null));
            placeCrate(level, o.offset(-2, 1, 0), Direction.EAST, crateOf(level, EntityTypes.SHEEP, null));
            placeCrate(level, o.offset(-3, 0, 1), Direction.SOUTH, null);
            placeCrate(level, o.offset(3, 0, 0), Direction.SOUTH, crateOf(level, EntityTypes.CHICKEN, null));
            placeCrate(level, o.offset(4, 0, 1), Direction.EAST, null);
        });

        // Pen behind the sign
        cmd(world, "fill " + (x - 8) + " " + y + " " + (z - 8) + " " + (x + 9) + " " + y + " " + (z - 3) + " oak_fence");
        cmd(world, "fill " + (x - 7) + " " + y + " " + (z - 7) + " " + (x + 8) + " " + y + " " + (z - 4) + " air");
        cmd(world, "setblock " + (x - 8) + " " + (y + 1) + " " + (z - 3) + " lantern");
        cmd(world, "setblock " + (x + 9) + " " + (y + 1) + " " + (z - 3) + " lantern");
        cmd(world, "setblock " + (x + 6) + " " + y + " " + (z - 6) + " hay_block");
        cmd(world, "setblock " + (x + 7) + " " + y + " " + (z - 6) + " hay_block");
        cmd(world, "setblock " + (x + 6) + " " + (y + 1) + " " + (z - 6) + " hay_block");
        cmd(world, "setblock " + (x - 6) + " " + y + " " + (z + 5) + " poppy");
        cmd(world, "setblock " + (x + 7) + " " + y + " " + (z + 5) + " dandelion");
        cmd(world, "setblock " + (x - 5) + " " + y + " " + (z + 6) + " cornflower");

        String still = "NoAI:1b,Silent:1b,PersistenceRequired:1b";
        cmd(world, "summon cow " + (x - 4.5) + " " + y + " " + (z - 5.5) + " {" + still + ",Rotation:[20f,0f]}");
        cmd(world, "summon cow " + (x + 3.5) + " " + y + " " + (z - 6.0) + " {" + still + ",Rotation:[-30f,0f]}");
        cmd(world, "summon sheep " + (x - 1.5) + " " + y + " " + (z - 6.5) + " {" + still + ",Rotation:[10f,0f],Color:0}");
        cmd(world, "summon sheep " + (x + 6.5) + " " + y + " " + (z - 4.5) + " {" + still + ",Rotation:[-40f,0f],Color:1}");
        cmd(world, "summon chicken " + (x + 1.5) + " " + y + " " + (z - 4.5) + " {" + still + ",Rotation:[15f,0f]}");
        cmd(world, "summon villager " + (x + 5.5) + " " + y + " " + (z + 1.5) + " {" + still
                + ",Rotation:[-20f,0f],VillagerData:{profession:\"minecraft:librarian\",level:2,type:\"minecraft:plains\"}}");
        cmd(world, "summon horse " + (x - 5.0) + " " + y + " " + (z + 2.0) + " {" + still + ",Rotation:[40f,0f],Variant:3}");

        gameMode(world, GameType.SPECTATOR);
        cmd(world, "tp @a " + (x + 1.0) + " " + (y + 2.6) + " " + (z + 7.5) + " facing " + (x + 1) + " " + (y - 0.6) + " " + (z - 1));
        world.getConnection().waitForChunksRender();
        hideHud(context, true);
        shot(context, "ranch");
    }

    private static void orderScreen(ClientGameTestContext context, TestSingleplayerContext world, BlockPos sign) {
        gameMode(world, GameType.CREATIVE);
        cmd(world, "tp @a " + (sign.getX() + 1.5) + " " + sign.getY() + " " + (sign.getZ() + 3.5) + " facing " + (sign.getX() + 1) + " " + (sign.getY() + 1.5) + " " + sign.getZ());
        context.waitTicks(5);
        world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            if (level.getBlockEntity(sign) instanceof OrderBoardBlockEntity board) {
                for (String id : new String[]{"mobs_on_demand:cow", "mobs_on_demand:villager_librarian", "mobs_on_demand:horse"}) {
                    board.placeOrder(player, ShopCatalog.indexOf(id));
                }
                board.setItem(0, crateOf(level, EntityTypes.COW, "Bessie"));
                board.setItem(1, crateOf(level, EntityTypes.SHEEP, null));
            }
        });
        gameMode(world, GameType.SURVIVAL);
        cmd(world, "clear @a");
        cmd(world, "give @a emerald 64");
        cmd(world, "give @a wheat 32");
        cmd(world, "give @a book 8");
        cmd(world, "give @a mobs_on_demand:mob_crate 6");
        cmd(world, "give @a carrot 16");
        context.waitTicks(5);
        world.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            if (server.overworld().getBlockEntity(sign) instanceof OrderBoardBlockEntity board) {
                player.openMenu(board);
            }
        });
        context.waitForScreen(OrderBoardScreen.class);
        context.runOnClient(client -> {
            try {
                Field selected = OrderBoardScreen.class.getDeclaredField("selected");
                selected.setAccessible(true);
                selected.setInt(client.gui.screen(), 4); // cow
                Field scroll = OrderBoardScreen.class.getDeclaredField("scroll");
                scroll.setAccessible(true);
                scroll.setInt(client.gui.screen(), 2);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
        hideHud(context, true); // screens still render; this hides the hand behind it
        shot(context, "order_board");
        context.setScreen(() -> null);
    }

    private static void crateInspect(ClientGameTestContext context, TestSingleplayerContext world, BlockPos o) {
        int x = o.getX(), y = o.getY(), z = o.getZ();
        cmd(world, "fill " + (x - 4) + " " + y + " " + (z - 4) + " " + (x + 4) + " " + (y + 4) + " " + (z + 4) + " air");
        cmd(world, "fill " + (x - 4) + " " + (y - 1) + " " + (z - 4) + " " + (x + 4) + " " + (y - 1) + " " + (z + 4) + " grass_block");
        world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            placeCrate(level, o, Direction.SOUTH, crateOf(level, EntityTypes.COW, "Bessie"));
            placeCrate(level, o.offset(1, 0, -1), Direction.SOUTH, crateOf(level, EntityTypes.PIG, null));
            placeCrate(level, o.offset(-1, 0, -1), Direction.SOUTH, null);
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.getInventory().setSelectedSlot(0);
            player.getInventory().setItem(0, crateOf(level, EntityTypes.VILLAGER, null));
        });
        gameMode(world, GameType.SURVIVAL);
        cmd(world, "tp @a " + (x + 1.6) + " " + y + " " + (z + 2.4));
        world.getConnection().waitForChunksRender();
        hideHud(context, false);
        context.getInput().holdKey(options -> options.keyShift);
        context.waitTicks(10);
        context.getInput().lookAt(o); // from the (sneaking) eye position
        context.waitTicks(5);
        String target = context.computeOnClient(client -> String.valueOf(client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                ? client.level.getBlockState(hit.getBlockPos()) + " crate=" + (client.level.getBlockEntity(hit.getBlockPos()) instanceof net.bandit.mobs_on_demand.crate.MobCrateBlockEntity c ? c.contentLabel() : null)
                : client.hitResult));
        System.err.println("[readme screenshots] crate inspect target: " + target);
        shot(context, "crate_inspect");
        context.getInput().releaseKey(options -> options.keyShift);
    }

    /** Places plains villages until one generates with an order sign, then photographs the sign from the street. */
    private static void villageScene(ClientGameTestContext context, TestSingleplayerContext world, int ground) {
        BlockPos found = null;
        for (int attempt = 0; attempt < 12 && found == null; attempt++) {
            int vx = 400 + attempt * 400;
            cmd(world, "forceload add " + (vx - 96) + " -96 " + (vx + 96) + " 96");
            cmd(world, "place structure minecraft:village_plains " + vx + " " + ground + " 0");
            found = world.getServer().computeOnServer(server -> findSignMaster(server.overworld(), vx, ground));
            int bells = world.getServer().computeOnServer(server -> countBlocks(server.overworld(), vx, ground, net.minecraft.world.level.block.Blocks.BELL));
            System.err.println("[readme screenshots] village attempt " + attempt + " at x=" + vx + ": bells=" + bells + " sign=" + found);
            if (found == null) {
                cmd(world, "forceload remove " + (vx - 96) + " -96 " + (vx + 96) + " 96");
            }
        }
        if (found == null) {
            System.err.println("[readme screenshots] no village sign generated; skipping village shot");
            return;
        }
        BlockPos master = found;
        Direction facing = world.getServer().computeOnServer(server -> server.overworld().getBlockState(master).getValue(OrderBoardBlock.FACING));
        Direction right = facing.getCounterClockWise();
        double cx = master.getX() + 0.5 + right.getStepX() * 0.5;
        double cz = master.getZ() + 0.5 + right.getStepZ() * 0.5;
        // Back away from the sign along the street, only as far as the view stays unobstructed.
        int distance = world.getServer().computeOnServer(server -> clearDistance(server.overworld(), master, facing, 9));
        double camX = cx + facing.getStepX() * distance + right.getStepX() * 0.8;
        double camZ = cz + facing.getStepZ() * distance + right.getStepZ() * 0.8;
        double camY = master.getY() + 1.4; // spectator eye ends up ~1.6 above this
        gameMode(world, GameType.SPECTATOR);
        cmd(world, "tp @a " + camX + " " + camY + " " + camZ + " facing " + cx + " " + (master.getY() + 0.2) + " " + cz);
        world.getConnection().waitForChunksRender();
        hideHud(context, true);
        shot(context, "village");
    }

    /** Furthest distance (3..max) in front of the sign where every block between it and the camera is air. */
    private static int clearDistance(ServerLevel level, BlockPos master, Direction facing, int max) {
        int clear = 3;
        for (int d = 1; d <= max; d++) {
            BlockPos ahead = master.relative(facing, d);
            boolean open = true;
            for (int dy = 1; dy <= 3; dy++) {
                for (int side = 0; side <= 1; side++) {
                    if (!level.getBlockState(ahead.above(dy).relative(facing.getCounterClockWise(), side)).isAir()) {
                        open = false;
                    }
                }
            }
            if (!open) {
                break;
            }
            clear = Math.max(3, d);
        }
        return clear;
    }

    private static int countBlocks(ServerLevel level, int centerX, int ground, Block block) {
        int count = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -96; dx <= 96; dx++) {
            for (int dz = -96; dz <= 96; dz++) {
                for (int dy = -6; dy <= 8; dy++) {
                    pos.set(centerX + dx, ground + dy, dz);
                    if (level.isLoaded(pos) && level.getBlockState(pos).is(block)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static BlockPos findSignMaster(ServerLevel level, int centerX, int ground) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -96; dx <= 96; dx++) {
            for (int dz = -96; dz <= 96; dz++) {
                for (int dy = -6; dy <= 8; dy++) {
                    pos.set(centerX + dx, ground + dy, dz);
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.is(ModBlocks.ORDER_BOARD) && state.getValue(OrderBoardBlock.PART).isMaster()) {
                        return pos.immutable();
                    }
                }
            }
        }
        return null;
    }
}
