package net.bandit.mobs_on_demand.delivery;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.bandit.mobs_on_demand.MobsOnDemandMod;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The Ranch Order Board shop, loaded from data packs: {@code data/<namespace>/mobs_on_demand/shop/<name>.json}.
 * Offer id is {@code <namespace>:<name>}. A data pack overrides a default offer by providing the same path
 * (set {@code "enabled": false} to remove it). Reloads with {@code /reload}; the list is synced to clients so
 * indices match on both sides.
 */
public final class ShopCatalog {
    private static final FileToIdConverter FILES = FileToIdConverter.json(MobsOnDemandMod.MOD_ID + "/shop");

    public record Entry(String id, DeliveryOffer offer) {
    }

    private record Loaded(List<Entry> entries, int disabled) {
    }

    private static List<Entry> entries = List.of();

    private ShopCatalog() {
    }

    public static void init() {
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(MobsOnDemandMod.id("shop"), new SimpleReloadListener<Loaded>() {
            @Override
            protected Loaded prepare(PreparableReloadListener.SharedState state) {
                return read(FILES.listMatchingResources(state.resourceManager()), state.get(ResourceLoader.REGISTRY_LOOKUP_KEY));
            }

            @Override
            protected void apply(Loaded loaded, PreparableReloadListener.SharedState state) {
                entries = loaded.entries();
                MobsOnDemandMod.LOGGER.info("Loaded {} shop offers ({} disabled)", entries.size(), loaded.disabled());
            }
        });
    }

    public static List<Entry> entries() {
        return entries;
    }

    public static Entry byIndex(int index) {
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    public static DeliveryOffer get(String id) {
        for (Entry entry : entries) {
            if (entry.id().equals(id)) {
                return entry.offer();
            }
        }
        return null;
    }

    public static int indexOf(String id) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /** Parses every shop file; a broken file is logged with its path and skipped so one typo can't empty the shop. */
    private static Loaded read(Map<Identifier, Resource> files, HolderLookup.Provider registries) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        List<Entry> loaded = new ArrayList<>();
        List<DeliveryOffer> offers = new ArrayList<>();
        int disabled = 0;
        for (Map.Entry<Identifier, Resource> file : files.entrySet()) {
            Identifier id = FILES.fileToId(file.getKey());
            try (Reader reader = file.getValue().openAsReader()) {
                DataResult<DeliveryOffer> result = DeliveryOffer.CODEC.parse(ops, JsonParser.parseReader(reader));
                if (result.error().isPresent()) {
                    MobsOnDemandMod.LOGGER.error("Skipping shop offer {} ({}): {}", id, file.getKey(), result.error().get().message());
                    continue;
                }
                DeliveryOffer offer = result.getOrThrow();
                if (offer.enabled()) {
                    loaded.add(new Entry(id.toString(), offer));
                } else {
                    disabled++;
                }
            } catch (Exception e) {
                MobsOnDemandMod.LOGGER.error("Skipping shop offer {} ({}): {}", id, file.getKey(), e.getMessage());
            }
        }
        loaded.sort(Comparator.comparingInt((Entry entry) -> entry.offer().sort()).thenComparing(Entry::id));
        return new Loaded(List.copyOf(loaded), disabled);
    }

    /** Encodes the loaded shop for {@link net.bandit.mobs_on_demand.network.ShopSyncPayload}. */
    public static CompoundTag encode(HolderLookup.Provider registries) {
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", entry.id());
            tag.put("offer", DeliveryOffer.CODEC.encodeStart(ops, entry.offer()).getOrThrow());
            list.add(tag);
        }
        CompoundTag root = new CompoundTag();
        root.put("offers", list);
        return root;
    }

    /** Decodes a synced shop (client side). Offers the client can't decode are skipped but keep their slot. */
    public static List<Entry> decode(CompoundTag root, HolderLookup.Provider registries) {
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        List<Entry> decoded = new ArrayList<>();
        for (Tag element : root.getListOrEmpty("offers")) {
            if (!(element instanceof CompoundTag tag)) {
                continue;
            }
            String id = tag.getStringOr("id", "?");
            DeliveryOffer offer = tag.get("offer") == null ? null
                    : DeliveryOffer.CODEC.parse(ops, tag.get("offer")).resultOrPartial(
                    error -> MobsOnDemandMod.LOGGER.warn("Couldn't read synced shop offer {}: {}", id, error)).orElse(null);
            decoded.add(new Entry(id, offer));
        }
        return decoded;
    }
}
