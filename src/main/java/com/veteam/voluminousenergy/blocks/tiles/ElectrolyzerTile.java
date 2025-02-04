package com.veteam.voluminousenergy.blocks.tiles;

import com.veteam.voluminousenergy.blocks.blocks.VEBlocks;
import com.veteam.voluminousenergy.blocks.containers.ElectrolyzerContainer;
import com.veteam.voluminousenergy.items.VEItems;
import com.veteam.voluminousenergy.recipe.ElectrolyzerRecipe;
import com.veteam.voluminousenergy.tools.Config;
import com.veteam.voluminousenergy.tools.energy.VEEnergyStorage;
import com.veteam.voluminousenergy.tools.networking.VENetwork;
import com.veteam.voluminousenergy.tools.networking.packets.BoolButtonPacket;
import com.veteam.voluminousenergy.tools.networking.packets.DirectionButtonPacket;
import com.veteam.voluminousenergy.tools.sidemanager.VESlotManager;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RangedWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static net.minecraft.util.math.MathHelper.abs;

public class ElectrolyzerTile extends VoluminousTileEntity implements ITickableTileEntity, INamedContainerProvider {
    private LazyOptional<ItemStackHandler> handler = LazyOptional.of(() -> this.inventory); // Main item handler
    private LazyOptional<IItemHandlerModifiable> inputHandler = LazyOptional.of(() -> new RangedWrapper(this.inventory,0,1));
    private LazyOptional<IItemHandlerModifiable> bucketHandler = LazyOptional.of(() -> new RangedWrapper(this.inventory,1,2));
    private LazyOptional<IItemHandlerModifiable> outputHandler = LazyOptional.of(() -> new RangedWrapper(this.inventory,2,3));
    private LazyOptional<IItemHandlerModifiable> rngOneHandler = LazyOptional.of(() -> new RangedWrapper(this.inventory,3,4));
    private LazyOptional<IItemHandlerModifiable> rngTwoHandler = LazyOptional.of(() -> new RangedWrapper(this.inventory,4,5));
    private LazyOptional<IItemHandlerModifiable> rngThreeHandler = LazyOptional.of(() -> new RangedWrapper(this.inventory,5,6));

    private LazyOptional<IEnergyStorage> energy = LazyOptional.of(this::createEnergy);

    public VESlotManager inputSm = new VESlotManager(0,Direction.UP,true,"slot.voluminousenergy.input_slot");
    public VESlotManager bucketSm = new VESlotManager(1,Direction.WEST,true,"slot.voluminousenergy.input_slot");
    public VESlotManager outputSm = new VESlotManager(2,Direction.DOWN,true,"slot.voluminousenergy.output_slot");
    public VESlotManager rngOneSm = new VESlotManager(3, Direction.NORTH, true,"slot.voluminousenergy.output_slot");
    public VESlotManager rngTwoSm = new VESlotManager(4,Direction.SOUTH,true,"slot.voluminousenergy.output_slot");
    public VESlotManager rngThreeSm = new VESlotManager(5,Direction.EAST,true,"slot.voluminousenergy.output_slot");
    private int counter;
    private int length;
    private AtomicReference<ItemStack> inputItemStack = new AtomicReference<ItemStack>(new ItemStack(Items.AIR,0));
    private static final Logger LOGGER = LogManager.getLogger();

    // Sided item handlers
    //private LazyOptional<IItemHandlerModifiable> inputItemHandler = LazyOptional.of(() -> new RangedWrapper(this.inventory, 0, 2));
    //private LazyOptional<IItemHandlerModifiable> outputItemHandler = LazyOptional.of(() -> new RangedWrapper(this.inventory, 2, 6));


    public ElectrolyzerTile(){
        super(VEBlocks.ELECTROLYZER_TILE);
    }

