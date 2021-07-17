package shadows.fastsuite.mixin;

import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.item.crafting.RecipeManager;
import net.minecraft.resources.DataPackRegistries;
import shadows.fastsuite.AuxRecipeManager;

@Mixin(DataPackRegistries.class)
public class MixinDataPackRegistries {

	@Shadow
	private final RecipeManager recipeManager = new AuxRecipeManager();

	@Inject(at = @At("TAIL"), method = "func_240961_a_(Ljava/util/List;Lnet/minecraft/command/Commands$EnvironmentType;ILjava/util/concurrent/Executor;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;", cancellable = true)
	private static void func_240961_a_(CallbackInfoReturnable<CompletableFuture<DataPackRegistries>> info) {
		info.setReturnValue(info.getReturnValue().thenApply((dpr) -> {
			((AuxRecipeManager) dpr.getRecipeManager()).processInitialRecipes(dpr.getRecipeManager().recipes);
			return dpr;
		}));
	}

}
