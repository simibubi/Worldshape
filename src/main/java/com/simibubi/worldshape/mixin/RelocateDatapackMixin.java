package com.simibubi.worldshape.mixin;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.worldshape.WorldshapePack;

import net.minecraft.resources.ResourcePackInfo;
import net.minecraft.resources.ResourcePackList;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.packs.ModFileResourcePack;
import net.minecraftforge.fml.packs.ResourcePackLoader;
import net.minecraftforge.fml.packs.ResourcePackLoader.IPackInfoFinder;

@Mixin(ResourcePackLoader.class)
public class RelocateDatapackMixin {

    @Inject(method = "loadResourcePacks(Lnet/minecraft/resources/ResourcePackList;Ljava/util/function/BiFunction;)V", at = @At("RETURN"), remap = false)
    private static void relocateDatapack(ResourcePackList resourcePacks,
        BiFunction<Map<ModFile, ? extends ModFileResourcePack>, BiConsumer<? super ModFileResourcePack, ? extends ResourcePackInfo>, IPackInfoFinder> packFinder,
        CallbackInfo callback) {
        WorldshapePack.relocateModFileDatapack();
    }

}
