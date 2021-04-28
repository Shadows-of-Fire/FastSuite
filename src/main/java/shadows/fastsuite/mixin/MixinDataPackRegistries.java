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
import net.minecraftforge.event.ForgeEventFactory;
import shadows.fastsuite.AuxRecipeManager;
import shadows.placebo.util.RunnableReloader;

@Mixin(DataPackRegistries.class)
public class MixinDataPackRegistries {

	@Shadow
	private final RecipeManager recipeManager = new AuxRecipeManager();

	@Inject(at = @At("TAIL"), method = "<init>(Lnet/minecraft/command/Commands$EnvironmentType;I)V")
	private void init(Commands.EnvironmentType envType, int permissionsLevel, CallbackInfo info) {
		DataPackRegistries dpr = (DataPackRegistries) (Object) this;
		reload(dpr.getRecipeManager(), (IReloadableResourceManager) dpr.getResourceManager());
	}

	/**
	 * ASM Hook: Called from {@link #DataPackRegistries()}<br>
	 * Called after {@link ForgeEventFactory#onResourceReload()}
	 * @param mgr The Recipe Manager, accessed from the DPR's constructor.
	 * @param rel The resource reload manager from the same location.
	 */
	public static void reload(RecipeManager mgr, IReloadableResourceManager rel) {
		rel.addReloadListener(RunnableReloader.of(() -> {
			((AuxRecipeManager) mgr).processInitialRecipes(mgr.recipes);
		}));
	}

}
