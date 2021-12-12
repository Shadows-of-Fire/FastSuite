package shadows.fastsuite.mixin;

import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.ServerResources;
import net.minecraft.world.item.crafting.RecipeManager;
import shadows.fastsuite.AuxRecipeManager;

@Mixin(ServerResources.class)
public class ServerResourcesMixin {

	@Shadow
	private final RecipeManager recipes = new AuxRecipeManager();

	@Inject(at = @At("TAIL"), method = "loadResources(Ljava/util/List;Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/commands/Commands$CommandSelection;ILjava/util/concurrent/Executor;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;", cancellable = true)
	private static void func_240961_a_(CallbackInfoReturnable<CompletableFuture<ServerResources>> info) {
		info.setReturnValue(info.getReturnValue().thenApply((dpr) -> {
			((AuxRecipeManager) dpr.getRecipeManager()).processInitialRecipes(dpr.getRecipeManager().recipes);
			return dpr;
		}));
	}

}
