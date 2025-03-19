package dev.shadowsoffire.fastsuite;

import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import dev.shadowsoffire.placebo.config.Configuration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@Mod(FastSuite.MODID)
public class FastSuite {

    public static final String MODID = "fastsuite";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    public static boolean DEBUG = false;
    public static final int MIN_SIZE_REQUIRED_FOR_THREADING = 100;

    public static int maxRecipeLookupTime = 25;
    public static Set<RecipeType<?>> singleThreadedLookups = new HashSet<>();
    public static boolean lockInputStacks = false;
    public static boolean unsafeMode = false;

    public FastSuite(IEventBus bus) {
        StreamUtils.setup(this);
        bus.register(this);
        if (DEBUG) {
            NeoForge.EVENT_BUS.addListener(this::test);
        }
    }

    @SubscribeEvent
    public void setup(FMLCommonSetupEvent e) {
        Configuration cfg = new Configuration(MODID);
        cfg.setTitle("FastSuite Configuration");
        String[] stLookups = cfg.getStringList("Single Threaded Recipe Types", "general", new String[0],
            "A list of recipe types which may only be looked up on the main thread. Add a recipe type to this list if errors start happening.");
        for (String s : stLookups) {
            try {
                singleThreadedLookups.add(BuiltInRegistries.RECIPE_TYPE.get(ResourceLocation.parse(s)));
            }
            catch (Exception ex) {
                LOGGER.error("Invalid single threaded recipe type name {} will be ignored.", s);
            }
        }

        maxRecipeLookupTime = cfg.getInt("Max Recipe Lookup Time", "general", maxRecipeLookupTime, 1, 300, "The max time, in seconds, that a recipe lookup may take before aborting the lookup and logging an error.");
        lockInputStacks = cfg.getBoolean("Lock Crafting Input Stacks", "general", false, "If true, the stacks used as recipe inputs will be locked and throw an error if modified during parallel matching. Useful for debugging.");
        unsafeMode = cfg.getBoolean("Unsafe Mode", "general", false, "If true, FastSuite parallelize all recipes without validation. This can cause crashes if a recipe is not thread safe.");

        if (cfg.hasChanged()) cfg.save();
    }

    public static void registerSafeRecipeClass(Class<?> clazz) {
        CachedRecipeList.parallelRecipeClassCache.put(clazz, true);
    }

    public static void registerSafeIngredientClass(Class<?> clazz) {
        CachedRecipeList.ingredientClassCache.put(clazz, true);
    }

    private static class TestMenu extends AbstractContainerMenu {

        protected TestMenu() {
            super(null, -1);
        }

        @Override
        public ItemStack quickMoveStack(Player pPlayer, int pIndex) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player pPlayer) {
            return true;
        }

    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void test(ServerStartedEvent e) {
        LOGGER.info("FastSuite Debug Recipe Counts:");
        for (RecipeType type : BuiltInRegistries.RECIPE_TYPE) {
            LOGGER.info("{}: {}", BuiltInRegistries.RECIPE_TYPE.getKey(type), e.getServer().getRecipeManager().getAllRecipesFor(type).size());
        }

        LOGGER.info("Initiating FastSuite Tests...");
        AuxRecipeManager mgr = (AuxRecipeManager) e.getServer().getRecipeManager();
        CraftingContainer inv = new TransientCraftingContainer(new TestMenu(), 2, 2);
        Level world = e.getServer().getLevel(Level.OVERWORLD);
        inv.setItem(0, new ItemStack(Items.ACACIA_LOG));

        CraftingContainer inv2 = new TransientCraftingContainer(new TestMenu(), 2, 2);
        inv2.setItem(0, new ItemStack(Items.BIRCH_PLANKS));
        inv2.setItem(2, new ItemStack(Items.BIRCH_PLANKS));

        CraftingContainer inv3 = new TransientCraftingContainer(new TestMenu(), 2, 2);
        for (int i = 0; i < 4; i++)
            inv3.setItem(i, new ItemStack(Items.OAK_PLANKS));

        CraftingContainer inv4 = new TransientCraftingContainer(new TestMenu(), 2, 2);
        inv4.setItem(0, new ItemStack(Items.SHULKER_BOX));
        inv4.setItem(3, new ItemStack(Items.BLACK_DYE));

        CraftingContainer inv5 = new TransientCraftingContainer(new TestMenu(), 3, 3);
        inv5.setItem(0, new ItemStack(Items.STICK));
        inv5.setItem(1, new ItemStack(Items.STICKY_PISTON));
        inv5.setItem(2, new ItemStack(Items.ACACIA_FENCE));
        inv5.setItem(3, new ItemStack(Items.ACACIA_LEAVES));
        inv5.setItem(4, new ItemStack(Items.APPLE));
        inv5.setItem(5, new ItemStack(Items.BEEHIVE));
        inv5.setItem(6, new ItemStack(Items.BEE_NEST));
        inv5.setItem(7, new ItemStack(Items.BLACK_DYE));
        inv5.setItem(8, new ItemStack(Items.SHULKER_BOX));

        CraftingContainer[] arr = { inv, inv2, inv3, inv4, inv5 };
        String[] names = { "acacia planks", "sticks", "crafting table", "black shulker box", "failed match" };

        for (int testCase = 0; testCase < names.length; testCase++) {
            this.testMulti(mgr, world, arr[testCase].asCraftInput(), names[testCase]);
            this.testSingle(mgr, world, arr[testCase].asCraftInput(), names[testCase]);
        }
    }

    private void testMulti(AuxRecipeManager mgr, Level level, CraftingInput input, String recipeName) {
        long time, time2;
        long deltaSum = 0;
        int iterations = 10000;
        for (int i = 0; i < iterations; i++) {
            time = System.nanoTime();
            mgr.getRecipeFor(RecipeType.CRAFTING, input, level);
            time2 = System.nanoTime();
            deltaSum += time2 - time;
        }
        LOGGER.info("[Multithreaded Test] - Took an average of {} ns to find the recipe for {}", deltaSum / (float) iterations, recipeName);
    }

    private void testSingle(AuxRecipeManager mgr, Level level, CraftingInput input, String recipeName) {
        long time, time2;
        long deltaSum = 0;
        int iterations = 10000;
        for (int i = 0; i < iterations; i++) {
            time = System.nanoTime();
            mgr.super_getRecipeFor(RecipeType.CRAFTING, input, level);
            time2 = System.nanoTime();
            deltaSum += time2 - time;
        }
        LOGGER.info("[Singlethreaded Test] - Took an average of {} ns to find the recipe for {}", deltaSum / (float) iterations, recipeName);
    }

}
