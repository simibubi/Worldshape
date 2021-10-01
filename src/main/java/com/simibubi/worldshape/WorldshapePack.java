package com.simibubi.worldshape;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.common.base.Joiner;

import net.minecraft.resources.ResourcePackFileNotFoundException;
import net.minecraft.resources.ResourcePackType;
import net.minecraft.resources.data.IMetadataSectionSerializer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.packs.ModFileResourcePack;
import net.minecraftforge.fml.packs.ResourcePackLoader;

public class WorldshapePack extends ModFileResourcePack {

	public static void relocateModFileDatapack() {
		try {
			Field field = ResourcePackLoader.class.getDeclaredField("modResourcePacks");
			field.setAccessible(true);
			Object map = field.get(null);

			if (map instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<ModFile, ModFileResourcePack> fileMap = (Map<ModFile, ModFileResourcePack>) map;
				ModFile file = ModList.get()
					.getModFileById(Worldshape.ID)
					.getFile();

				fileMap.put(file, new WorldshapePack(file));
			}

		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
	}

	public WorldshapePack(ModFile modFile) {
		super(modFile);
	}

	@Nullable
	@SuppressWarnings("unchecked")
	public <T> T getMetadataSection(IMetadataSectionSerializer<T> p_195760_1_) throws IOException {
		// Using mcmeta from actual mod files
		Object object;
		try (InputStream inputstream = super.getResource("pack.mcmeta")) {
			object = getMetadataFromStream(p_195760_1_, inputstream);
		}
		return (T) object;
	}

	@Override
	protected InputStream getResource(String name) throws IOException {
		final Path path = getResourcePath(name);
		if (!Files.exists(path))
			throw new ResourcePackFileNotFoundException(path.toFile(), name);
		return Files.newInputStream(path, StandardOpenOption.READ);
	}

	private Path getResourcePath(String name) {
		return FMLPaths.GAMEDIR.get()
			.resolve(Paths.get(Worldshape.ID, name));
	}

	@Override
	protected boolean hasResource(String name) {
		return Files.exists(getResourcePath(name));
	}

	@Override
	public Collection<ResourceLocation> getResources(ResourcePackType type, String resourceNamespace, String pathIn,
		int maxDepth, Predicate<String> filter) {
		if (type == ResourcePackType.CLIENT_RESOURCES)
			return Collections.emptyList();

		try {
			Path root = getResourcePath(type.getDirectory()).resolve(resourceNamespace)
				.toAbsolutePath();
			Path inputPath = root.getFileSystem()
				.getPath(pathIn);
			List<ResourceLocation> resources = Files.walk(root)
				.map(path -> root.relativize(path.toAbsolutePath()))
				.filter(path -> path.getNameCount() <= maxDepth)
				.filter(path -> !path.toString()
					.endsWith(".mcmeta"))
				.filter(path -> path.startsWith(inputPath))
				.filter(path -> filter.test(path.getFileName()
					.toString()))
				.map(path -> new ResourceLocation(resourceNamespace, Joiner.on('/')
					.join(path)))
				.collect(Collectors.toList());

			return resources;

		} catch (IOException e) {
			return Collections.emptyList();
		}
	}

	@Override
	public Set<String> getNamespaces(ResourcePackType type) {
		if (type == ResourcePackType.CLIENT_RESOURCES)
			return Collections.emptySet();

		try {
			Path root = getResourcePath(type.getDirectory()).toAbsolutePath();
			Set<String> namespaces = Files.walk(root, 1)
				.map(path -> root.relativize(path.toAbsolutePath()))
				.filter(path -> path.getNameCount() > 0)
				.map(p -> p.toString()
					.replaceAll("/$", ""))
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toSet());

			return namespaces;

		} catch (IOException e) {
			return Collections.emptySet();
		}
	}

}
