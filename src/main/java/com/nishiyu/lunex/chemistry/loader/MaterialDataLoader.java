package com.nishiyu.lunex.chemistry.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.nishiyu.lunex.chemistry.ChemicalRegistry;
import com.nishiyu.lunex.chemistry.MaterialRegistryUtil;
import com.nishiyu.lunex.chemistry.model.Compound;
import com.nishiyu.lunex.chemistry.model.Element;
import com.nishiyu.lunex.chemistry.model.Molecule;
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

    private final Map<String, Molecule> resolvedPresets = new HashMap<>();
    private final Map<ResourceLocation, Molecule> resolvedItems = new HashMap<>();
    private final Map<ResourceLocation, Molecule> resolvedTags = new HashMap<>();

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

                Molecule baseMolecule = null;
                boolean canResolve = true;

                if (baseRef != null && !baseRef.isEmpty()) {
                    baseMolecule = findResolvedMolecule(baseRef);
                    if (baseMolecule == null) {
                        canResolve = false;
                    }
                }

                if (canResolve) {
                    Molecule molecule = (baseMolecule != null) ? new Molecule(baseMolecule) : new Molecule();
                    applyDifferences(molecule, def);
                    registerResolved(def, molecule);

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

        LOGGER.info("Successfully loaded {} chemical materials from datapacks.",
                resolvedItems.size() + resolvedTags.size() + resolvedPresets.size());
    }

    private Molecule findResolvedMolecule(String reference) {
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

    private void applyDifferences(Molecule molecule, MaterialJsonDefinition def) {
        for (Map.Entry<String, Integer> entry : def.getCompounds().entrySet()) {
            try {
                Compound compound = Compound.valueOf(entry.getKey().toUpperCase(Locale.ROOT));
                molecule.addCompound(compound, entry.getValue());
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Unknown compound name '{}' in material '{}'", entry.getKey(), def.getTarget());
            }
        }

        for (Map.Entry<String, Integer> entry : def.getElements().entrySet()) {
            try {
                Element element = Element.valueOf(entry.getKey());
                molecule.addElement(element, entry.getValue());
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Unknown element symbol '{}' in material '{}'", entry.getKey(), def.getTarget());
            }
        }
    }

    private void registerResolved(MaterialJsonDefinition def, Molecule molecule) {
        String target = def.getTarget();
        String targetType = def.getTargetType();

        switch (targetType) {
            case "preset" -> resolvedPresets.put(target, molecule);
            case "tag" -> {
                ResourceLocation tagLoc = ResourceLocation.parse(target);
                resolvedTags.put(tagLoc, molecule);
                MaterialRegistryUtil.registerTag(TagKey.create(Registries.ITEM, tagLoc), molecule);
            }
            case "item" -> {
                ResourceLocation itemLoc = ResourceLocation.parse(target);
                resolvedItems.put(itemLoc, molecule);
                ChemicalRegistry.registerBaseMaterial(itemLoc, molecule);
            }
            default -> LOGGER.warn("Unknown target_type '{}' for '{}'", targetType, target);
        }
    }
}