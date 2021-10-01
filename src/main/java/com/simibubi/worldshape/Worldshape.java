package com.simibubi.worldshape;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.simibubi.worldshape.structure.WStructureRegistry;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.FMLNetworkConstants;

@Mod("worldshape")
public class Worldshape {
	public static final Logger LOGGER = LogManager.getLogger();
	public static final String ID = "worldshape";

	public Worldshape() {
		IEventBus forgeEventBus = MinecraftForge.EVENT_BUS;
		IEventBus modEventBus = FMLJavaModLoadingContext.get()
			.getModEventBus();

		WStructureRegistry.load();
		WStructureRegistry.CUSTOM_STRUCTURES.register(modEventBus);
		WStructureRegistry.CUSTOM_FEATURES.register(modEventBus);

		modEventBus.addListener(this::setup);
		modEventBus.addListener(this::clientSetup);

		forgeEventBus.addListener(WStructureRegistry::addDimensionalSpacing);
		forgeEventBus.addListener(WStructureRegistry::populateBiomes);

		ModLoadingContext.get()
			.registerExtensionPoint(ExtensionPoint.DISPLAYTEST,
				() -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (a, b) -> true));
	}

	void setup(final FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			WStructureRegistry.setupStructureSpacing();
			WStructureRegistry.registerConfiguredStructures();
		});
	}

	void clientSetup(final FMLClientSetupEvent event) {}

	public static ResourceLocation asResource(String path) {
		return new ResourceLocation(ID, path);
	}

}
