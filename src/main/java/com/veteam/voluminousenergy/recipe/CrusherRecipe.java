package com.veteam.voluminousenergy.recipe;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import com.veteam.voluminousenergy.blocks.blocks.VEBlocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistryEntry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class CrusherRecipe extends VERecipe {

    public static final IRecipeType<CrusherRecipe> RECIPE_TYPE = VERecipes.VERecipeTypes.CRUSHING;

    public static final Serializer SERIALIZER = new Serializer();

    public final ResourceLocation recipeId;
    public Ingredient ingredient;
    public int ingredientCount;
    public ItemStack result;
    public ItemStack rngResult;
    private int processTime;
    private int outputAmount;
    private int outputRngAmount;
    private float chance;

    private final Map<Ingredient, Integer> ingredients = new LinkedHashMap<>();


    public CrusherRecipe(ResourceLocation recipeId){
        this.recipeId = recipeId;
    }

    public Ingredient getIngredient(){ return ingredient;}

    public int getIngredientCount(){ return ingredientCount;}

    public ItemStack getResult() {return result;}

    public ItemStack getRngItem(){return rngResult;}

    public float getChance(){return chance;}

    @Override
    public boolean matches(IInventory inv, World worldIn){
        ItemStack stack = inv.getItem(0);
        int count = stack.getCount();
        return ingredient.test(stack) && count >= ingredientCount;
    }

    @Override
    public ItemStack assemble(IInventory inv){return ItemStack.EMPTY;}

    @Override
    public boolean canCraftInDimensions(int width, int height){return true;}

    @Override
    public ItemStack getResultItem(){return result;}

    @Override
    public ResourceLocation getId(){return recipeId;}

    @Override
    public IRecipeSerializer<?> getSerializer(){ return SERIALIZER;}

    @Override
    public IRecipeType<?> getType(){return RECIPE_TYPE;}

    public int getOutputAmount() {return outputAmount;}

    public int getOutputRngAmount(){return outputRngAmount;}

    public int getProcessTime() { return processTime; }

    public Map<Ingredient, Integer> getIngredientMap() {
        return ImmutableMap.copyOf(ingredients);
    }

    @Override
    public ItemStack getToastSymbol(){
        return new ItemStack(VEBlocks.CRUSHER_BLOCK);
    }

    public static class Serializer extends ForgeRegistryEntry<IRecipeSerializer<?>> implements IRecipeSerializer<CrusherRecipe>{

        public static ArrayList<Item> ingredientList = new ArrayList<>();

        @Override
        public CrusherRecipe fromJson(ResourceLocation recipeId, JsonObject json){
            CrusherRecipe recipe = new CrusherRecipe(recipeId);

            recipe.ingredient = Ingredient.fromJson(json.get("ingredient"));
            recipe.ingredientCount = JSONUtils.getAsInt(json.get("ingredient").getAsJsonObject(), "count", 1);
            recipe.processTime = JSONUtils.getAsInt(json,"process_time",200);

            for (ItemStack stack : recipe.ingredient.getItems()){
                if(!ingredientList.contains(stack.getItem())){
                    ingredientList.add(stack.getItem());
                }
            }

            ResourceLocation itemResourceLocation = ResourceLocation.of(JSONUtils.getAsString(json.get("result").getAsJsonObject(),"item","minecraft:air"),':');
            int itemAmount = JSONUtils.getAsInt(json.get("result").getAsJsonObject(),"count",1);
            recipe.result = new ItemStack(ForgeRegistries.ITEMS.getValue(itemResourceLocation));
            recipe.outputAmount = itemAmount;

            ResourceLocation rngResourceLocation = ResourceLocation.of(JSONUtils.getAsString(json.get("rng").getAsJsonObject(),"item","minecraft:air"),':');
            int rngAmount = JSONUtils.getAsInt(json.get("rng").getAsJsonObject(),"count",0);
            float rngChance = JSONUtils.getAsFloat(json.get("rng").getAsJsonObject(),"chance",0); //Enter % as DECIMAL. Ie 50% = 0.5

            recipe.rngResult = new ItemStack(ForgeRegistries.ITEMS.getValue(rngResourceLocation));
            recipe.outputRngAmount = rngAmount;
            recipe.chance = rngChance;

            return recipe;
        }

        @Nullable
        @Override
        public CrusherRecipe fromNetwork(ResourceLocation recipeId, PacketBuffer buffer){
            CrusherRecipe recipe = new CrusherRecipe((recipeId));
            recipe.ingredient = Ingredient.fromNetwork(buffer);
            recipe.ingredientCount = buffer.readByte();
            recipe.result = buffer.readItem();
            recipe.processTime = buffer.readInt();
            recipe.outputAmount = buffer.readInt();
            recipe.rngResult = buffer.readItem();
            recipe.outputRngAmount = buffer.readInt();
            recipe.chance = buffer.readFloat();
            return recipe;
        }

        @Override
        public void toNetwork(PacketBuffer buffer, CrusherRecipe recipe){
            recipe.ingredient.toNetwork(buffer);
            buffer.writeByte(recipe.getIngredientCount());
            buffer.writeItem(recipe.getResult());
            buffer.writeInt(recipe.processTime);
            buffer.writeInt(recipe.outputAmount);
            buffer.writeItem(recipe.rngResult);
            buffer.writeInt(recipe.outputRngAmount);
            buffer.writeFloat(recipe.chance);
        }
    }

}
