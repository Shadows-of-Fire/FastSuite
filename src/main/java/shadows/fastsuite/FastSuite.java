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
		//	MinecraftForge.EVENT_BUS.addListener(this::test);
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

	/*
		public void test(FMLServerStartedEvent e) {
			RecipeManager mgr = e.getServer().getRecipeManager();
			CraftingInventory inv = new CraftingInventory(new Container(null, -1) {
				public boolean canInteractWith(PlayerEntity playerIn) {
					return false;
				}
			}, 2, 2);
			World world = e.getServer().getWorld(World.OVERWORLD);
			inv.setInventorySlotContents(0, new ItemStack(Items.ACACIA_LOG));
			CraftingInventory inv2 = new CraftingInventory(new Container(null, -1) {
				public boolean canInteractWith(PlayerEntity playerIn) {
					return false;
				}
			}, 2, 2);
			inv2.setInventorySlotContents(0, new ItemStack(Items.BIRCH_PLANKS));
			inv2.setInventorySlotContents(2, new ItemStack(Items.BIRCH_PLANKS));
	
			CraftingInventory inv3 = new CraftingInventory(new Container(null, -1) {
				public boolean canInteractWith(PlayerEntity playerIn) {
					return false;
				}
			}, 2, 2);
			for (int i = 0; i < 4; i++)
				inv3.setInventorySlotContents(i, new ItemStack(Items.OAK_PLANKS));
	
			CraftingInventory[] arr = { inv, inv2, inv3 };
			String[] names = { "acacia planks", "sticks", "crafting table" };
	
			long time, time2;
	
			for (int i = 0; i < 192; i++) {
				int j = i % 3;
				time = System.nanoTime();
				mgr.getRecipe(IRecipeType.CRAFTING, arr[j], world);
				time2 = System.nanoTime();
				LOG.info("Took {} ns to find the recipe for {} on attempt {}", time2 - time, names[j], i);
			}
		}
	*/
}
