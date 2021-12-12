package shadows.fastsuite.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.item.crafting.RecipeManager;
import shadows.fastsuite.AuxRecipeManager;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

	@Shadow
	private final RecipeManager recipeManager = new AuxRecipeManager();

}
