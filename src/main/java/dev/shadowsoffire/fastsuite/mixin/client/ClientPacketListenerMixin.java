package dev.shadowsoffire.fastsuite.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import dev.shadowsoffire.fastsuite.AuxRecipeManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.item.crafting.RecipeManager;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

	@Shadow
	@SuppressWarnings("deprecation")
	private final RecipeManager recipeManager = new AuxRecipeManager();

}
