package com.veteam.voluminousenergy.blocks.inventory.slots.TileEntitySlots;

import com.veteam.voluminousenergy.blocks.inventory.slots.VEInsertSlot;
import com.veteam.voluminousenergy.recipe.CrusherRecipe;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

public class CrusherInputSlot extends VEInsertSlot {
    public World world;
    public CrusherInputSlot(IItemHandler itemHandler, int index, int xPos, int yPos, World world){
        super(itemHandler,index,xPos,yPos);
        this.world = world;
    }

    @Override
    public boolean mayPlace(ItemStack stack){
        ItemStack referenceStack = stack.copy();
        referenceStack.setCount(64);
        CrusherRecipe recipe = world.getRecipeManager().getRecipeFor(CrusherRecipe.RECIPE_TYPE, new Inventory(referenceStack), world).orElse(null);
        return checkRecipe(recipe,referenceStack);
    }
}