    @Override
    public void tick(){

        updateClients();

        handler.ifPresent(h -> {
            ItemStack input = h.getStackInSlot(0).copy();
            ItemStack bucket = h.getStackInSlot(1).copy();
            ItemStack output = h.getStackInSlot(2).copy();
            ItemStack rngOne = h.getStackInSlot(3).copy();
            ItemStack rngTwo = h.getStackInSlot(4).copy();
            ItemStack rngThree = h.getStackInSlot(5).copy();

            ElectrolyzerRecipe recipe = level.getRecipeManager().getRecipeFor(ElectrolyzerRecipe.RECIPE_TYPE, new Inventory(input), level).orElse(null);
            inputItemStack.set(input.copy()); // Atomic Reference, use this to query recipes

            if (usesBucket(recipe,bucket.copy())){
                if (!areSlotsFull(recipe,output.copy(),rngOne.copy(),rngTwo.copy(),rngThree.copy()) && canConsumeEnergy()) {
                    if (counter == 1){ //The processing is about to be complete
                        // Extract the inputted item
                        h.extractItem(0,recipe.ingredientCount,false);
                        // Extract bucket if it uses a bucket
                        if (recipe.needsBuckets() > 0){
                            h.extractItem(1,recipe.needsBuckets(),false);
                        }

                        // Get output stack from the recipe
                        ItemStack newOutputStack = recipe.getResult().copy();

                        //LOGGER.debug("output: " + output + " rngOne: " + rngOne + " rngTwo: " + rngTwo + " rngThree: " + rngThree + " newOutputStack: "  + newOutputStack);

                        // Manipulating the Output slot
                        if (output.getItem() != newOutputStack.getItem() || output.getItem() == Items.AIR) {
                            if(output.getItem() == Items.AIR){ // Fix air >1 jamming slots
                                output.setCount(1);
                            }
                            newOutputStack.setCount(recipe.getOutputAmount());
                            //LOGGER.debug(" Stack to output: " + newOutputStack.copy());
                            h.insertItem(2,newOutputStack.copy(),false); // CRASH the game if this is not empty!
                            //LOGGER.debug(" in output slot: " + h.getStackInSlot(2).copy());
                        } else { // Assuming the recipe output item is already in the output slot
                            output.setCount(recipe.getOutputAmount()); // Simply change the stack to equal the output amount
                            h.insertItem(2,output.copy(),false); // Place the new output stack on top of the old one
                        }

                        // Manipulating the RNG 0 slot
                        if (recipe.getChance0() != 0){ // If the chance is ZERO, this functionality won't be used
                            ItemStack newRngStack = recipe.getRngItemSlot0().copy();

                            // Generate Random floats
                            Random r = new Random();
                            float random = abs(0 + r.nextFloat() * (0 - 1));
                            //LOGGER.debug("Random: " + random);
                            // ONLY manipulate the slot if the random float is under or is identical to the chance float
                            if(random <= recipe.getChance0()){
                                //LOGGER.debug("Chance HIT!");
                                if (rngOne.getItem() != recipe.getRngItemSlot0().getItem()){
                                    if (rngOne.getItem() == Items.AIR){
                                        rngOne.setCount(1);
                                    }
                                    newRngStack.setCount(recipe.getOutputRngAmount0());
                                    h.insertItem(3, newRngStack.copy(),false); // CRASH the game if this is not empty!
                                } else { // Assuming the recipe output item is already in the output slot
                                    rngOne.setCount(recipe.getOutputRngAmount0()); // Simply change the stack to equal the output amount
                                    h.insertItem(3,rngOne.copy(),false); // Place the new output stack on top of the old one
                                }
                            }
                        }

                        // Manipulating the RNG 1 slot
                        if (recipe.getChance1() != 0){ // If the chance is ZERO, this functionality won't be used
                            ItemStack newRngStack = recipe.getRngItemSlot1().copy();

                            // Generate Random floats
                            Random r = new Random();
                            float random = abs(0 + r.nextFloat() * (0 - 1));
                            //LOGGER.debug("Random: " + random);
                            // ONLY manipulate the slot if the random float is under or is identical to the chance float
                            if(random <= recipe.getChance1()){
                                //LOGGER.debug("Chance HIT!");
                                if (rngTwo.getItem() != recipe.getRngItemSlot1().getItem()){
                                    if (rngTwo.getItem() == Items.AIR){
                                        rngTwo.setCount(1);
                                    }
                                    newRngStack.setCount(recipe.getOutputRngAmount1());
                                    h.insertItem(4, newRngStack.copy(),false); // CRASH the game if this is not empty!
                                } else { // Assuming the recipe output item is already in the output slot
                                    rngTwo.setCount(recipe.getOutputRngAmount1()); // Simply change the stack to equal the output amount
                                    h.insertItem(4,rngTwo.copy(),false); // Place the new output stack on top of the old one
                                }
                            }
                        }

                        // Manipulating the RNG 2 slot
                        if (recipe.getChance1() != 0){ // If the chance is ZERO, this functionality won't be used
                            ItemStack newRngStack = recipe.getRngItemSlot2().copy();

                            // Generate Random floats
                            Random r = new Random();
                            float random = abs(0 + r.nextFloat() * (0 - 1));
                            //LOGGER.debug("Random: " + random);
                            // ONLY manipulate the slot if the random float is under or is identical to the chance float
                            if(random <= recipe.getChance2()){
                                //LOGGER.debug("Chance HIT!");
                                if (rngThree.getItem() != recipe.getRngItemSlot2().getItem()){
                                    if (rngThree.getItem() == Items.AIR){
                                        rngThree.setCount(1);
                                    }
                                    newRngStack.setCount(recipe.getOutputRngAmount2());
                                    h.insertItem(5, newRngStack.copy(),false); // CRASH the game if this is not empty!
                                } else { // Assuming the recipe output item is already in the output slot
                                    rngThree.setCount(recipe.getOutputRngAmount2()); // Simply change the stack to equal the output amount
                                    h.insertItem(5,rngThree.copy(),false); // Place the new output stack on top of the old one
                                }
                            }
                        }

                        counter--;
                        consumeEnergy();
                        setChanged();
                    } else if (counter > 0){ //In progress
                        counter--;
                        consumeEnergy();
                    } else { // Check if we should start processing
                        if (areSlotsEmptyOrHaveCurrentItems(recipe,output,rngOne,rngTwo,rngThree)){
                            counter = this.calculateCounter(recipe.getProcessTime(), inventory.getStackInSlot(6).copy());
                            length = counter;
                        } else {
                            counter = 0;
                        }
                    }
                } else { // This is if we reach the maximum in the slots
                    counter = 0;
                }
            } else { // this is if the input slot is empty
                counter = 0;
            }
        });
    }

