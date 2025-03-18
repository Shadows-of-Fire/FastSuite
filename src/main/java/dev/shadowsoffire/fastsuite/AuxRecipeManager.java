package dev.shadowsoffire.fastsuite;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.google.common.base.Stopwatch;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.VisibleForTesting;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.registries.ForgeRegistries;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class AuxRecipeManager extends RecipeManager {

    @Deprecated
    public AuxRecipeManager() {
    }

    public AuxRecipeManager(ICondition.IContext context) {
        super(context);
    }

    @VisibleForTesting
    public <C extends Container, T extends Recipe<C>> Optional<T> super_getRecipeFor(RecipeType<T> type, C inv, Level level) {
        return super.getRecipeFor(type, inv, level);
    }

    private final Map<RecipeType<?>, CachedRecipeList<?, ?>> cachedRecipeListMap = new HashMap<>();

    private <C extends Container, T extends Recipe<C>> CachedRecipeList<C, T> getCachedRecipeList(RecipeType<T> type) {
        synchronized (cachedRecipeListMap) {
            CachedRecipeList<C, T> list = (CachedRecipeList<C, T>)cachedRecipeListMap.get(type);
            if(list == null) {
                list = new CachedRecipeList<>(type, this.byType(type));
                cachedRecipeListMap.put(type, list);
            }
            return list;
        }
    }

    @Override
    public <C extends Container, T extends Recipe<C>> Optional<T> getRecipeFor(RecipeType<T> type, C inv, Level level) {
        if (this.numRecipesOf(type) < FastSuite.MIN_SIZE_REQUIRED_FOR_THREADING || FastSuite.singleThreadedLookups.contains(type)) return super.getRecipeFor(type, inv, level);
        this.lockAllStacks(inv, true);
        try {
            var cachedRecipeList = getCachedRecipeList(type);
            return cachedRecipeList.getRecipeFor(inv, level);
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        finally {
            this.lockAllStacks(inv, false);
        }
    }

    @Override
    public <C extends Container, T extends Recipe<C>> List<T> getRecipesFor(RecipeType<T> type, C inv, Level level) {
        if (this.numRecipesOf(type) < FastSuite.MIN_SIZE_REQUIRED_FOR_THREADING || FastSuite.singleThreadedLookups.contains(type)) return super.getRecipesFor(type, inv, level);
        this.lockAllStacks(inv, true);
        try {
            var cachedRecipeList = getCachedRecipeList(type);
            return cachedRecipeList.getRecipesFor(inv, level);
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        finally {
            this.lockAllStacks(inv, false);
        }
    }

    private <C extends Container> void lockAllStacks(C inv, boolean locked) {
        if (!FastSuite.lockInputStacks) return;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) {
                ((ILockableItemStack) (Object) s).setLocked(locked);
            }
        }
    }

    private int numRecipesOf(RecipeType type) {
        return this.byType(type).size();
    }

    private <C extends Container, T extends Recipe<C>> Map<ResourceLocation, T> byType(RecipeType<T> pRecipeType) {
        return (Map<ResourceLocation, T>) this.recipes.getOrDefault(pRecipeType, Collections.emptyMap());
    }

    private static String timeoutMsg(RecipeType<?> type) {
        return String.format("Multithreaded recipe lookup took longer than %d seconds - aborting and returning nothing. Consider blacklisting this recipe type (%s) in the config.", FastSuite.maxRecipeLookupTime,
            ForgeRegistries.RECIPE_TYPES.getKey(type));
    }

    private static class CachedRecipeList<C extends Container, T extends Recipe<C>> {
        private final List<T> serialRecipes;
        private final List<T> parallelRecipes;
        private final RecipeType<T> type;

        public CachedRecipeList(RecipeType<T> type, Map<ResourceLocation, T> recipeMap) {
            this.type = type;
            this.serialRecipes = new ArrayList<>();
            this.parallelRecipes = new ArrayList<>();
            Stopwatch watch = Stopwatch.createStarted();
            for(Map.Entry<ResourceLocation, T> entry : recipeMap.entrySet()) {
                if(isParallelRecipe(entry.getValue()))
                    this.parallelRecipes.add(entry.getValue());
                else
                    this.serialRecipes.add(entry.getValue());
            }
            watch.stop();
            FastSuite.LOGGER.info("Constructed recipe list for {} in {}. {}/{} recipes are parallelized.",
                    ForgeRegistries.RECIPE_TYPES.getKey(type), watch, this.parallelRecipes.size(), recipeMap.size());
        }

        public Optional<T> getRecipeFor(C inv, Level level) {
            Predicate<T> recipeFilter = (recipe) -> {
                return recipe.matches(inv, level);
            };
            Optional<T> parRecipe = StreamUtils.executeUntil(() -> this.parallelRecipes.parallelStream().filter(recipeFilter).findFirst(), FastSuite.maxRecipeLookupTime, TimeUnit.SECONDS, Optional.empty(), () -> timeoutMsg(type));
            if(parRecipe.isPresent())
                return parRecipe;
            // check serial recipes
            for(T recipe : this.serialRecipes) {
                if(recipe.matches(inv, level))
                    return Optional.of(recipe);
            }
            return Optional.empty();
        }

        public List<T> getRecipesFor(C inv, Level level) {
            Comparator<T> recipeSorter = Comparator.comparing((recipe) -> {
                return recipe.getResultItem(level.registryAccess()).getDescriptionId();
            });
            Predicate<T> recipeFilter = (recipe) -> {
                return recipe.matches(inv, level);
            };
            List<T> parallelList = StreamUtils.executeUntil(() -> this.parallelRecipes.parallelStream().filter(recipeFilter).sorted(recipeSorter).collect(Collectors.toCollection(ArrayList::new)), FastSuite.maxRecipeLookupTime, TimeUnit.SECONDS, Collections.emptyList(), () -> timeoutMsg(type));
            parallelList.addAll(this.serialRecipes.stream().filter(recipeFilter).sorted(recipeSorter).toList());
            return parallelList;
        }

        private static final Map<Class<?>, Boolean> parallelRecipeClassCache = Collections.synchronizedMap(new IdentityHashMap<>());
        private static final Map<Class<?>, Boolean> ingredientClassCache = Collections.synchronizedMap(new IdentityHashMap<>());

        private static boolean isParallelRecipeClass(Class<?> clz) {
            // TODO: add modded whitelist
            return parallelRecipeClassCache.computeIfAbsent(clz, c -> c.getName().startsWith("net.minecraft.world.item.crafting."));
        }

        private static boolean isValidIngredient(Ingredient ingredient) {
            if(ingredient.isVanilla())
                return true;
            return ingredientClassCache.computeIfAbsent(ingredient.getClass(), clz -> {
                return clz.getName().startsWith("net.minecraftforge.common.crafting.");
            });
        }

        private boolean isParallelRecipe(T recipe) {
            if(!isParallelRecipeClass(recipe.getClass()))
                return false;
            // now check ingredients
            for(Ingredient ingredient : recipe.getIngredients()) {
                if(!isValidIngredient(ingredient))
                    return false;
            }
            return true;
        }
    }
}
