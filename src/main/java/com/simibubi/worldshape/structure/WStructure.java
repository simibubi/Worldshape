package com.simibubi.worldshape.structure;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.simibubi.worldshape.foundation.Pair;

import net.minecraft.block.BlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SharedSeedRandom;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.provider.BiomeProvider;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.GenerationStage.Decoration;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.feature.jigsaw.JigsawManager;
import net.minecraft.world.gen.feature.structure.AbstractVillagePiece;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.feature.structure.StructurePiece;
import net.minecraft.world.gen.feature.structure.StructureStart;
import net.minecraft.world.gen.feature.structure.VillageConfig;
import net.minecraft.world.gen.feature.template.TemplateManager;
import net.minecraft.world.gen.settings.StructureSeparationSettings;

public class WStructure extends Structure<NoFeatureConfig> {

	private WStructureType definition;

	public WStructure(WStructureType definition) {
		super(NoFeatureConfig.CODEC);
		this.definition = definition;
	}

	@Override
	public Decoration step() {
		return definition.getPlacement()
			.getDecorationStage();
	}

	@Override
	public IStartFactory<NoFeatureConfig> getStartFactory() {
		return WStructureStart::new;
	}

	@Override
	protected boolean isFeatureChunk(ChunkGenerator generator, BiomeProvider biomeSource, long seed,
		SharedSeedRandom random, int chunkX, int chunkZ, Biome biome, ChunkPos chunkPos,
		NoFeatureConfig featureConfig) {
		int x = chunkX * 16;
		int z = chunkZ * 16;
		BlockPos centerOfChunk = new BlockPos(x, 0, z);
		int landHeight = generator.getFirstOccupiedHeight(centerOfChunk.getX(), centerOfChunk.getZ(),
			Heightmap.Type.WORLD_SURFACE_WG);

		IBlockReader column = generator.getBaseColumn(centerOfChunk.getX(), centerOfChunk.getZ());
		Optional<Integer> determineY = definition.getPlacement()
			.determineY(generator, generator.getBaseColumn(x, z), random, x, z, landHeight);
		if (!determineY.isPresent())
			return false;

		BlockState generatedOnBlock = column.getBlockState(centerOfChunk.above(determineY.get()));
		if (generatedOnBlock.getFluidState()
			.isEmpty() == definition.getPlacement()
				.fluidSurface())
			return false;

		if (definition.shouldAvoidOthers())
			if (isNearAny(generator, biomeSource, seed, random, chunkX, chunkZ, biome, chunkPos, featureConfig, 0,
				WStructureRegistry.COLLISION_SURFACE))
				return false;

		if (!definition.getPlacement()
			.avoidVillages())
			return true;

		return coordsOfNearbyStructure(generator, Structure.VILLAGE, seed, random, chunkX, chunkZ, 4) == null;
	}

	private boolean isNearAny(ChunkGenerator generator, BiomeProvider biomeSource, long seed, SharedSeedRandom random,
		int chunkX, int chunkZ, Biome biome, ChunkPos chunkPos, NoFeatureConfig featureConfig, int radius,
		List<ResourceLocation> structures) {
		Map<ResourceLocation, WStructureType> types = WStructureRegistry.CUSTOM_TYPES;

		for (int i = structures.indexOf(definition.getId()) + 1; i < structures.size(); i++) {
			WStructureType wStructureType = types.get(structures.get(i));
			if (!wStructureType.canGenerateIn(biome.getRegistryName(), biome.getBiomeCategory()))
				continue;
			Structure<?> feature = wStructureType.configuredStructureFeature.feature;
			if (!(feature instanceof WStructure))
				continue;
			Pair<Integer, Integer> coords =
				coordsOfNearbyStructure(generator, feature, seed, random, chunkX, chunkZ, radius);
			if (coords == null)
				continue;
			return true;
		}

		return false;
	}

	@Nullable
	private Pair<Integer, Integer> coordsOfNearbyStructure(ChunkGenerator generator, Structure<?> structure, long seed,
		SharedSeedRandom random, int chunkX, int chunkZ, int radius) {
		StructureSeparationSettings structureseparationsettings = generator.getSettings()
			.getConfig(structure);
		if (structureseparationsettings == null)
			return null;

		for (int i = chunkX - radius; i <= chunkX + radius; ++i) {
			for (int j = chunkZ - radius; j <= chunkZ + radius; ++j) {
				ChunkPos chunkpos = structure.getPotentialFeatureChunk(structureseparationsettings, seed, random, i, j);
				if (i == chunkpos.x && j == chunkpos.z)
					return Pair.of(i, j);
			}
		}

		return null;
	}

	public class WStructureStart extends StructureStart<NoFeatureConfig> {

		public WStructureStart(Structure<NoFeatureConfig> structureIn, int chunkX, int chunkZ,
			MutableBoundingBox mutableBoundingBox, int referenceIn, long seedIn) {
			super(structureIn, chunkX, chunkZ, mutableBoundingBox, referenceIn, seedIn);
		}

		@Override
		public void generatePieces(DynamicRegistries dynamicRegistryManager, ChunkGenerator chunkGenerator,
			TemplateManager templateManagerIn, int chunkX, int chunkZ, Biome biomeIn, NoFeatureConfig config) {
			int x = chunkX * 16;
			int z = chunkZ * 16;

			WStructureTemplate template = definition.getTemplate();
			WStructurePlacement placement = definition.getPlacement();
			VillageConfig jigsawConfig =
				new VillageConfig(template.create(dynamicRegistryManager), definition.getTemplate()
					.getMaxDepth());

			int surface = chunkGenerator.getFirstFreeHeight(x, z, Heightmap.Type.WORLD_SURFACE_WG);
			Optional<Integer> determineY =
				placement.determineY(chunkGenerator, chunkGenerator.getBaseColumn(x, z), random, x, z, surface);
			if (!determineY.isPresent())
				return;

			BlockPos centerPos = new BlockPos(x, determineY.get(), z);
			JigsawManager.addPieces(dynamicRegistryManager, jigsawConfig, AbstractVillagePiece::new, chunkGenerator,
				templateManagerIn, centerPos, pieces, random, true, false);

			Vector3i structureCenter = pieces.get(0)
				.getBoundingBox()
				.getCenter();

			int xOffset = centerPos.getX() - structureCenter.getX();
			int zOffset = centerPos.getZ() - structureCenter.getZ();
			for (StructurePiece structurePiece : pieces) {
				structurePiece.move(xOffset, placement.getSurfaceOffset(), zOffset);
				placement.getTerraformedSurfaceOffset()
					.ifPresent(i -> structurePiece.getBoundingBox().y0 += i);
			}

			calculateBoundingBox();
		}

		@Override
		protected void calculateBoundingBox() {
			super.calculateBoundingBox();

			if (!definition.getPlacement()
				.getTerraformedSurfaceOffset()
				.isPresent())
				return;

			int margin = 12;
			this.boundingBox.x0 -= margin;
			this.boundingBox.y0 -= margin;
			this.boundingBox.z0 -= margin;
			this.boundingBox.x1 += margin;
			this.boundingBox.y1 += margin;
			this.boundingBox.z1 += margin;
		}

	}

}