    // Extract logic for energy management, since this is getting quite complex now.
    private void consumeEnergy(){
        energy.ifPresent(e -> ((VEEnergyStorage)e)
                .consumeEnergy(this.consumptionMultiplier(Config.ELECTROLYZER_POWER_USAGE.get(),
                        this.inventory.getStackInSlot(6).copy()
                        )
                )
        );
    }

    private boolean canConsumeEnergy(){
        return this.getCapability(CapabilityEnergy.ENERGY).map(IEnergyStorage::getEnergyStored).orElse(0)
                > this.consumptionMultiplier(Config.ELECTROLYZER_POWER_USAGE.get(), this.inventory.getStackInSlot(6).copy());
    }

    private boolean areSlotsFull(ElectrolyzerRecipe recipe, ItemStack one, ItemStack two, ItemStack three, ItemStack four){

        if (one.getCount() + recipe.getOutputAmount() > one.getItem().getItemStackLimit(one.copy())){ // Main output slot
            return true;
        } else if (two.getCount() + recipe.getOutputRngAmount0() > two.getItem().getItemStackLimit(two.copy())){ // Rng Slot 0
            return true;
        } else if (three.getCount() + recipe.getOutputRngAmount1() > three.getItem().getItemStackLimit(three.copy())){ // Rng Slot 1
            return true;
        } else if (four.getCount() + recipe.getOutputRngAmount2() > four.getItem().getItemStackLimit(four.copy())){ // Rng Slot 2
            return true;
        } else {
            return false;
        }
    }

    private boolean usesBucket(ElectrolyzerRecipe recipe,ItemStack bucket){
        if (recipe != null){ // If the recipe is null, don't bother processing
            if (recipe.needsBuckets() > 0){ // If it doesn't use a bucket, we know that it must have a valid recipe, return true
                if (!bucket.isEmpty() && bucket.getItem() == Items.BUCKET){
                    if(bucket.getCount() >= recipe.needsBuckets()) return true; // Needs a bucket, has enough buckets. Return true.
                    return false; // Needs a bucket, doesn't have enough buckets. Return false.
                } else {
                    return false; // Needs a bucket, doesn't have a bucket. Return false.
                }
            } else {
                return true; // Doesn't need a bucket, likely valid recipe. Return true.
            }
        }
        return false; // Likely empty slot, don't bother
    }

