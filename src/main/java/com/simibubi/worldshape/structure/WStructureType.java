package com.simibubi.worldshape.structure;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import com.google.gson.JsonObject;

import net.minecraft.util.JSONUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.feature.StructureFeature;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.settings.StructureSeparationSettings;
import net.minecraftforge.fml.RegistryObject;

public class WStructureType {
	private ResourceLocation id;
	private WStructurePlacement placement;
	private WStructureTemplate template;
	private boolean avoidOthers;

	private Set<ResourceLocation> biomes;
	private Set<ResourceLocation> dimensions;
	private Set<String> biomeCategories;

	// Instance
	public StructureSeparationSettings separationSettings;

	// Instance (if structure)
	public RegistryObject<Structure<NoFeatureConfig>> registryStructureObject;
	public StructureFeature<?, ?> configuredStructureFeature;

	// Instance (if feature)
	public RegistryObject<Feature<NoFeatureConfig>> registryFeatureObject;
	public ConfiguredFeature<?, ?> configuredFeature;

	public WStructureType(ResourceLocation id) {
		this.id = id;
	}

	public void deserializeJSON(JsonObject object) {
		placement = WStructurePlacement.get(id, JSONUtils.getAsJsonObject(object, "placement"));
		template = WStructureTemplate.get(id, JSONUtils.getAsJsonObject(object, "template"));
		avoidOthers = JSONUtils.getAsBoolean(object, "avoidOthers", placement.avoidVillages());

		int averageSpacing = JSONUtils.getAsInt(object, "averageSpacing");
		int minimumSpacing = JSONUtils.getAsInt(object, "minimumSpacing", averageSpacing / 2);
		int salt = JSONUtils.getAsInt(object, "seed", new Random(id.toString()
			.hashCode()).nextInt());
		separationSettings = new StructureSeparationSettings(averageSpacing, minimumSpacing, salt);

		if (object.has("biomes")) {
			biomes = new HashSet<>();
			biomeCategories = new HashSet<>();
			JSONUtils.getAsJsonArray(object, "biomes")
				.forEach(je -> {
					String biome = je.getAsString();
					if (biome.startsWith("#"))
						biomeCategories.add(biome.substring(1));
					else
						this.biomes.add(new ResourceLocation(biome));
				});
		}

		if (object.has("dimensions")) {
			dimensions = new HashSet<>();
			JSONUtils.getAsJsonArray(object, "dimensions")
				.forEach(je -> this.dimensions.add(new ResourceLocation(je.getAsString())));
		}

	}

	public ResourceLocation getId() {
		return id;
	}

	public boolean canGenerateIn(ResourceLocation biome, Biome.Category category) {
		return biomes == null || biomes.contains(biome) || biomeCategories.contains(category.getName());
	}

	public boolean canGenerateIn(ResourceLocation dimension) {
		return dimensions == null || dimensions.contains(dimension);
	}

	public WStructurePlacement getPlacement() {
		return placement;
	}

	public WStructureTemplate getTemplate() {
		return template;
	}

	public boolean shouldAvoidOthers() {
		return avoidOthers;
	}

}
