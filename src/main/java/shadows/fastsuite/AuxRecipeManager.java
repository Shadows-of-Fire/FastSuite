package shadows.fastsuite;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.jetbrains.annotations.VisibleForTesting;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
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
		super();
	}

	public AuxRecipeManager(ICondition.IContext context) {
		super(context);
	}

	@VisibleForTesting
	public <C extends Container, T extends Recipe<C>> Optional<T> super_getRecipeFor(RecipeType<T> type, C inv, Level level) {
		return super.getRecipeFor(type, inv, level);
	}

	@Override
	public <C extends Container, T extends Recipe<C>> Optional<T> getRecipeFor(RecipeType<T> type, C inv, Level level) {
		if (numRecipesOf(type) < FastSuite.MIN_SIZE_REQUIRED_FOR_THREADING || FastSuite.singleThreadedLookups.contains(type)) return super.getRecipeFor(type, inv, level);
		return StreamUtils.executeUntil(() -> {
			return this.byType(type).values().parallelStream().filter(recipe -> {
				return recipe.matches(inv, level);
			}).findFirst();
		}, FastSuite.maxRecipeLookupTime, TimeUnit.SECONDS, Optional.empty(), () -> timeoutMsg(type));
	}

	@Override
	public <C extends Container, T extends Recipe<C>> List<T> getRecipesFor(RecipeType<T> type, C inv, Level level) {
		if (numRecipesOf(type) < FastSuite.MIN_SIZE_REQUIRED_FOR_THREADING || FastSuite.singleThreadedLookups.contains(type)) return super.getRecipesFor(type, inv, level);
		return StreamUtils.executeUntil(() -> {
			return this.byType(type).values().parallelStream().filter((recipe) -> {
				return recipe.matches(inv, level);
			}).sorted(Comparator.comparing((recipe) -> {
				return recipe.getResultItem().getDescriptionId();
			})).collect(Collectors.toList());
		}, FastSuite.maxRecipeLookupTime, TimeUnit.SECONDS, Collections.emptyList(), () -> timeoutMsg(type));
	};

	private int numRecipesOf(RecipeType type) {
		return this.byType(type).size();
	}

	private <C extends Container, T extends Recipe<C>> Map<ResourceLocation, T> byType(RecipeType<T> pRecipeType) {
		return (Map<ResourceLocation, T>) this.recipes.getOrDefault(pRecipeType, Collections.emptyMap());
	}

	private static String timeoutMsg(RecipeType<?> type) {
		return String.format("Multithreaded recipe lookup took longer than %d seconds - aborting and returning nothing. Consider blacklisting this recipe type (%s) in the config.", FastSuite.maxRecipeLookupTime, ForgeRegistries.RECIPE_TYPES.getKey(type));
	}
}
