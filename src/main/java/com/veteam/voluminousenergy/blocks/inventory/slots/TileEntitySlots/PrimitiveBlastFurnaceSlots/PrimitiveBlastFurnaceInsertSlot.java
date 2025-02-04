package com.veteam.voluminousenergy.blocks.inventory.slots.TileEntitySlots.PrimitiveBlastFurnaceSlots;

import com.veteam.voluminousenergy.blocks.inventory.slots.VEInsertSlot;
import com.veteam.voluminousenergy.recipe.PrimitiveBlastFurnaceRecipe;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

public class PrimitiveBlastFurnaceInsertSlot extends VEInsertSlot
{
    World world;
    public PrimitiveBlastFurnaceInsertSlot(IItemHandler itemHandler, int index, int xPos, int yPos, World world){
        super(itemHandler,index,xPos,yPos);
        this.world = world;
    }

    @Override
    public boolean mayPlace(ItemStack stack){
        ItemStack referenceStack = stack.copy();
        referenceStack.setCount(64);
        PrimitiveBlastFurnaceRecipe recipe = world.getRecipeManager().getRecipeFor(PrimitiveBlastFurnaceRecipe.RECIPE_TYPE, new Inventory(referenceStack), world).orElse(null);
        return checkRecipe(recipe,referenceStack);
    }
}
