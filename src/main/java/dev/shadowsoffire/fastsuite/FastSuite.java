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

    public static Set<RecipeType<?>> indexedTypes = new HashSet<>();

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
        String[] types = cfg.getStringList("Indexed Recipe Types", "general", new String[] { "minecraft:crafting", "minecraft:smelting", "minecraft:blasting", "minecraft:smoking" },
            "A whitelist of recipe types that FastSuite will index and accelerate. Add a recipe type here (e.g. minecraft:smelting) to optimize its lookups.");
        for (String s : types) {
            try {
                RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.getValue(Identifier.parse(s));
                if (type != null) {
                    indexedTypes.add(type);
                }
                else {
                    LOGGER.error("Unknown recipe type {} in the Indexed Recipe Types config will be ignored.", s);
                }
            }
            catch (Exception ex) {
                LOGGER.error("Invalid recipe type name {} in the Indexed Recipe Types config will be ignored.", s);
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
