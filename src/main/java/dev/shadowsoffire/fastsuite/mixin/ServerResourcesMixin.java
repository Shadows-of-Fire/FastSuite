package dev.shadowsoffire.fastsuite.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import dev.shadowsoffire.fastsuite.AuxRecipeManager;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.tags.TagManager;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.crafting.conditions.ConditionContext;

@Mixin(ReloadableServerResources.class)
public class ServerResourcesMixin {

	@Shadow
	private TagManager tagManager;

	@Shadow
	private final RecipeManager recipes = new AuxRecipeManager(new ConditionContext(this.tagManager));

}
