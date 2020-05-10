package com.veteam.voluminousenergy.recipe;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

public class VERecipe  implements IRecipe<IInventory> {

    public static final IRecipeType<PrimitiveBlastFurnaceRecipe> recipeType = IRecipeType.register("primitive_blast_furnacing");
    public static final PrimitiveBlastFurnaceRecipe.Serializer serializer = new PrimitiveBlastFurnaceRecipe.Serializer();

    public Ingredient ingredient;
    public int ingredientCount;
    public ItemStack result;


    public Ingredient getIngredient() {
        return ingredient;
    }

    public int getIngredientCount() {
        return ingredientCount;
    }

    public ItemStack getResult() { return result; }

    @Override
    public boolean matches(IInventory inv, World worldIn){
        ItemStack stack = inv.getStackInSlot(0);
        int count = stack.getCount();
        return ingredient.test(stack) && count >= ingredientCount;
    }
    @Override
    public ItemStack getCraftingResult(IInventory inv){
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canFit(int width, int height){
        return true;
    }

    @Override
    public ItemStack getRecipeOutput(){
        return result;
    }

    @Override
    public ResourceLocation getId(){
        return null;
    }

    @Override
    public IRecipeSerializer<?> getSerializer(){
        return serializer;
    }

    @Override
    public IRecipeType<?> getType(){
        return recipeType;
    }

}
