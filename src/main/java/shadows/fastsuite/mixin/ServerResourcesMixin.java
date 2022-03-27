package shadows.fastsuite.mixin;

import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.ReloadableServerResources;
import net.minecraft.world.item.crafting.RecipeManager;
import shadows.fastsuite.AuxRecipeManager;

@Mixin(ReloadableServerResources.class)
public class ServerResourcesMixin {

	@Shadow
	private final RecipeManager recipes = new AuxRecipeManager();

	@Inject(at = @At("TAIL"), method = "loadResources(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/core/RegistryAccess$Frozen;Lnet/minecraft/commands/Commands$CommandSelection;ILjava/util/concurrent/Executor;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;", cancellable = true)
	private static void loadResources(CallbackInfoReturnable<CompletableFuture<ReloadableServerResources>> info) {
		info.setReturnValue(info.getReturnValue().thenApply(dpr -> {
			((AuxRecipeManager) dpr.getRecipeManager()).processInitialRecipes(dpr.getRecipeManager().recipes);
			return dpr;
		}));
	}

}
