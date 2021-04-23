package shadows.fastsuite;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.item.crafting.RecipeManager;
import net.minecraft.resources.IReloadableResourceManager;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.common.Mod;
import shadows.placebo.util.RunnableReloader;

@Mod(FastSuite.MODID)
public class FastSuite {

	public static final String MODID = "fastsuite";
	public static final Logger LOG = LogManager.getLogger(MODID);

	public FastSuite() {
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
