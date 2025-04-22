package dev.shadowsoffire.fastsuite.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.ShapelessRecipe;

@Mixin(value = CraftingInput.class, remap = false)
public abstract class CraftingInputMixin implements RecipeInput {

    @Shadow
    private List<ItemStack> items;

    @Shadow
    public abstract int width();

    @Shadow
    public abstract int height();

    /**
     * Override toString so we can get better debug information.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CraftingInput{");
        sb.append(this.width()).append("x").append(this.height());
        sb.append(": ");

        for (int i = 0; i < this.size(); i++) {
            sb.append(i).append("->").append(this.getItem(i)).append(";");
        }

        sb.append("}");

        return sb.toString();
    }

    /**
     * StackedContents is NOT thread-safe, so we have to force {@link CraftingInput} to
     * always return a new one since {@link ShapelessRecipe} accesses it during {@link ShapelessRecipe#matches}.
     * <p>
     * Failure to do so will result in random matching failures, which causes all kinds of random side effects.
     * https://github.com/Shadows-of-Fire/FastSuite/issues/44
     */
    @Overwrite
    public StackedContents stackedContents() {
        StackedContents contents = new StackedContents();
        for (ItemStack itemstack : items) {
            if (!itemstack.isEmpty()) {
                contents.accountStack(itemstack, 1);
            }
        }
        return contents;
    }

}
