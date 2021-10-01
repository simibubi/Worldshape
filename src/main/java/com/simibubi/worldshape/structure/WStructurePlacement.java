package com.simibubi.worldshape.structure;

import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SharedSeedRandom;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.GenerationStage.Decoration;
import net.minecraft.world.gen.Heightmap.Type;

public abstract class WStructurePlacement {

	int minY;
	int maxY;
	int surfaceOffset;
	boolean needsWater;
	Optional<Integer> terraform;
	
	public static WStructurePlacement get(ResourceLocation id, JsonElement json) {
		if (json.isJsonObject()) {
			JsonObject jo = json.getAsJsonObject();
			String type = JSONUtils.getAsString(jo, "type", "");
			if (type.equals("surface"))
				return new SurfacePlacement().read(jo);
			if (type.equals("underground"))
				return new UndergroundPlacement().read(jo);
			if (type.equals("nether"))
				return new NetherPlacement().read(jo);
			if (type.equals("cave"))
				return new CavePlacement().read(jo);
		}
		throw new JsonSyntaxException("Cannot find Structure placement information for " + id);
	}

	WStructurePlacement read(JsonObject json) {
		maxY = JSONUtils.getAsInt(json, "maxY", 256);
		minY = JSONUtils.getAsInt(json, "minY", 0);
		surfaceOffset = JSONUtils.getAsInt(json, "offset", 0);
		needsWater = JSONUtils.getAsBoolean(json, "fluidSurface", false);
		terraform = Optional.ofNullable(json.has("terraform") ? JSONUtils.getAsInt(json, "terraform", -1) : null);
		return this;
	}

	//

	public boolean isFeaturePlacement() {
		return false;
	}

	public boolean avoidVillages() {
		return false;
	}

	public Decoration getDecorationStage() {
		return Decoration.SURFACE_STRUCTURES;
	}

	public Optional<Integer> getTerraformedSurfaceOffset() {
		return terraform;
	}

	public int getSurfaceOffset() {
		return surfaceOffset;
	}

	public boolean fluidSurface() {
		return needsWater;
	}

	public final Optional<Integer> determineY(ChunkGenerator generator, IBlockReader reader, SharedSeedRandom random,
		int x, int z, int surfaceY) {
		return determineYInternal(generator, reader, random, x, z, surfaceY).filter(i -> i < maxY && i >= minY);
	}

	protected abstract Optional<Integer> determineYInternal(ChunkGenerator generator, IBlockReader reader,
		SharedSeedRandom random, int x, int z, int surfaceY);

	//

	static class SurfacePlacement extends WStructurePlacement {

		@Override
		public Optional<Integer> determineYInternal(ChunkGenerator generator, IBlockReader reader,
			SharedSeedRandom random, int x, int z, int surfaceY) {
			return Optional.of(surfaceY);
		}

		@Override
		public boolean avoidVillages() {
			return true;
		}

	}

	static class NetherPlacement extends WStructurePlacement {

		boolean atCeiling;
		boolean pickHighest;
		int minSpace;

		@Override
		WStructurePlacement read(JsonObject json) {
			atCeiling = JSONUtils.getAsBoolean(json, "ceiling", false);
			pickHighest = JSONUtils.getAsBoolean(json, "pickHighest", false);
			minSpace = JSONUtils.getAsInt(json, "minSpace", 1);
			return super.read(json);
		}

		@Override
		protected Optional<Integer> determineYInternal(ChunkGenerator generator, IBlockReader reader,
			SharedSeedRandom random, int x, int z, int surfaceY) {
			if (surfaceY <= minY)
				return Optional.empty();

			BlockPos.Mutable searchPos =
				new Mutable(x, pickHighest ? Math.min(surfaceY, maxY) - 1 : Math.max(1, minY), z);
			int space = 0;
			int prevShelf = 0;
			int direction = pickHighest ? -1 : 1;
			BlockState airState = getValidAirState();

			while ((!pickHighest || searchPos.getY() >= minY) && (pickHighest || searchPos.getY() < maxY)) {
				int currentY = searchPos.getY();
				boolean isAir = reader.getBlockState(searchPos) == airState;

				if (isAir) {
					if (space == 0)
						prevShelf = currentY;
					space++;
					if (space >= minSpace && !(atCeiling ^ pickHighest)
						&& reader.getBlockState(new BlockPos(x, prevShelf, z))
							.getFluidState()
							.isEmpty() != needsWater)
						return Optional.of(prevShelf);

				} else {
					if (space >= minSpace && (atCeiling ^ pickHighest)
						&& reader.getBlockState(new BlockPos(x, currentY, z))
							.getFluidState()
							.isEmpty() != needsWater)
						return Optional.of(currentY - direction);
					space = 0;
				}
				searchPos.move(0, direction, 0);
			}

			return Optional.empty();
		}

		protected BlockState getValidAirState() {
			return Blocks.AIR.defaultBlockState();
		}

		@Override
		public Decoration getDecorationStage() {
			return Decoration.UNDERGROUND_DECORATION;
		}

	}

	static class CavePlacement extends NetherPlacement {

		public CavePlacement() {}

		@Override
		protected BlockState getValidAirState() {
			return Blocks.CAVE_AIR.defaultBlockState();
		}

		@Override
		public boolean isFeaturePlacement() {
			return true;
		}

	}

	static class UndergroundPlacement extends WStructurePlacement {

		int minDistanceFromSurface;

		@Override
		WStructurePlacement read(JsonObject json) {
			super.read(json);
			minDistanceFromSurface = JSONUtils.getAsInt(json, "surfaceMargin", 0);
			return this;
		}

		@Override
		public Decoration getDecorationStage() {
			return Decoration.UNDERGROUND_DECORATION;
		}

		@Override
		protected Optional<Integer> determineYInternal(ChunkGenerator generator, IBlockReader reader,
			SharedSeedRandom random, int x, int z, int surfaceY) {
			surfaceY = generator.getBaseHeight(x, z, Type.OCEAN_FLOOR_WG);
			int actualMaxY = Math.min(maxY, surfaceY - minDistanceFromSurface);
			if (actualMaxY < minY)
				return Optional.empty();
			return Optional.of(random.nextInt(actualMaxY - minY) + minY);
		}

	}

}
