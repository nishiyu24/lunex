package com.nishiyu.lunex.chemistry.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.nishiyu.lunex.chemistry.MaterialRegistry;
import com.nishiyu.lunex.chemistry.MaterialRegistryUtil;
import com.nishiyu.lunex.chemistry.model.BaseInk;
import com.nishiyu.lunex.chemistry.model.InkState;
import com.nishiyu.lunex.chemistry.model.ItemDNA;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class MaterialDataLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaterialDataLoader.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Map<String, ItemDNA> resolvedPresets = new HashMap<>();
    private final Map<ResourceLocation, ItemDNA> resolvedItems = new HashMap<>();
    private final Map<ResourceLocation, ItemDNA> resolvedTags = new HashMap<>();

    public MaterialDataLoader() {
        super(GSON, "chemistry/materials");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        resolvedPresets.clear();
        resolvedItems.clear();
        resolvedTags.clear();

        List<MaterialJsonDefinition> pendingDefinitions = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            try {
                MaterialJsonDefinition def = GSON.fromJson(entry.getValue(), MaterialJsonDefinition.class);
                if (def != null && def.getTarget() != null) {
                    pendingDefinitions.add(def);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to parse material JSON: {}", entry.getKey(), e);
            }
        }

        boolean progressed = true;
        while (!pendingDefinitions.isEmpty() && progressed) {
            progressed = false;
            Iterator<MaterialJsonDefinition> it = pendingDefinitions.iterator();

            while (it.hasNext()) {
                MaterialJsonDefinition def = it.next();
                String baseRef = def.getBase();

                ItemDNA baseDNA = null;
                boolean canResolve = true;

                if (baseRef != null && !baseRef.isEmpty()) {
                    baseDNA = findResolvedDNA(baseRef);
                    if (baseDNA == null) {
                        canResolve = false;
                    }
                }

                if (canResolve) {
                    ItemDNA dna = (baseDNA != null) ? new ItemDNA(baseDNA) : new ItemDNA();
                    applyDifferences(dna, def);
                    registerResolved(def, dna);

                    it.remove();
                    progressed = true;
                }
            }
        }

        if (!pendingDefinitions.isEmpty()) {
            for (MaterialJsonDefinition unres : pendingDefinitions) {
                LOGGER.error("Could not resolve material dependency: target '{}' requires base '{}'",
                        unres.getTarget(), unres.getBase());
            }
        }

        LOGGER.info("Successfully loaded {} item DNA materials from datapacks.",
                resolvedItems.size() + resolvedTags.size() + resolvedPresets.size());
    }

    private ItemDNA findResolvedDNA(String reference) {
        String[] parts = reference.split(":", 2);
        if (parts.length < 2) {
            return resolvedPresets.get(reference);
        }

        String type = parts[0].toLowerCase(Locale.ROOT);
        String idStr = parts[1];

        return switch (type) {
            case "preset" -> resolvedPresets.get(idStr);
            case "item" -> resolvedItems.get(ResourceLocation.parse(idStr));
            case "tag" -> resolvedTags.get(ResourceLocation.parse(idStr));
            default -> {
                if (resolvedPresets.containsKey(reference)) {
                    yield resolvedPresets.get(reference);
                }
                yield resolvedItems.get(ResourceLocation.parse(reference));
            }
        };
    }

    private void applyDifferences(ItemDNA dna, MaterialJsonDefinition def) {
        if (def.getDna() != null) {
            List<String> dnalist = def.getDna();
            for (int i = 0; i < ItemDNA.DNA_LENGTH && i < dnalist.size(); i++) {
                String entry = dnalist.get(i);
                if (entry == null || entry.equalsIgnoreCase("none")) {
                    dna.setGene(i, BaseInk.NONE, InkState.NONE);
                    continue;
                }

                String[] parts = entry.split(":");
                try {
                    BaseInk ink = BaseInk.valueOf(parts[0].toUpperCase(Locale.ROOT));
                    InkState state = InkState.SOLID; // 状態が省略された場合のデフォルト

                    if (parts.length > 1) {
                        state = InkState.valueOf(parts[1].toUpperCase(Locale.ROOT));
                    }
                    dna.setGene(i, ink, state);
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Unknown ink or state in material '{}': {}", def.getTarget(), entry);
                }
            }
        }
    }

    private void registerResolved(MaterialJsonDefinition def, ItemDNA dna) {
        String target = def.getTarget();
        String targetType = def.getTargetType();

        switch (targetType) {
            case "preset" -> resolvedPresets.put(target, dna);
            case "tag" -> {
                ResourceLocation tagLoc = ResourceLocation.parse(target);
                resolvedTags.put(tagLoc, dna);
                MaterialRegistryUtil.registerTag(TagKey.create(Registries.ITEM, tagLoc), dna);
            }
            case "item" -> {
                ResourceLocation itemLoc = ResourceLocation.parse(target);
                resolvedItems.put(itemLoc, dna);
                MaterialRegistry.registerBaseMaterial(itemLoc, dna);
            }
            default -> LOGGER.warn("Unknown target_type '{}' for '{}'", targetType, target);
        }
    }
}