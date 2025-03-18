package dev.shadowsoffire.fastsuite.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.shadowsoffire.fastsuite.AuxRecipeManager;
import net.minecraft.commands.Commands;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeManager;

@Mixin(value = ReloadableServerResources.class, remap = false)
public class ServerResourcesMixin {

    @Shadow
    @Mutable
    private RecipeManager recipes;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(RegistryAccess.Frozen registryAccess, FeatureFlagSet enabledFeatures, Commands.CommandSelection commandSelection, int functionCompilationLevel, CallbackInfo ci) {
        this.recipes = new AuxRecipeManager(registryAccess);
    }

}
