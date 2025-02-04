package com.veteam.voluminousenergy.recipe.CombustionGenerator;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.veteam.voluminousenergy.VoluminousEnergy;
import com.veteam.voluminousenergy.recipe.VERecipe;
import com.veteam.voluminousenergy.recipe.VERecipes;
import net.minecraft.fluid.Fluid;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tags.ITag;
import net.minecraft.tags.TagCollectionManager;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistryEntry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class CombustionGeneratorOxidizerRecipe extends VERecipe {
    public static final IRecipeType<CombustionGeneratorOxidizerRecipe> RECIPE_TYPE = VERecipes.VERecipeTypes.OXIDIZING;

    public static final Serializer SERIALIZER = new Serializer();

    public static ArrayList<Item> ingredientList = new ArrayList<>();
    public static ArrayList<OxidizerProperties> oxidizerList = new ArrayList<>();
    public static ArrayList<CombustionGeneratorOxidizerRecipe> oxidizerRecipes = new ArrayList<>();
    public static ArrayList<FluidStack> fluidInputList = new ArrayList<>();
    public static ArrayList<Fluid> rawFluidInputList = new ArrayList<>();

    public ArrayList<FluidStack> nsFluidInputList = new ArrayList<>();
    public ArrayList<Fluid> nsRawFluidInputList = new ArrayList<>();

    private final ResourceLocation recipeId;
    private int processTime;
    private int inputArraySize;

    private FluidStack inputFluid;
    public ItemStack result;

    public CombustionGeneratorOxidizerRecipe(ResourceLocation recipeId){
        this.recipeId = recipeId;
    }

    private final Map<Ingredient, Integer> ingredients = new LinkedHashMap<>();

    public Map<Ingredient, Integer> getIngredientMap() {
        return ImmutableMap.copyOf(ingredients);
    }

    public Ingredient getIngredient(){ return ingredient;}

    public int getIngredientCount(){ return ingredientCount;}

    public ItemStack getResult() {return result;}

    public FluidStack getInputFluid(){
        return this.inputFluid.copy();
    }

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

    public int getProcessTime(){return processTime;}


    public static class Serializer extends ForgeRegistryEntry<IRecipeSerializer<?>> implements IRecipeSerializer<CombustionGeneratorOxidizerRecipe> {
        @Override
        public CombustionGeneratorOxidizerRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            CombustionGeneratorOxidizerRecipe recipe = new CombustionGeneratorOxidizerRecipe(recipeId);

            recipe.ingredient = Ingredient.fromJson(json.get("ingredient"));
            recipe.ingredientCount = JSONUtils.getAsInt(json.get("ingredient").getAsJsonObject(), "count", 1);
            recipe.processTime = JSONUtils.getAsInt(json,"process_time",1600);

            for (ItemStack stack : recipe.ingredient.getItems()){
                if(!ingredientList.contains(stack.getItem())){
                    ingredientList.add(stack.getItem());
                }
            }

            for (ItemStack stack : recipe.ingredient.getItems()){
                boolean hit = false;
                for (OxidizerProperties oxidizerProperties : oxidizerList) {
                    ItemStack bucketStack = oxidizerProperties.getBucketItem();
                    if (bucketStack.getItem() == stack.getItem()) {
                        hit = true;
                        break;
                    }
                }
                if (!hit){
                    OxidizerProperties temp = new OxidizerProperties(stack,recipe.processTime);
                    oxidizerList.add(temp);
                }
            }

            // A tag is used instead of a manually defined fluid
            try{
                if(json.get("input_fluid").getAsJsonObject().has("tag") && !json.get("input_fluid").getAsJsonObject().has("fluid")){
                    ResourceLocation fluidTagLocation = ResourceLocation.of(JSONUtils.getAsString(json.get("input_fluid").getAsJsonObject(),"tag","minecraft:empty"),':');
                    ITag<Fluid> tag = TagCollectionManager.getInstance().getFluids().getTag(fluidTagLocation);
                    if(tag != null){
                        for(Fluid fluid : tag.getValues()){
                            FluidStack tempStack = new FluidStack(fluid.getFluid(), 1000);
                            fluidInputList.add(tempStack);
                            rawFluidInputList.add(tempStack.getRawFluid());
                            recipe.nsFluidInputList.add(tempStack.copy());
                            recipe.nsRawFluidInputList.add(tempStack.getRawFluid());
                            recipe.inputArraySize = recipe.nsFluidInputList.size();
                        }



                        // Sane add
                        saneAdd(recipe);
                    } else {
                        VoluminousEnergy.LOGGER.debug("Tag is null!");
                    }

                } else if (!json.get("input_fluid").getAsJsonObject().has("tag") && json.get("input_fluid").getAsJsonObject().has("fluid")){
                    // In here, a manually defined fluid is used instead of a tag
                    ResourceLocation fluidResourceLocation = ResourceLocation.of(JSONUtils.getAsString(json.get("input_fluid").getAsJsonObject(),"fluid","minecraft:empty"),':');
                    recipe.inputFluid = new FluidStack(Objects.requireNonNull(ForgeRegistries.FLUIDS.getValue(fluidResourceLocation).getFluid()),1000);
                    fluidInputList.add(recipe.inputFluid.copy());
                    rawFluidInputList.add(recipe.inputFluid.getRawFluid());
                    recipe.nsFluidInputList.add(recipe.inputFluid.copy());
                    recipe.nsRawFluidInputList.add(recipe.inputFluid.getRawFluid());
                    recipe.inputArraySize = recipe.nsFluidInputList.size();
                    saneAdd(recipe);
                } else {
                    throw new JsonSyntaxException("Invalid recipe input for an Oxidizer, please check usage of tag and fluid in the json file.");
                }
            } catch (Exception e){
                VoluminousEnergy.LOGGER.debug("NULL! CombustionGeneratorOxidizerRecipe");
            }

            recipe.result = new ItemStack(Items.BUCKET); // REQUIRED TO PREVENT JEI OR VANILLA RECIPE BOOK TO RETURN A NULL POINTER
            return recipe;
        }

        @Nullable
        @Override
        public CombustionGeneratorOxidizerRecipe fromNetwork(ResourceLocation recipeId, PacketBuffer buffer){
            CombustionGeneratorOxidizerRecipe recipe = new CombustionGeneratorOxidizerRecipe((recipeId));
            recipe.ingredient = Ingredient.fromNetwork(buffer);
            recipe.ingredientCount = buffer.readByte();

            recipe.inputArraySize = buffer.readInt();
            for (int i = 0; i < recipe.inputArraySize; i++){
                FluidStack serverFluid = buffer.readFluidStack();
                recipe.nsFluidInputList.add(serverFluid.copy());
                recipe.nsRawFluidInputList.add(serverFluid.getRawFluid());
            }

            recipe.result = buffer.readItem();
            recipe.processTime = buffer.readInt();
            saneAdd(recipe);
            return recipe;
        }

        @Override
        public void toNetwork(PacketBuffer buffer, CombustionGeneratorOxidizerRecipe recipe){
            recipe.ingredient.toNetwork(buffer);
            buffer.writeByte(recipe.getIngredientCount());

            buffer.writeInt(recipe.inputArraySize);
            for(int i = 0; i < recipe.inputArraySize; i++){
                buffer.writeFluidStack(recipe.nsFluidInputList.get(i).copy());
            }

            buffer.writeItem(recipe.getResult());
            buffer.writeInt(recipe.processTime);
            saneAdd(recipe);
        }

        public void saneAdd(CombustionGeneratorOxidizerRecipe recipe){
            if(CombustionGeneratorOxidizerRecipe.oxidizerRecipes.size() >= (Short.MAX_VALUE * 32)) return; // If greater than 1,048,544 don't bother to add any more
            // Sanity check to prevent multiple of the same recipes being stored in the array
            ArrayList<FluidStack> sanityList = new ArrayList<>();
            for(int i = 0; (i < CombustionGeneratorOxidizerRecipe.oxidizerRecipes.size() || CombustionGeneratorOxidizerRecipe.oxidizerRecipes.size() == 0); i++){
                if(CombustionGeneratorOxidizerRecipe.oxidizerRecipes.size() == 0){
                    sanityList.addAll(recipe.nsFluidInputList);

                    oxidizerRecipes.add(recipe);
                    continue;
                }
                CombustionGeneratorOxidizerRecipe referenceRecipe = CombustionGeneratorOxidizerRecipe.oxidizerRecipes.get(i);
                for(int j = 0; j < referenceRecipe.nsFluidInputList.size(); j++){
                    if(!sanityList.isEmpty()){
                        AtomicBoolean isInsane = new AtomicBoolean(false);

                        referenceRecipe.nsFluidInputList.forEach(fluidStack -> {
                            if(sanityList.contains(fluidStack)){
                                isInsane.set(true);
                            }
                        });

                        if(!isInsane.get()){
                            sanityList.addAll(referenceRecipe.nsFluidInputList);

                            // Original logic
                            oxidizerRecipes.add(recipe);
                        }
                    } else { // assume sane
                        sanityList.addAll(referenceRecipe.nsFluidInputList);

                        oxidizerRecipes.add(recipe);
                    }
                }
            }
        }
    }


}