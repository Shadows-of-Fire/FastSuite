package shadows.fastsuite;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonElement;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.item.crafting.RecipeManager;
import net.minecraft.profiler.IProfiler;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class AuxRecipeManager extends RecipeManager {

	public boolean active = false;

	/**
	 * Master Map of recipes.  Recipe lists are stored as LinkedLists, which have a faster insertion at head and removal speed.
	 */
	private final Map<IRecipeType<?>, LinkedRecipeList<?>> linkedRecipes = new HashMap<>();

	protected void apply(Map<ResourceLocation, JsonElement> objectIn, IResourceManager resourceManagerIn, IProfiler profilerIn) {
		active = false;
		super.apply(objectIn, resourceManagerIn, profilerIn);
	};

	public void processInitialRecipes(Map<IRecipeType<?>, Map<ResourceLocation, IRecipe<?>>> recipes) {
		this.linkedRecipes.clear();
		for (Map.Entry<IRecipeType<?>, Map<ResourceLocation, IRecipe<?>>> e : recipes.entrySet()) {
			LinkedRecipeList<?> list = new LinkedRecipeList<>((Collection) e.getValue().values());
			this.linkedRecipes.put(e.getKey(), list);
		}
		active = true;
	}

	@Override
	public <C extends IInventory, T extends IRecipe<C>> Optional<T> getRecipe(IRecipeType<T> type, C inv, World world) {
		if (!active) return super.getRecipe(type, inv, world);
		LinkedRecipeList<C> list = getRecipes(type);
		T recipe = (T) list.findFirstMatch(inv, world);
		return Optional.ofNullable(recipe);
	}

	@Override
	public <C extends IInventory, T extends IRecipe<C>> List<T> getRecipes(IRecipeType<T> type, C inv, World world) {
		if (!active) return super.getRecipes(type, inv, world);
		LinkedRecipeList<C> list = getRecipes(type);
		List<T> recipes = (List) list.findAllMatches(inv, world);
		recipes.sort(Comparator.comparing(r -> r.getRecipeOutput().getTranslationKey()));
		return recipes;
	}

	private <C extends IInventory, T extends IRecipe<C>> LinkedRecipeList<C> getRecipes(IRecipeType<T> type) {
		return (LinkedRecipeList) linkedRecipes.getOrDefault(type, LinkedRecipeList.EMPTY);
	}

	@Override
	public <C extends IInventory, T extends IRecipe<C>> NonNullList<ItemStack> getRecipeNonNull(IRecipeType<T> type, C inv, World world) {
		if (!active) return super.getRecipeNonNull(type, inv, world);
		LinkedRecipeList<C> list = getRecipes(type);
		T recipe = (T) list.findFirstMatch(inv, world);
		if (recipe != null) {
			return recipe.getRemainingItems(inv);
		} else {
			NonNullList<ItemStack> nonnulllist = NonNullList.withSize(inv.getSizeInventory(), ItemStack.EMPTY);

			for (int i = 0; i < nonnulllist.size(); ++i) {
				nonnulllist.set(i, inv.getStackInSlot(i));
			}

			return nonnulllist;
		}
	}

	public void deserializeRecipes(Iterable<IRecipe<?>> recipes) {
		super.deserializeRecipes(recipes);
		processInitialRecipes(super.recipes);
	};

	public static class LinkedRecipeList<I extends IInventory> {

		public static final LinkedRecipeList<IInventory> EMPTY = new LinkedRecipeList<>(Collections.emptyList());

		RecipeNode<I> head;
		RecipeNode<I> tail;

		public LinkedRecipeList(Collection<IRecipe<I>> recipes) {
			for (IRecipe<I> r : recipes) {
				add(new RecipeNode<>(r));
			}
		}

		void add(RecipeNode<I> node) {
			if (head == null) tail = head = node;
			else {
				tail.next = node;
				node.prev = tail;
				tail = node;
			}
		}

		void addToHead(RecipeNode<I> node) {
			if (head == null) tail = head = node;
			else {
				node.next = head;
				head.prev = node;
				head = node;
			}
		}

		void remove(RecipeNode<I> node) {
			if (node == head) {
				head = head.next;
			}
			if (node == tail) {
				tail = tail.prev;
			} else {
				node.prev.next = node.next;
				node.next.prev = node.prev;
			}
			node.next = node.prev = null;
		}

		IRecipe<I> findFirstMatch(I inv, World world) {
			RecipeNode<I> temp = head;
			while (temp != null) {
				if (temp.r.matches(inv, world)) {
					if (temp != head) {
						remove(temp);
						addToHead(temp);
					}
					return temp.r;
				}
				temp = temp.next;
			}
			return null;
		}

		List<IRecipe<I>> findAllMatches(I inv, World world) {
			RecipeNode<I> temp = head;
			List<IRecipe<I>> ret = null;
			while (temp != null) {
				if (temp.r.matches(inv, world)) {
					RecipeNode<I> next = temp.next;
					if (temp != head) {
						remove(temp);
						addToHead(temp);
					}
					if (ret == null) ret = new ArrayList<>();
					ret.add(temp.r);
					temp = next;
				} else temp = temp.next;
			}
			return ret == null ? Collections.emptyList() : ret;
		}

	}

	public static class RecipeNode<I extends IInventory> {
		final IRecipe<I> r;
		RecipeNode<I> next;
		RecipeNode<I> prev;

		public RecipeNode(IRecipe<I> r) {
			this.r = r;
		}

		@Override
		public String toString() {
			return String.format("RecipeNode(%s)", this.r.getId());
		}
	}
}
