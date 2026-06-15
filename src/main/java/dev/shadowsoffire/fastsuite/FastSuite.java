package dev.shadowsoffire.fastsuite;

import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import dev.shadowsoffire.placebo.config.Configuration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(FastSuite.MODID)
public class FastSuite {

    public static final String MODID = "fastsuite";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    public static boolean ENABLE_SERVER_START_TESTS = false;
    public static final boolean DEBUG_MATCHING = "on".equalsIgnoreCase(System.getenv("FASTSUITE_DEBUG_MATCHING"));

    public static Set<RecipeType<?>> singleThreadedLookups = new HashSet<>();

    public FastSuite(IEventBus bus) {
        bus.register(this);
        if (ENABLE_SERVER_START_TESTS) {
            NeoForge.EVENT_BUS.addListener(FastSuiteTests::test);
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
                singleThreadedLookups.add(BuiltInRegistries.RECIPE_TYPE.getValue(Identifier.parse(s)));
            }
            catch (Exception ex) {
                LOGGER.error("Invalid single threaded recipe type name {} will be ignored.", s);
            }
        }

        if (cfg.hasChanged()) cfg.save();
    }

    public static void registerSafeRecipeClass(Class<?> clazz) {
        CachedRecipeList.parallelRecipeClassCache.put(clazz, true);
    }

    public static void registerSafeIngredientClass(Class<?> clazz) {
        CachedRecipeList.ingredientClassCache.put(clazz, true);
    }

}
