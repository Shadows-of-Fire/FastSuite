package dev.shadowsoffire.fastsuite.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.shadowsoffire.fastsuite.AuxRecipeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.Connection;
import net.minecraft.world.item.crafting.RecipeManager;

@Mixin(value = ClientPacketListener.class, remap = false)
public class ClientPacketListenerMixin {

    @Shadow
    private RegistryAccess.Frozen registryAccess;

    @Shadow
    @Mutable
    private RecipeManager recipeManager;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(Minecraft minecraft, Connection connection, CommonListenerCookie commonListenerCookie, CallbackInfo ci) {
        this.recipeManager = new AuxRecipeManager(this.registryAccess);
    }

}
