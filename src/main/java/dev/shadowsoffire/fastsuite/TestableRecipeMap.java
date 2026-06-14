package dev.shadowsoffire.fastsuite;

import java.util.stream.Stream;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public interface TestableRecipeMap {

    /**
     * Copy of the vanilla code path for testing purposes.
     */
    default <I extends RecipeInput, T extends Recipe<I>> Stream<RecipeHolder<T>> super_getRecipesFor(RecipeType<T> type, I container, Level level) {
        return container.isEmpty() ? Stream.empty() : ((RecipeMap) this).byType(type).stream().filter(r -> r.value().matches(container, level));
    }

}
