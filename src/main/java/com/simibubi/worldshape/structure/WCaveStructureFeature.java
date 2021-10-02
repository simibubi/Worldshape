package com.simibubi.worldshape.structure;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SharedSeedRandom;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.feature.jigsaw.JigsawManager;
import net.minecraft.world.gen.feature.structure.AbstractVillagePiece;
import net.minecraft.world.gen.feature.structure.StructurePiece;
import net.minecraft.world.gen.feature.structure.VillageConfig;
import net.minecraft.world.gen.feature.template.TemplateManager;
import net.minecraft.world.gen.settings.StructureSeparationSettings;
import net.minecraft.world.server.ServerWorld;

public class WCaveStructureFeature extends Feature<NoFeatureConfig> {

	private WStructureType definition;

	public WCaveStructureFeature(WStructureType definition) {
		super(NoFeatureConfig.CODEC);
		this.definition = definition;
	}

	public static ChunkPos getPotentialFeatureChunk(StructureSeparationSettings settings, long p_236392_2_,
		SharedSeedRandom random, int chunkX, int chunkZ) {
		int i = settings.spacing();
		int j = settings.separation();
		int k = Math.floorDiv(chunkX, i);
		int l = Math.floorDiv(chunkZ, i);
		random.setLargeFeatureWithSalt(p_236392_2_, k, l, settings.salt());
		int i1 = random.nextInt(i - j);
		int j1 = random.nextInt(i - j);
		return new ChunkPos(k * i + i1, l * i + j1);
	}

	@Override
	public boolean place(ISeedReader reader, ChunkGenerator chunkGenerator, Random random, BlockPos pos,
		NoFeatureConfig config) {

		int x = pos.getX();
		int z = pos.getZ();

		ServerWorld level = reader.getLevel();
		if (level == null)
			return false;

		ResourceLocation currentDim = level.dimension()
			.location();
		if (!definition.canGenerateIn(currentDim))
			return false;

		int chunkX = x / 16;
		int chunkZ = z / 16;
		SharedSeedRandom seedRandom = new SharedSeedRandom();
		long seed = reader.getSeed();
		ChunkPos featureChunk =
			getPotentialFeatureChunk(definition.separationSettings, seed, seedRandom, chunkX, chunkZ);
		if (featureChunk.x != chunkX || featureChunk.z != chunkZ)
			return false;

		WStructureTemplate template = definition.getTemplate();
		WStructurePlacement placement = definition.getPlacement();

		DynamicRegistries dynamicRegistryManager = level.registryAccess();
		TemplateManager structureManager = level.getStructureManager();
		VillageConfig jigsawConfig = new VillageConfig(template.create(dynamicRegistryManager), definition.getTemplate()
			.getMaxDepth());

		int surface = chunkGenerator.getFirstFreeHeight(x, z, Heightmap.Type.WORLD_SURFACE_WG);
		Optional<Integer> determineY = placement.determineY(chunkGenerator, reader, seedRandom, x, z, surface);
		if (!determineY.isPresent())
			return false;

		if (definition.shouldAvoidOthers()) {
			List<ResourceLocation> structures = WStructureRegistry.COLLISION_CAVE;
			Map<ResourceLocation, WStructureType> types = WStructureRegistry.CUSTOM_TYPES;

			for (int i = structures.indexOf(definition.getId()) + 1; i < structures.size(); i++) {
				WStructureType wStructureType = types.get(structures.get(i));
				if (!wStructureType.canGenerateIn(currentDim))
					continue;
				Biome biome = reader.getBiome(pos);
				if (!wStructureType.canGenerateIn(biome.getRegistryName(), biome.getBiomeCategory()))
					continue;

				SharedSeedRandom seedRandomOther = new SharedSeedRandom();
				ChunkPos potentialFeatureChunk =
					getPotentialFeatureChunk(wStructureType.separationSettings, seed, seedRandomOther, chunkX, chunkZ);
				if (potentialFeatureChunk.x != chunkX || potentialFeatureChunk.z != chunkZ)
					continue;
				if (wStructureType.getPlacement()
					.determineY(chunkGenerator, reader, seedRandom, x, z, surface)
					.isPresent())
					return false;
			}
		}

		List<StructurePiece> pieces = new ArrayList<>();
		BlockPos centerPos = new BlockPos(x, determineY.get() + placement.getSurfaceOffset(), z);
		JigsawManager.addPieces(dynamicRegistryManager, jigsawConfig, AbstractVillagePiece::new, chunkGenerator,
			structureManager, centerPos, pieces, random, false, false);

		Vector3i structureCenter = pieces.get(0)
			.getBoundingBox()
			.getCenter();

		int xOffset = centerPos.getX() - structureCenter.getX();
		int zOffset = centerPos.getZ() - structureCenter.getZ();
		for (StructurePiece structurePiece : pieces)
			structurePiece.move(xOffset, 0, zOffset);

		MutableBoundingBox boundingBox = MutableBoundingBox.getUnknownBox();
		for (StructurePiece structurepiece : pieces)
			boundingBox.expand(structurepiece.getBoundingBox());

		if (pieces.isEmpty())
			return false;

		MutableBoundingBox firstBB = (pieces.get(0)).getBoundingBox();
		Vector3i vector3i = firstBB.getCenter();
		BlockPos blockpos = new BlockPos(vector3i.getX(), firstBB.y0, vector3i.getZ());
		Iterator<StructurePiece> iterator = pieces.iterator();

		while (iterator.hasNext()) {
			StructurePiece structurepiece = iterator.next();
			structurepiece.postProcess(reader, level.structureFeatureManager(), chunkGenerator, random, boundingBox,
				new ChunkPos(blockpos), blockpos);
		}

		return true;
	}

}
