package net.bandit.mobs_on_demand.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.bandit.mobs_on_demand.MobsOnDemandMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Server-side settings, stored in {@code config/mobs_on_demand.json}. */
public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(MobsOnDemandMod.MOD_ID + ".json");

    private static ModConfig instance = new ModConfig();

    /** Allow capturing baby animals and villagers. */
    public boolean allowBabies = true;
    /** Ranch Order Board orders also consume one empty Mob Crate, which the delivery arrives in. */
    public boolean ordersRequireCrate = true;
    /** Most Ranch Order signs one village can generate with. 0 turns village signs off. */
    public int villageSignsPerVillage = 1;
    /** Chance (percent) that a given village house spot becomes a sign, until the per-village cap is reached. */
    public double villageSignChancePercent = 10.0;
    /**
     * Village types that never get signs. Accepts "plains", "desert", "savanna", "snowy", "taiga",
     * structure ids like "minecraft:village_desert", or house pool ids like "minecraft:village/desert/houses".
     */
    public List<String> villageBlacklist = new ArrayList<>();
    /** With FTB Chunks, also allow crating mobs in claims owned by allied teams. */
    public boolean ftbAllowAllies = true;

    public static ModConfig get() {
        return instance;
    }

    public static void load() {
        ModConfig loaded = new ModConfig();
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                ModConfig parsed = GSON.fromJson(reader, ModConfig.class);
                if (parsed != null) {
                    loaded = parsed;
                }
            } catch (IOException | JsonParseException e) {
                MobsOnDemandMod.LOGGER.error("Failed to read {}, using defaults", PATH, e);
            }
        }
        loaded.clamp();
        instance = loaded;
        save(loaded); // writes defaults on first run and adds fields introduced by updates
    }

    private void clamp() {
        villageSignsPerVillage = Mth.clamp(villageSignsPerVillage, 0, 16);
        villageSignChancePercent = Mth.clamp(villageSignChancePercent, 0.0, 50.0);
        if (villageBlacklist == null) {
            villageBlacklist = new ArrayList<>();
        }
    }

    private static void save(ModConfig config) {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            MobsOnDemandMod.LOGGER.error("Failed to write {}", PATH, e);
        }
    }
}
