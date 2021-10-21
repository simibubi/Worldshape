package com.simibubi.worldshape.structure;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.simibubi.worldshape.Worldshape;
import com.simibubi.worldshape.foundation.FilesHelper;
import com.simibubi.worldshape.foundation.Lang;

import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.WorldGenRegistries;
import net.minecraft.world.World;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.DimensionSettings;
import net.minecraft.world.gen.FlatChunkGenerator;
import net.minecraft.world.gen.FlatGenerationSettings;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.IFeatureConfig;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.feature.StructureFeature;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.settings.DimensionStructuresSettings;
import net.minecraft.world.gen.settings.StructureSeparationSettings;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.world.BiomeLoadingEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class WStructureRegistry {

	public static final Map<ResourceLocation, WStructureType> CUSTOM_TYPES = new HashMap<>();
	public static final List<ResourceLocation> COLLISION_SURFACE = new ArrayList<>();
	public static final List<ResourceLocation> COLLISION_CAVE = new ArrayList<>();

	public static final DeferredRegister<Structure<?>> CUSTOM_STRUCTURES =
		DeferredRegister.create(ForgeRegistries.STRUCTURE_FEATURES, Worldshape.ID);

	public static final DeferredRegister<Feature<?>> CUSTOM_FEATURES =
		DeferredRegister.create(ForgeRegistries.FEATURES, Worldshape.ID);

	public static void load() {
		String dataFolder = Worldshape.ID + "/data";
		FilesHelper.createFolderIfMissing(dataFolder);

		CUSTOM_TYPES.clear();
		COLLISION_SURFACE.clear();
		COLLISION_CAVE.clear();

		FilesHelper.forEachInFolder(Paths.get(dataFolder), "/", packpath -> {
			Path structuresFolder = packpath.resolve("structure_spawners");
			if (!Files.exists(structuresFolder))
				return;
			String packId = packpath.getFileName()
				.toString();

			FilesHelper.forEachInFolder(structuresFolder, ".json", path -> {
				String filename = path.getFileName()
					.toString()
					.replace(".json", "");

				ResourceLocation id = Worldshape.asResource(packId + "/" + Lang.asId(FilesHelper.slug(filename)));
				WStructureType type = new WStructureType(id);
				type.deserializeJSON(FilesHelper.loadJson(path.toString())
					.getAsJsonObject());

				if (type.getPlacement()
					.isFeaturePlacement()) {
					type.registryFeatureObject =
						CUSTOM_FEATURES.register(id.getPath(), () -> new WCaveStructureFeature(type));
					CUSTOM_TYPES.put(id, type);
					if (type.shouldAvoidOthers())
						COLLISION_CAVE.add(id);

				} else {
					type.registryStructureObject = CUSTOM_STRUCTURES.register(id.getPath(), () -> new WStructure(type));
					CUSTOM_TYPES.put(id, type);
					if (type.shouldAvoidOthers())
						COLLISION_SURFACE.add(id);
				}

			});

		});
	}

	public static void registerConfiguredStructures() {
		for (WStructureType type : CUSTOM_TYPES.values()) {

			if (type.getPlacement()
				.isFeaturePlacement()) {
				type.configuredFeature = type.registryFeatureObject.get()
					.configured(IFeatureConfig.NONE);
				Registry<ConfiguredFeature<?, ?>> registry = WorldGenRegistries.CONFIGURED_FEATURE;
				Registry.register(registry, type.getId(), type.configuredFeature);

			} else {
				type.configuredStructureFeature = type.registryStructureObject.get()
					.configured(IFeatureConfig.NONE);
				Registry<StructureFeature<?, ?>> registry = WorldGenRegistries.CONFIGURED_STRUCTURE_FEATURE;
				Registry.register(registry, type.getId(), type.configuredStructureFeature);
				FlatGenerationSettings.STRUCTURE_FEATURES.put(type.registryStructureObject.get(),
					type.configuredStructureFeature);
			}
		}
	}

	public static void populateBiomes(BiomeLoadingEvent event) {
		for (WStructureType type : CUSTOM_TYPES.values()) {
			if (type.canGenerateIn(event.getName(), event.getCategory())) {
				if (type.getPlacement()
					.isFeaturePlacement()) {
					event.getGeneration()
						.addFeature(type.getPlacement()
							.getDecorationStage()
							.ordinal(), () -> type.configuredFeature);

				} else {
					event.getGeneration()
						.getStructures()
						.add(() -> type.configuredStructureFeature);
				}
			}
		}
	}

	public static void setupStructureSpacing() {
		for (WStructureType type : CUSTOM_TYPES.values()) {
			if (type.getPlacement()
				.isFeaturePlacement())
				continue;

			Structure<?> structure = type.registryStructureObject.get();
			StructureSeparationSettings spacing = type.separationSettings;

			Structure.STRUCTURES_REGISTRY.put(type.getId()
				.toString(), structure);

			if (type.getPlacement()
				.getTerraformedSurfaceOffset()
				.isPresent())
				Structure.NOISE_AFFECTING_FEATURES = ImmutableList.<Structure<?>>builder()
					.addAll(Structure.NOISE_AFFECTING_FEATURES)
					.add(structure)
					.build();

			DimensionStructuresSettings.DEFAULTS = ImmutableMap.<Structure<?>, StructureSeparationSettings>builder()
				.putAll(DimensionStructuresSettings.DEFAULTS)
				.put(structure, spacing)
				.build();

			for (Entry<RegistryKey<DimensionSettings>, DimensionSettings> settings : WorldGenRegistries.NOISE_GENERATOR_SETTINGS
				.entrySet()) {
				if (!type.canGenerateIn(settings.getKey()
					.location()))
					continue;

				Map<Structure<?>, StructureSeparationSettings> structureMap = settings.getValue()
					.structureSettings()
					.structureConfig();

				if (structureMap instanceof ImmutableMap) {
					Map<Structure<?>, StructureSeparationSettings> tempMap = new HashMap<>(structureMap);
					tempMap.put(structure, spacing);
					settings.getValue()
						.structureSettings().structureConfig = tempMap;
				} else
					structureMap.put(structure, spacing);
			}
		}
	}

	private static Method GETCODEC_METHOD;

	public static void addDimensionalSpacing(final WorldEvent.Load event) {
		if (!(event.getWorld() instanceof ServerWorld))
			return;

		ServerWorld serverWorld = (ServerWorld) event.getWorld();

		try {
			if (GETCODEC_METHOD == null)
				GETCODEC_METHOD = ObfuscationReflectionHelper.findMethod(ChunkGenerator.class, "func_230347_a_");
			@SuppressWarnings("unchecked")
			ResourceLocation cgRL = Registry.CHUNK_GENERATOR.getKey(
				(Codec<? extends ChunkGenerator>) GETCODEC_METHOD.invoke(serverWorld.getChunkSource().generator));
			if (cgRL != null && cgRL.getNamespace()
				.equals("terraforged"))
				return;
		} catch (Exception e) {
		}

		RegistryKey<World> dimension = serverWorld.dimension();
		ChunkGenerator generator = serverWorld.getChunkSource()
			.getGenerator();

		if (generator instanceof FlatChunkGenerator && dimension.equals(World.OVERWORLD))
			return;

		DimensionStructuresSettings settings = generator.getSettings();
		Map<Structure<?>, StructureSeparationSettings> settingMap = new HashMap<>(settings.structureConfig());

		for (WStructureType type : CUSTOM_TYPES.values()) {
			if (type.getPlacement()
				.isFeaturePlacement())
				continue;

			Structure<NoFeatureConfig> structure = type.registryStructureObject.get();
			if (type.canGenerateIn(dimension.location()))
				settingMap.putIfAbsent(structure, DimensionStructuresSettings.DEFAULTS.get(structure));
			else
				settingMap.remove(structure);
		}

		settings.structureConfig = settingMap;
	}

	public static WStructureType getType(ResourceLocation id) {
		return CUSTOM_TYPES.get(id);
	}

}
