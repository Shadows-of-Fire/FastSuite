package shadows.fastsuite.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.item.crafting.RecipeManager;
import shadows.fastsuite.AuxRecipeManager;

@Mixin(ClientPlayNetHandler.class)
public class MixinClientPlayNetHandler {

	@Shadow
	private final RecipeManager recipeManager = new AuxRecipeManager();

}
