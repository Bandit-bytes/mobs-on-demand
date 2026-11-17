package net.bandit.mobs_on_demand.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.bandit.mobs_on_demand.MobsOnDemandMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Server-side settings, stored in {@code config/mobs_on_demand.json}. */
public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(MobsOnDemandMod.MOD_ID + ".json");

    private static ModConfig instance = new ModConfig();

    /** Allow capturing baby animals and villagers. */
    public boolean allowBabies = true;
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
        // Nothing numeric yet; kept as the single place to validate future settings.
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
