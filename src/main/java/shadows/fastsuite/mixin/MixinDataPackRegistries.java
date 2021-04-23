package shadows.fastsuite.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.command.Commands;
import net.minecraft.item.crafting.RecipeManager;
import net.minecraft.resources.DataPackRegistries;
import net.minecraft.resources.IReloadableResourceManager;
import shadows.fastsuite.AuxRecipeManager;
import shadows.fastsuite.FastSuite;

@Mixin(DataPackRegistries.class)
public class MixinDataPackRegistries {

	@Shadow
	private final RecipeManager recipeManager = new AuxRecipeManager();

	@Inject(at = @At("TAIL"), method = "<init>(Lnet/minecraft/command/Commands$EnvironmentType;I)V")
	private void init(Commands.EnvironmentType envType, int permissionsLevel, CallbackInfo info) {
		DataPackRegistries dpr = (DataPackRegistries) (Object) this;
		FastSuite.reload(dpr.getRecipeManager(), (IReloadableResourceManager) dpr.getResourceManager());
	}

}