    private boolean areSlotsEmptyOrHaveCurrentItems(ElectrolyzerRecipe recipe, ItemStack one, ItemStack two, ItemStack three, ItemStack four){
        ArrayList<ItemStack> outputList = new ArrayList<>();
        outputList.add(one.copy());
        outputList.add(two.copy());
        outputList.add(three.copy());
        outputList.add(four.copy());
        boolean isEmpty = true;
        boolean matchesRecipe = true;
        for (ItemStack x : outputList){
            if (!x.isEmpty()){
                //LOGGER.debug("Not Empty Slot!");
                isEmpty = false;
                if (one.getItem() != recipe.getResult().getItem() && one.getItem() != Items.AIR){
                    return false;
                } else if (two.getItem() != recipe.getRngItemSlot0().getItem() && two.getItem() != Items.AIR){
                    return false;
                } else if (three.getItem() != recipe.getRngItemSlot1().getItem() && three.getItem() != Items.AIR){
                    return false;
                } else if (four.getItem() != recipe.getRngItemSlot2().getItem() && four.getItem() != Items.AIR){
                    return false;
                } else {
                    return true;
                }
            }
        }
        return isEmpty;
    }

    /*
        Read and Write on World save
     */

    @Override
    public void load(BlockState state, CompoundNBT tag){
        CompoundNBT inv = tag.getCompound("inv");
        handler.ifPresent(h -> ((INBTSerializable<CompoundNBT>)h).deserializeNBT(inv));
        //createHandler().deserializeNBT(inv);
        CompoundNBT energyTag = tag.getCompound("energy");
        energy.ifPresent(h -> ((INBTSerializable<CompoundNBT>)h).deserializeNBT(energyTag));

        inputSm.read(tag, "input_manager");
        bucketSm.read(tag, "bucket_manager");
        outputSm.read(tag, "output_manager");
        rngOneSm.read(tag, "rng_one_manager");
        rngTwoSm.read(tag, "rng_two_manager");
        rngThreeSm.read(tag, "rng_three_manager");

        super.load(state,tag);
    }

    @Override
    public CompoundNBT save(CompoundNBT tag) {
        handler.ifPresent(h -> {
            CompoundNBT compound = ((INBTSerializable<CompoundNBT>) h).serializeNBT();
            tag.put("inv", compound);
        });
        energy.ifPresent(h -> {
            CompoundNBT compound = ((INBTSerializable<CompoundNBT>)h).serializeNBT();
            tag.put("energy",compound);
        });

        inputSm.write(tag, "input_manager");
        bucketSm.write(tag, "bucket_manager");
        outputSm.write(tag, "output_manager");
        rngOneSm.write(tag, "rng_one_manager");
        rngTwoSm.write(tag, "rng_two_manager");
        rngThreeSm.write(tag, "rng_three_manager");

        return super.save(tag);
    }

