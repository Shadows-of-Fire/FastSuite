package shadows.fastsuite;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonElement;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class AuxRecipeManager extends RecipeManager {

	public boolean active = false;

	/**
	 * Master Map of recipes.  Recipe lists are stored as LinkedLists, which have a faster insertion at head and removal speed.
	 */
	private final Map<RecipeType<?>, LinkedRecipeList<?>> linkedRecipes = new HashMap<>();

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> objectIn, ResourceManager resourceManagerIn, ProfilerFiller profilerIn) {
		this.active = false;
		super.apply(objectIn, resourceManagerIn, profilerIn);
	};

	public void processInitialRecipes(Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipes) {
		this.linkedRecipes.clear();
		long recipeCount = 0;
		for (Map.Entry<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> e : recipes.entrySet()) {
			LinkedRecipeList<?> list = new LinkedRecipeList<>((Collection) e.getValue().values());
			this.linkedRecipes.put(e.getKey(), list);
			recipeCount += e.getValue().size();
		}
		FastSuite.LOG.info("Successfully processed {} recipes into the AuxRecipeManager.", recipeCount);
		this.active = true;
	}

	@Override
	public <C extends Container, T extends Recipe<C>> Optional<T> getRecipeFor(RecipeType<T> type, C inv, Level world) {
		if (!this.active) return super.getRecipeFor(type, inv, world);
		LinkedRecipeList<C> list = this.getRecipes(type);
		T recipe = (T) list.findFirstMatch(inv, world);
		return Optional.ofNullable(recipe);
	}

	private <C extends Container, T extends Recipe<C>> LinkedRecipeList<C> getRecipes(RecipeType<T> type) {
		return (LinkedRecipeList) this.linkedRecipes.getOrDefault(type, LinkedRecipeList.EMPTY);
	}

	@Override
	public <C extends Container, T extends Recipe<C>> NonNullList<ItemStack> getRemainingItemsFor(RecipeType<T> type, C inv, Level world) {
		if (!this.active) return super.getRemainingItemsFor(type, inv, world);
		LinkedRecipeList<C> list = this.getRecipes(type);
		T recipe = (T) list.findFirstMatch(inv, world);
		if (recipe != null) {
			return recipe.getRemainingItems(inv);
		} else {
			NonNullList<ItemStack> nonnulllist = NonNullList.withSize(inv.getContainerSize(), ItemStack.EMPTY);

			for (int i = 0; i < nonnulllist.size(); ++i) {
				nonnulllist.set(i, inv.getItem(i));
			}

			return nonnulllist;
		}
	}

	@Override
	public void replaceRecipes(Iterable<Recipe<?>> recipes) {
		super.replaceRecipes(recipes);
		this.processInitialRecipes(super.recipes);
	};

	public void dump() {
		for (Map.Entry<RecipeType<?>, LinkedRecipeList<?>> e : this.linkedRecipes.entrySet()) {
			FastSuite.LOG.info("Recipes for type {}:", e.getKey().toString());
			LinkedRecipeList<?> list = e.getValue();
			RecipeNode<?> temp = list.head;
			while (temp != null) {
				FastSuite.LOG.info("{}", temp.r.getId());
				temp = temp.next;
			}
		}
	}

	public static class LinkedRecipeList<I extends Container> {

		public static final LinkedRecipeList<Container> EMPTY = new LinkedRecipeList<>(Collections.emptyList());

		RecipeNode<I> head;
		RecipeNode<I> tail;

		public LinkedRecipeList(Collection<Recipe<I>> recipes) {
			for (Recipe<I> r : recipes) {
				if (r != null) this.add(new RecipeNode<>(r));
			}
		}

		void add(RecipeNode<I> node) {
			if (this.head == null) this.tail = this.head = node;
			else {
				this.tail.next = node;
				node.prev = this.tail;
				this.tail = node;
			}
		}

		void addToHead(RecipeNode<I> node) {
			if (this.head == null) this.tail = this.head = node;
			else {
				node.next = this.head;
				this.head.prev = node;
				this.head = node;
			}
		}

		void remove(RecipeNode<I> node) {
			if (node == this.head && node == this.tail) {
				this.head = this.tail = null;
			} else if (node == this.head) {
				this.head = this.head.next;
				if (this.head != null) this.head.prev = null;
			} else if (node == this.tail) {
				this.tail = this.tail.prev;
				if (this.tail != null) this.tail.next = null;
			} else {
				node.prev.next = node.next;
				node.next.prev = node.prev;
			}
			node.next = node.prev = null;
		}

		Recipe<I> findFirstMatch(I inv, Level world) {
			synchronized (this) {
				RecipeNode<I> temp = this.head;
				int idx = 0;
				while (temp != null) {
					if (temp.matches(inv, world)) {
						if (idx > FastSuite.cacheSize) {
							this.remove(temp);
							this.addToHead(temp);
						}
						return temp.r;
					}
					temp = temp.next;
					idx++;
				}
				return null;
			}
		}
	}

	public static class RecipeNode<I extends Container> {
		final Recipe<I> r;
		RecipeNode<I> next;
		RecipeNode<I> prev;

		public RecipeNode(Recipe<I> r) {
			this.r = r;
		}

		@Override
		public String toString() {
			return String.format("RecipeNode(%s)", this.r.getId());
		}

		boolean matches(I inv, Level world) {
			return this.r.matches(inv, world);
		}
	}
}
