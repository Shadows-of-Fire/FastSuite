package dev.shadowsoffire.fastsuite;

import java.util.HashSet;

import org.apache.logging.log4j.Logger;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Debug/benchmark harness for the crafting recipe index. Registered as a {@link ServerStartedEvent} listener only when
 * {@link FastSuite#ENABLE_SERVER_START_TESTS} is enabled.
 * It verifies the index agrees with vanilla on every lookup, then benchmarks the match-all and short-circuit paths against a full vanilla scan.
 */
public class FastSuiteTests {

    private static final Logger LOGGER = FastSuite.LOGGER;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void test(ServerStartedEvent e) {
        RecipeManager mgr = e.getServer().getRecipeManager();
        Level world = e.getServer().getLevel(Level.OVERWORLD);

        LOGGER.info("FastSuite Debug Recipe Counts:");
        for (RecipeType type : BuiltInRegistries.RECIPE_TYPE) {
            LOGGER.info("{}: {}", BuiltInRegistries.RECIPE_TYPE.getKey(type), mgr.recipeMap().byType(type).size());
        }

        CraftingContainer inv = new TransientCraftingContainer(new TestMenu(), 2, 2);
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

        CraftingInput[] inputs = { inv.asCraftInput(), inv2.asCraftInput(), inv3.asCraftInput(), inv4.asCraftInput(), inv5.asCraftInput() };
        String[] names = { "acacia planks", "sticks", "crafting table", "black shulker box", "failed match" };

        // Correctness gate: the index must return exactly the same match set as a full vanilla scan, for every input.
        LOGGER.info("Verifying crafting index against vanilla...");
        boolean ok = true;
        for (int i = 0; i < names.length; i++) {
            ok &= verify(mgr, world, inputs[i], names[i]);
        }
        RandomSource rng = RandomSource.create(0xF457_5009L);
        int trials = 5000;
        int mismatches = 0;
        for (int n = 0; n < trials; n++) {
            if (!verify(mgr, world, randomInput(rng), "random#" + n)) {
                mismatches++;
            }
        }
        LOGGER.info("Crafting index correctness: {} ({}/{} random inputs matched vanilla).", ok && mismatches == 0 ? "PASS" : "FAIL", trials - mismatches, trials);

        // Timing: match-all (the workload Polymorph forces on every grid change) and short-circuit findFirst (the singular lookup non-Polymorph packs use).
        for (int i = 0; i < names.length; i++) {
            timeIndexed(mgr, world, inputs[i], names[i]);
            timeVanilla(mgr, world, inputs[i], names[i]);
            timeIndexedFirst(mgr, world, inputs[i], names[i]);
            timeVanillaFirst(mgr, world, inputs[i], names[i]);
        }
    }

    /** Asserts the index agrees with a full vanilla scan on both the match-all set and the short-circuit first match (the latter also proves priority order). */
    private static boolean verify(RecipeManager mgr, Level level, CraftingInput input, String name) {
        RecipeMap map = mgr.recipeMap();
        TestableRecipeMap test = (TestableRecipeMap) map;

        var indexedAll = new HashSet<>(map.getRecipesFor(RecipeType.CRAFTING, input, level).toList());
        var vanillaAll = new HashSet<>(test.super_getRecipesFor(RecipeType.CRAFTING, input, level).toList());

        var indexedFirst = map.getRecipesFor(RecipeType.CRAFTING, input, level).findFirst();
        var vanillaFirst = test.super_getRecipesFor(RecipeType.CRAFTING, input, level).findFirst();

        if (!indexedAll.equals(vanillaAll) || !indexedFirst.equals(vanillaFirst)) {
            LOGGER.error("Index MISMATCH for {}: setEqual={}, firstEqual={}, indexedFirst={}, vanillaFirst={}", name, indexedAll.equals(vanillaAll), indexedFirst.equals(vanillaFirst), indexedFirst, vanillaFirst);
            return false;
        }
        return true;
    }

    private static CraftingInput randomInput(RandomSource rng) {
        CraftingContainer c = new TransientCraftingContainer(new TestMenu(), 3, 3);
        for (int i = 0; i < 9; i++) {
            if (rng.nextBoolean()) {
                Item item = BuiltInRegistries.ITEM.byId(rng.nextInt(BuiltInRegistries.ITEM.size()));
                if (item != null && item != Items.AIR) {
                    c.setItem(i, new ItemStack(item));
                }
            }
        }
        return c.asCraftInput();
    }

    private static void timeIndexed(RecipeManager mgr, Level level, CraftingInput input, String recipeName) {
        RecipeMap map = mgr.recipeMap();
        long deltaSum = 0;
        int iterations = 10000;
        for (int i = 0; i < iterations; i++) {
            long time = System.nanoTime();
            map.getRecipesFor(RecipeType.CRAFTING, input, level).count();
            deltaSum += System.nanoTime() - time;
        }
        LOGGER.info("[Indexed] - Took an average of {} ns to match all recipes for {}", deltaSum / (float) iterations, recipeName);
    }

    private static void timeVanilla(RecipeManager mgr, Level level, CraftingInput input, String recipeName) {
        TestableRecipeMap map = (TestableRecipeMap) mgr.recipeMap();
        long deltaSum = 0;
        int iterations = 10000;
        for (int i = 0; i < iterations; i++) {
            long time = System.nanoTime();
            map.super_getRecipesFor(RecipeType.CRAFTING, input, level).count();
            deltaSum += System.nanoTime() - time;
        }
        LOGGER.info("[Vanilla] - Took an average of {} ns to match all recipes for {}", deltaSum / (float) iterations, recipeName);
    }

    private static void timeIndexedFirst(RecipeManager mgr, Level level, CraftingInput input, String recipeName) {
        RecipeMap map = mgr.recipeMap();
        long deltaSum = 0;
        int iterations = 10000;
        for (int i = 0; i < iterations; i++) {
            long time = System.nanoTime();
            map.getRecipesFor(RecipeType.CRAFTING, input, level).findFirst();
            deltaSum += System.nanoTime() - time;
        }
        LOGGER.info("[Indexed findFirst] - Took an average of {} ns to find the first recipe for {}", deltaSum / (float) iterations, recipeName);
    }

    private static void timeVanillaFirst(RecipeManager mgr, Level level, CraftingInput input, String recipeName) {
        TestableRecipeMap map = (TestableRecipeMap) mgr.recipeMap();
        long deltaSum = 0;
        int iterations = 10000;
        for (int i = 0; i < iterations; i++) {
            long time = System.nanoTime();
            map.super_getRecipesFor(RecipeType.CRAFTING, input, level).findFirst();
            deltaSum += System.nanoTime() - time;
        }
        LOGGER.info("[Vanilla findFirst] - Took an average of {} ns to find the first recipe for {}", deltaSum / (float) iterations, recipeName);
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

}