    public ItemStackHandler inventory = new ItemStackHandler(7) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) { //IS ITEM VALID PLEASE DO THIS PER SLOT TO SAVE DEBUG HOURS!!!!
            ItemStack referenceStack = stack.copy();
            referenceStack.setCount(64);
            //ItemStack referenceStack1 = inputItemStack.get().copy();
            //referenceStack1.setCount(64);
            ElectrolyzerRecipe recipe = level.getRecipeManager().getRecipeFor(ElectrolyzerRecipe.RECIPE_TYPE, new Inventory(referenceStack), level).orElse(null);
            ElectrolyzerRecipe recipe1 = level.getRecipeManager().getRecipeFor(ElectrolyzerRecipe.RECIPE_TYPE, new Inventory(inputItemStack.get().copy()),level).orElse(null);

            if (slot == 0 && recipe != null){
                for (ItemStack testStack : recipe.ingredient.getItems()){
                    if(stack.getItem() == testStack.getItem()){
                        return true;
                    }
                }
            } else if (slot == 1 && stack.getItem() == Items.BUCKET){
                return true;
            } else if (slot == 2 && recipe1 != null){ // Output slot
                return stack.getItem() == recipe1.result.getItem();
            } else if (slot == 3 && recipe1 != null){ // RNG 0 slot
                return stack.getItem() == recipe1.getRngItemSlot0().getItem();
            } else if (slot == 4 && recipe1 != null){ // RNG 1 slot
                return stack.getItem() == recipe1.getRngItemSlot1().getItem();
            } else if (slot == 5 && recipe1 != null){ // RNG 2 slot
                return stack.getItem() == recipe1.getRngItemSlot2().getItem();
            } else if (slot == 6){
                return stack.getItem() == VEItems.QUARTZ_MULTIPLIER;
            }
            return false;
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate){ //ALSO DO THIS PER SLOT BASIS TO SAVE DEBUG HOURS!!!
            ItemStack referenceStack = stack.copy();
            referenceStack.setCount(64);
            ElectrolyzerRecipe recipe = level.getRecipeManager().getRecipeFor(ElectrolyzerRecipe.RECIPE_TYPE, new Inventory(referenceStack.copy()), level).orElse(null);
            ElectrolyzerRecipe recipe1 = level.getRecipeManager().getRecipeFor(ElectrolyzerRecipe.RECIPE_TYPE, new Inventory(inputItemStack.get().copy()),level).orElse(null);

            if(slot == 0 && recipe != null) {
                for (ItemStack testStack : recipe.ingredient.getItems()){
                    if(stack.getItem() == testStack.getItem()){
                        return super.insertItem(slot, stack, simulate);
                    }
                }
            } else if ( slot == 1 && stack.getItem() == Items.BUCKET) {
                return super.insertItem(slot, stack, simulate);
            } else if (slot == 2 && recipe1 != null){
                if (stack.getItem() == recipe1.result.getItem()){
                    return super.insertItem(slot, stack, simulate);
                }
            } else if (slot == 3 && recipe1 != null){
                if (stack.getItem() == recipe1.getRngItemSlot0().getItem()){
                    return super.insertItem(slot, stack, simulate);
                }
            } else if (slot == 4 && recipe1 != null){
                if (stack.getItem() == recipe1.getRngItemSlot1().getItem()){
                    return super.insertItem(slot, stack, simulate);
                }
            } else if (slot == 5 && recipe1 != null){
                if (stack.getItem() == recipe1.getRngItemSlot2().getItem()){
                    return super.insertItem(slot, stack, simulate);
                }
            } else if (slot == 6 && stack.getItem() == VEItems.QUARTZ_MULTIPLIER){
                return super.insertItem(slot, stack, simulate);
            }
            return stack;
        }
    };

    private IEnergyStorage createEnergy(){
        return new VEEnergyStorage(Config.ELECTROLYZER_MAX_POWER.get(),Config.ELECTROLYZER_TRANSFER.get()); // Max Power Storage, Max transfer
    }

    @Override
    public CompoundNBT getUpdateTag() {
        return this.save(new CompoundNBT());
    }

    @Nullable
    @Override
    public SUpdateTileEntityPacket getUpdatePacket() {
        return new SUpdateTileEntityPacket(this.worldPosition, 0, this.getUpdateTag());
    }

    @Override
    public void onDataPacket(final NetworkManager net, final SUpdateTileEntityPacket pkt){
        energy.ifPresent(e -> ((VEEnergyStorage)e).setEnergy(pkt.getTag().getInt("energy")));
        this.load(this.getBlockState(), pkt.getTag());
        super.onDataPacket(net, pkt);
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if(cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            if (side == null) {
                return handler.cast();
            } else {
                // 1 = top, 0 = bottom, 2 = north, 3 = south, 4 = west, 5 = east
                if(inputSm.getStatus() && inputSm.getDirection().get3DDataValue() == side.get3DDataValue())
                    return inputHandler.cast();
                else if(bucketSm.getStatus() && bucketSm.getDirection().get3DDataValue() == side.get3DDataValue())
                    return bucketHandler.cast();
                else if(outputSm.getStatus() && outputSm.getDirection().get3DDataValue() == side.get3DDataValue())
                    return outputHandler.cast();
                else if(rngOneSm.getStatus() && rngOneSm.getDirection().get3DDataValue() == side.get3DDataValue())
                    return rngOneHandler.cast();
                else if(rngTwoSm.getStatus() && rngTwoSm.getDirection().get3DDataValue() == side.get3DDataValue())
                    return rngTwoHandler.cast();
                else if(rngThreeSm.getStatus() && rngThreeSm.getDirection().get3DDataValue() == side.get3DDataValue())
                    return rngThreeHandler.cast();
            }
        }
        if (cap == CapabilityEnergy.ENERGY){
            return energy.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public ITextComponent getDisplayName(){
        return new StringTextComponent(getType().getRegistryName().getPath());
    }

    @Nullable
    @Override
    public Container createMenu(int i, @Nonnull PlayerInventory playerInventory, @Nonnull PlayerEntity playerEntity) {
        return new ElectrolyzerContainer(i,level,worldPosition,playerInventory,playerEntity);
    }

    public int progressCounterPX(int px){
        if (counter == 0){
            return 0;
        } else {
            return (px*(100-((counter*100)/length)))/100;
        }
    }

    public void updatePacketFromGui(boolean status, int slotId){
        if(slotId == inputSm.getSlotNum()) inputSm.setStatus(status);
        else if (slotId == bucketSm.getSlotNum()) bucketSm.setStatus(status);
        else if(slotId == outputSm.getSlotNum()) outputSm.setStatus(status);
        else if(slotId == rngOneSm.getSlotNum()) rngOneSm.setStatus(status);
        else if(slotId == rngTwoSm.getSlotNum()) rngTwoSm.setStatus(status);
        else if(slotId == rngThreeSm.getSlotNum()) rngThreeSm.setStatus(status);
    }

    public void updatePacketFromGui(int direction, int slotId){
        if(slotId == inputSm.getSlotNum()) inputSm.setDirection(direction);
        else if (slotId == bucketSm.getSlotNum()) bucketSm.setDirection(direction);
        else if(slotId == outputSm.getSlotNum()) outputSm.setDirection(direction);
        else if(slotId == rngOneSm.getSlotNum()) rngOneSm.setDirection(direction);
        else if(slotId == rngTwoSm.getSlotNum()) rngTwoSm.setDirection(direction);
        else if(slotId == rngThreeSm.getSlotNum()) rngThreeSm.setDirection(direction);
    }

    @Override
    public void sendPacketToClient(){
        if(level == null || getLevel() == null) return;
        if(getLevel().getServer() != null) {
            this.playerUuid.forEach(u -> {
                level.getServer().getPlayerList().getPlayers().forEach(s -> {
                    if (s.getUUID().equals(u)){
                        // Boolean Buttons
                        VENetwork.channel.send(PacketDistributor.PLAYER.with(() -> s), new BoolButtonPacket(inputSm.getStatus(), inputSm.getSlotNum()));
                        VENetwork.channel.send(PacketDistributor.PLAYER.with(() -> s), new BoolButtonPacket(bucketSm.getStatus(), bucketSm.getSlotNum()));
                        VENetwork.channel.send(PacketDistributor.PLAYER.with(() -> s), new BoolButtonPacket(outputSm.getStatus(), outputSm.getSlotNum()));
                        VENetwork.channel.send(PacketDistributor.PLAYER.with(() -> s), new BoolButtonPacket(rngOneSm.getStatus(), rngOneSm.getSlotNum()));
                        VENetwork.channel.send(PacketDistributor.PLAYER.with(() -> s), new BoolButtonPacket(rngTwoSm.getStatus(), rngTwoSm.getSlotNum()));
                        VENetwork.channel.send(PacketDistributor.PLAYER.with(() -> s), new BoolButtonPacket(rngThreeSm.getStatus(), rngThreeSm.getSlotNum()));

                        // Direction Buttons
                        VENetwork.channel.send(PacketDistributor.PLAYER.with(() -> s), new DirectionButtonPacket(inputSm.getDirection().get3DDataValue(),inputSm.getSlotNum()));
                        VENetwork.channel.send(PacketDistributor.PLAYER.with(() -> s), new DirectionButtonPacket(bucketSm.getDirection().get3DDataValue(),bucketSm.getSlotNum()));
                        VENetwork.channel.send(PacketDistributor.PLAYER.with(() -> s), new DirectionButtonPacket(outputSm.getDirection().get3DDataValue(),outputSm.getSlotNum()));
                        VENetwork.channel.send(PacketDistributor.PLAYER.with(() -> s), new DirectionButtonPacket(rngOneSm.getDirection().get3DDataValue(),rngOneSm.getSlotNum()));
                        VENetwork.channel.send(PacketDistributor.PLAYER.with(() -> s), new DirectionButtonPacket(rngTwoSm.getDirection().get3DDataValue(),rngTwoSm.getSlotNum()));
                        VENetwork.channel.send(PacketDistributor.PLAYER.with(() -> s), new DirectionButtonPacket(rngThreeSm.getDirection().get3DDataValue(),rngThreeSm.getSlotNum()));

                    }
                });
            });
        } else if (!playerUuid.isEmpty()){ // Legacy solution
            double x = this.getBlockPos().getX();
            double y = this.getBlockPos().getY();
            double z = this.getBlockPos().getZ();
            final double radius = 16;
            RegistryKey<World> worldRegistryKey = this.getLevel().dimension();
            PacketDistributor.TargetPoint targetPoint = new PacketDistributor.TargetPoint(x,y,z,radius,worldRegistryKey);

            VENetwork.channel.send(PacketDistributor.NEAR.with(() -> targetPoint), new BoolButtonPacket(inputSm.getStatus(), inputSm.getSlotNum()));
            VENetwork.channel.send(PacketDistributor.NEAR.with(() -> targetPoint), new BoolButtonPacket(bucketSm.getStatus(), bucketSm.getSlotNum()));
            VENetwork.channel.send(PacketDistributor.NEAR.with(() -> targetPoint), new BoolButtonPacket(outputSm.getStatus(), outputSm.getSlotNum()));
            VENetwork.channel.send(PacketDistributor.NEAR.with(() -> targetPoint), new BoolButtonPacket(rngOneSm.getStatus(), rngOneSm.getSlotNum()));
            VENetwork.channel.send(PacketDistributor.NEAR.with(() -> targetPoint), new BoolButtonPacket(rngTwoSm.getStatus(), rngTwoSm.getSlotNum()));
            VENetwork.channel.send(PacketDistributor.NEAR.with(() -> targetPoint), new BoolButtonPacket(rngThreeSm.getStatus(), rngThreeSm.getSlotNum()));

            // Direction Buttons
            VENetwork.channel.send(PacketDistributor.NEAR.with(() -> targetPoint), new DirectionButtonPacket(inputSm.getDirection().get3DDataValue(),inputSm.getSlotNum()));
            VENetwork.channel.send(PacketDistributor.NEAR.with(() -> targetPoint), new DirectionButtonPacket(bucketSm.getDirection().get3DDataValue(),bucketSm.getSlotNum()));
            VENetwork.channel.send(PacketDistributor.NEAR.with(() -> targetPoint), new DirectionButtonPacket(outputSm.getDirection().get3DDataValue(),outputSm.getSlotNum()));
            VENetwork.channel.send(PacketDistributor.NEAR.with(() -> targetPoint), new DirectionButtonPacket(rngOneSm.getDirection().get3DDataValue(),rngOneSm.getSlotNum()));
            VENetwork.channel.send(PacketDistributor.NEAR.with(() -> targetPoint), new DirectionButtonPacket(rngTwoSm.getDirection().get3DDataValue(),rngTwoSm.getSlotNum()));
            VENetwork.channel.send(PacketDistributor.NEAR.with(() -> targetPoint), new DirectionButtonPacket(rngThreeSm.getDirection().get3DDataValue(),rngThreeSm.getSlotNum()));
        }
    }

    @Override
    protected void uuidCleanup(){
        if(playerUuid.isEmpty() || level == null) return;
        if(level.getServer() == null) return;

        if(cleanupTick == 20){
            ArrayList<UUID> toRemove = new ArrayList<>();
            level.getServer().getPlayerList().getPlayers().forEach(player ->{
                if(player.containerMenu != null){
                    if(!(player.containerMenu instanceof ElectrolyzerContainer)){
                        toRemove.add(player.getUUID());
                    }
                } else if (player.containerMenu == null){
                    toRemove.add(player.getUUID());
                }
            });
            toRemove.forEach(uuid -> playerUuid.remove(uuid));
        }
        super.uuidCleanup();
    }
}
