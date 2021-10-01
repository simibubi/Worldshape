package com.simibubi.worldshape.structure;

import java.util.function.Supplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;
import com.simibubi.worldshape.Worldshape;

import net.minecraft.util.JSONUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.gen.feature.jigsaw.JigsawPattern;

public abstract class WStructureTemplate {

	ResourceLocation templateId;

	public static WStructureTemplate get(ResourceLocation id, JsonElement json) {
		if (json.isJsonObject()) {
			JsonObject jo = json.getAsJsonObject();
			String type = JSONUtils.getAsString(jo, "type", "");
			if (type.equals("single"))
				return new SimpleTemplate().read(jo);
			if (type.equals("jigsaw"))
				return new JigsawTemplate().read(jo);
		}
		throw new JsonSyntaxException("Cannot find Structure template information for " + id);
	}

	WStructureTemplate read(JsonObject json) {
		templateId = new ResourceLocation(JSONUtils.getAsString(json, "id"));
		return this;
	}

	//

	public Supplier<JigsawPattern> create(DynamicRegistries registries) {
		return () -> createInner(registries);
	}

	protected abstract JigsawPattern createInner(DynamicRegistries registries);

	//

	static class SimpleTemplate extends WStructureTemplate {

		JsonObject generatedJigsaw = new JsonObject();
		JigsawPattern cached = null;
		boolean rigid;

		@Override
		WStructureTemplate read(JsonObject json) {
			super.read(json);
			rigid = JSONUtils.getAsString(json, "projection", "rigid")
				.equals("rigid");

			String empty = "minecraft:empty";

			JsonArray elements = new JsonArray();
			JsonObject entry = new JsonObject();
			JsonObject element = new JsonObject();

			element.add("processors", new JsonArray());
			element.addProperty("location", templateId.toString());
			element.addProperty("projection", rigid ? "rigid" : "terrain_matching");
			element.addProperty("element_type", "minecraft:single_pool_element");

			entry.add("element", element);
			entry.addProperty("weight", 1);
			elements.add(entry);

			generatedJigsaw.add("elements", elements);
			generatedJigsaw.addProperty("name", templateId.toString() + "_pool");
			generatedJigsaw.addProperty("fallback", empty);
			return this;
		}

		@Override
		protected JigsawPattern createInner(DynamicRegistries registries) {
			if (cached == null)
				cached = JigsawPattern.CODEC.decode(JsonOps.INSTANCE, generatedJigsaw)
					.getOrThrow(false, Worldshape.LOGGER::error)
					.getFirst()
					.get();
			return cached;
		}

	}

	static class JigsawTemplate extends WStructureTemplate {

		@Override
		WStructureTemplate read(JsonObject json) {
			super.read(json);
			return this;
		}

		@Override
		protected JigsawPattern createInner(DynamicRegistries registries) {
			return registries.registryOrThrow(Registry.TEMPLATE_POOL_REGISTRY)
				.getOptional(templateId)
				.orElseThrow(
					() -> new IllegalStateException("No template pool " + templateId + " exists in loaded Datapacks"));
		}

	}

}
