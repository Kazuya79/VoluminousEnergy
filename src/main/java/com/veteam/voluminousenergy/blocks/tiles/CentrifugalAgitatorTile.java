package com.veteam.voluminousenergy.blocks.tiles;

import com.veteam.voluminousenergy.blocks.blocks.VEBlocks;
import com.veteam.voluminousenergy.blocks.containers.CentrifugalAgitatorContainer;
import com.veteam.voluminousenergy.recipe.CentrifugalAgitatorRecipe;
import com.veteam.voluminousenergy.recipe.VEFluidRecipe;
import com.veteam.voluminousenergy.tools.Config;
import com.veteam.voluminousenergy.tools.energy.VEEnergyStorage;
import com.veteam.voluminousenergy.tools.networking.VENetwork;
import com.veteam.voluminousenergy.tools.networking.packets.BoolButtonPacket;
import com.veteam.voluminousenergy.tools.networking.packets.DirectionButtonPacket;
import com.veteam.voluminousenergy.tools.networking.packets.TankBoolPacket;
import com.veteam.voluminousenergy.tools.networking.packets.TankDirectionPacket;
import com.veteam.voluminousenergy.tools.sidemanager.VESlotManager;
import com.veteam.voluminousenergy.util.IntToDirection;
import com.veteam.voluminousenergy.util.RecipeUtil;
import com.veteam.voluminousenergy.util.RelationalTank;
import com.veteam.voluminousenergy.util.TankType;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
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
import java.util.UUID;

public class CentrifugalAgitatorTile extends VEFluidTileEntity {

    private LazyOptional<ItemStackHandler> handler = LazyOptional.of(() -> this.inventory);
    private LazyOptional<IItemHandlerModifiable> input0h = LazyOptional.of(() -> new RangedWrapper(this.inventory, 0, 1));
    private LazyOptional<IItemHandlerModifiable> input1h = LazyOptional.of(() -> new RangedWrapper(this.inventory, 1, 2));
    private LazyOptional<IItemHandlerModifiable> output0h = LazyOptional.of(() -> new RangedWrapper(this.inventory, 2, 3));
    private LazyOptional<IItemHandlerModifiable> output1h = LazyOptional.of(() -> new RangedWrapper(this.inventory, 3, 4));

    private LazyOptional<IEnergyStorage> energy = LazyOptional.of(this::createEnergy);
    private LazyOptional<IFluidHandler> inputFluidHandler = LazyOptional.of(this::createInputTankFluidHandler);
    private LazyOptional<IFluidHandler> output0FluidHandler = LazyOptional.of(this::createOutputTank0FluidHandler);
    private LazyOptional<IFluidHandler> output1FluidHandler = LazyOptional.of(this::createOutputTank1FluidHandler);

    public VESlotManager input0sm = new VESlotManager(0, Direction.UP, true, "slot.voluminousenergy.input_slot");
    public VESlotManager input1sm = new VESlotManager(1, Direction.DOWN, true, "slot.voluminousenergy.output_slot");
    public VESlotManager output0sm = new VESlotManager(2, Direction.NORTH, true, "slot.voluminousenergy.output_slot");
    public VESlotManager output1sm = new VESlotManager(3, Direction.SOUTH, true, "slot.voluminousenergy.output_slot");

    RelationalTank inputTank = new RelationalTank(new FluidTank(TANK_CAPACITY),0,null,null, TankType.INPUT);
    RelationalTank outputTank0 = new RelationalTank(new FluidTank(TANK_CAPACITY),1,null,null, TankType.OUTPUT,0);
    RelationalTank outputTank1 = new RelationalTank(new FluidTank(TANK_CAPACITY),2,null,null, TankType.OUTPUT,1);

    private int counter;
    private int length;


    private static final Logger LOGGER = LogManager.getLogger();


    public CentrifugalAgitatorTile() {
        super(VEBlocks.CENTRIFUGAL_AGITATOR_TILE);
    }

    public ItemStackHandler inventory = createHandler();

    @Override
    public ItemStackHandler getItemStackHandler() {
        return inventory;
    }

    @Override
    public void tick() {
        updateClients();
        ItemStack input = inventory.getStackInSlot(0).copy();
        ItemStack input1 = inventory.getStackInSlot(1).copy();
        ItemStack output0 = inventory.getStackInSlot(2).copy();
        ItemStack output1 = inventory.getStackInSlot(3).copy();

        inputTank.setInput(input.copy());
        inputTank.setOutput(input1.copy());

        outputTank0.setOutput(output0);
        outputTank1.setOutput(output1);

        if(this.inputFluid(inputTank,0,1)) return;
        if(this.outputFluid(inputTank,0,1)) return;
        if(this.outputFluidStatic(outputTank0,2)) return;
        if(this.outputFluidStatic(outputTank1,3)) return;
        // Main Fluid Processing occurs here
        if (inputTank != null) {
            //ItemStack inputFluidStack = new ItemStack(inputTank.getTank().getFluid().getRawFluid().getFilledBucket(), 1);
            //lVEFluidRecipe recipe = world.getRecipeManager().getRecipe(CentrifugalAgitatorRecipe.RECIPE_TYPE, new Inventory(inputFluidStack), world).orElse(null);
            VEFluidRecipe recipe = RecipeUtil.getCentrifugalAgitatorRecipe(level,inputTank.getTank().getFluid().copy());
            if (recipe != null) {
                if (outputTank0 != null && outputTank1 != null) {

                    // Tank fluid amount check + tank cap checks
                    if (inputTank.getTank().getFluidAmount() >= recipe.getInputAmount()
                            && outputTank0.getTank().getFluidAmount() + recipe.getOutputAmount() <= TANK_CAPACITY
                            && outputTank1.getTank().getFluidAmount() + recipe.getFluids().get(1).getAmount() <= TANK_CAPACITY) {
                        // Check for power
                        if (canConsumeEnergy()) {
                            if (counter == 1) {

                                // Drain Input
                                inputTank.getTank().drain(recipe.getInputAmount(), IFluidHandler.FluidAction.EXECUTE);

                                // First Output Tank
                                if (outputTank0.getTank().getFluid().getRawFluid() != recipe.getOutputFluid().getRawFluid()) {
                                    outputTank0.getTank().setFluid(recipe.getOutputFluid().copy());
                                } else {
                                    outputTank0.getTank().fill(recipe.getOutputFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
                                }

                                // Second Output Tank
                                CentrifugalAgitatorRecipe centrifugalAgitatorRecipe = (CentrifugalAgitatorRecipe) recipe;
                                if (outputTank1.getTank().getFluid().getRawFluid() != centrifugalAgitatorRecipe.getSecondFluid().getRawFluid()) {
                                    outputTank1.getTank().setFluid(centrifugalAgitatorRecipe.getSecondFluid().copy());
                                } else {
                                    outputTank1.getTank().fill(centrifugalAgitatorRecipe.getSecondResult().copy(), IFluidHandler.FluidAction.EXECUTE);
                                }

                                counter--;
                                consumeEnergy();
                                this.setChanged();
                            } else if (counter > 0) {
                                counter--;
                                consumeEnergy();
                            } else {
                                counter = this.calculateCounter(recipe.getProcessTime(),inventory.getStackInSlot(4));
                                length = counter;
                            }
                        } // Energy Check
                    } else { // If fluid tank empty set counter to zero
                        counter = 0;
                    }
                }
            }
        }
    }

    // Extract logic for energy management, since this is getting quite complex now.
    private void consumeEnergy(){
        energy.ifPresent(e -> ((VEEnergyStorage)e)
                .consumeEnergy(this.consumptionMultiplier(Config.CENTRIFUGAL_AGITATOR_POWER_USAGE.get(),
                        this.inventory.getStackInSlot(4).copy()
                        )
                )
        );
    }

    private boolean canConsumeEnergy(){
        return this.getCapability(CapabilityEnergy.ENERGY).map(IEnergyStorage::getEnergyStored).orElse(0)
                > this.consumptionMultiplier(Config.CENTRIFUGAL_AGITATOR_POWER_USAGE.get(), this.inventory.getStackInSlot(4).copy());
    }

    /*
        Read and Write on World save
     */

    @Override
    public void load(BlockState state, CompoundNBT tag) {
        CompoundNBT inv = tag.getCompound("inv");
        handler.ifPresent(h -> ((INBTSerializable<CompoundNBT>) h).deserializeNBT(inv));
        createHandler().deserializeNBT(inv);
        CompoundNBT energyTag = tag.getCompound("energy");
        energy.ifPresent(h -> ((INBTSerializable<CompoundNBT>) h).deserializeNBT(energyTag));

        // Tanks
        CompoundNBT inputTank = tag.getCompound("inputTank");
        CompoundNBT outputTank0 = tag.getCompound("outputTank0");
        CompoundNBT outputTank1 = tag.getCompound("outputTank1");

        this.inputTank.getTank().readFromNBT(inputTank);
        this.outputTank0.getTank().readFromNBT(outputTank0);
        this.outputTank1.getTank().readFromNBT(outputTank1);

        this.inputTank.readGuiProperties(tag,"input_tank_gui");
        this.outputTank0.readGuiProperties(tag, "output_tank_0_gui");
        this.outputTank1.readGuiProperties(tag, "output_tank_1_gui");

        super.load(state, tag);
    }

    @Override
    public CompoundNBT save(CompoundNBT tag) {
        handler.ifPresent(h -> {
            CompoundNBT compound = ((INBTSerializable<CompoundNBT>) h).serializeNBT();
            tag.put("inv", compound);
        });
        energy.ifPresent(h -> {
            CompoundNBT compound = ((INBTSerializable<CompoundNBT>) h).serializeNBT();
            tag.put("energy", compound);
        });

        // Tanks
        CompoundNBT inputNBT = new CompoundNBT();
        CompoundNBT outputNBT0 = new CompoundNBT();
        CompoundNBT outputNBT1 = new CompoundNBT();

        this.inputTank.getTank().writeToNBT(inputNBT);
        this.outputTank0.getTank().writeToNBT(outputNBT0);
        this.outputTank1.getTank().writeToNBT(outputNBT1);

        tag.put("inputTank", inputNBT);
        tag.put("outputTank0", outputNBT0);
        tag.put("outputTank1", outputNBT1);

        this.inputTank.writeGuiProperties(tag, "input_tank_gui");
        this.outputTank0.writeGuiProperties(tag, "output_tank_0_gui");
        this.outputTank1.writeGuiProperties(tag, "output_tank_1_gui");

        return super.save(tag);
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
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) {
        energy.ifPresent(e -> ((VEEnergyStorage)e).setEnergy(pkt.getTag().getInt("energy")));
        this.load(this.getBlockState(), pkt.getTag());
        super.onDataPacket(net, pkt);
    }

    private IFluidHandler createInputTankFluidHandler() {
        return createFluidHandler(new CentrifugalAgitatorRecipe(), inputTank);
    }

    private IFluidHandler createOutputTank0FluidHandler(){
        return createFluidHandler(new CentrifugalAgitatorRecipe(), outputTank0);
    }

    private IFluidHandler createOutputTank1FluidHandler(){
        return createFluidHandler(new CentrifugalAgitatorRecipe(), outputTank1);
    }


    private ItemStackHandler createHandler() {
        return new ItemStackHandler(5) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }

            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) { //IS ITEM VALID PLEASE DO THIS PER SLOT TO SAVE DEBUG HOURS!!!!
                return true;
            }

            @Nonnull
            @Override
            public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) { //ALSO DO THIS PER SLOT BASIS TO SAVE DEBUG HOURS!!!
                return super.insertItem(slot, stack, simulate);
            }
        };
    }

    private IEnergyStorage createEnergy() {
        return new VEEnergyStorage(Config.CENTRIFUGAL_AGITATOR_MAX_POWER.get(), Config.CENTRIFUGAL_AGITATOR_TRANSFER.get()); // Max Power Storage, Max transfer
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            if (side == null) return handler.cast();
            if(output0sm.getStatus() && output0sm.getDirection().get3DDataValue() == side.get3DDataValue()){
                return output0h.cast();
            } else if (output1sm.getStatus() && output1sm.getDirection().get3DDataValue() == side.get3DDataValue()){
                return output1h.cast();
            } else if (input0sm.getStatus() && input0sm.getDirection().get3DDataValue() == side.get3DDataValue()){
                return input0h.cast();
            } else if (input1sm.getStatus() && input1sm.getDirection().get3DDataValue() == side.get3DDataValue()){
                return input1h.cast();
            }
        }
        if (cap == CapabilityEnergy.ENERGY) {
            return energy.cast();
        }
        if (cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY){
            if(inputTank.getSideStatus() && inputTank.getSideDirection().get3DDataValue() == side.get3DDataValue())
                return inputFluidHandler.cast();
            else if (outputTank0.getSideStatus() && outputTank0.getSideDirection().get3DDataValue() == side.get3DDataValue())
                return output0FluidHandler.cast();
            else if (outputTank1.getSideStatus() && outputTank1.getSideDirection().get3DDataValue() == side.get3DDataValue())
                return output1FluidHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public ITextComponent getDisplayName() {
        return new StringTextComponent(getType().getRegistryName().getPath());
    }

    @Nullable
    @Override
    public Container createMenu(int i, @Nonnull PlayerInventory playerInventory, @Nonnull PlayerEntity playerEntity) {
        return new CentrifugalAgitatorContainer(i, level, worldPosition, playerInventory, playerEntity);
    }

    public int progressCounterPX(int px) {
        if (counter == 0) {
            return 0;
        } else {
            return (px * (100 - ((counter * 100) / length))) / 100;
        }
    }

    public FluidStack getFluidStackFromTank(int num){
        if (num == 0){
            return inputTank.getTank().getFluid();
        } else if (num == 1){
            return outputTank0.getTank().getFluid();
        } else if (num == 2){
            return outputTank1.getTank().getFluid();
        }
        return FluidStack.EMPTY;
    }

    public int getTankCapacity(){
        return TANK_CAPACITY;
    }

    public RelationalTank getInputTank(){
        return this.inputTank;
    }

    public RelationalTank getOutputTank0(){
        return this.outputTank0;
    }

    public RelationalTank getOutputTank1(){
        return this.outputTank1;
    }

    public void updatePacketFromGui(boolean status, int slotId){
        if(slotId == input0sm.getSlotNum()){
            input0sm.setStatus(status);
        } else if (slotId == input1sm.getSlotNum()){
            input1sm.setStatus(status);
        } else if(slotId == output0sm.getSlotNum()){
            output0sm.setStatus(status);
        } else if(slotId == output1sm.getSlotNum()){
            output1sm.setStatus(status);
        }
    }

    public void updatePacketFromGui(int direction, int slotId){
        if(slotId == input0sm.getSlotNum()){
            input0sm.setDirection(direction);
        } else if (slotId == input1sm.getSlotNum()){
            input1sm.setDirection(direction);
        } else if(slotId == output0sm.getSlotNum()){
            output0sm.setDirection(direction);
        } else if(slotId == output1sm.getSlotNum()){
            output1sm.setDirection(direction);
        }
    }

    public void updateTankPacketFromGui(boolean status, int id){
        if(id == this.inputTank.getId()){
            this.inputTank.setSideStatus(status);
        } else if(id == this.outputTank0.getId()){
            this.outputTank0.setSideStatus(status);
        } else if(id == this.outputTank1.getId()){
            this.outputTank1.setSideStatus(status);
        }
    }

    public void updateTankPacketFromGui(int direction, int id){
        if(id == this.inputTank.getId()){
            this.inputTank.setSideDirection(IntToDirection.IntegerToDirection(direction));
        } else if(id == this.outputTank0.getId()){
            this.outputTank0.setSideDirection(IntToDirection.IntegerToDirection(direction));
        } else if(id == this.outputTank1.getId()){
            this.outputTank1.setSideDirection(IntToDirection.IntegerToDirection(direction));
        }
    }

    @Override
    public void sendPacketToClient(){
        if(level == null || getLevel() == null) return;
        if(getLevel().getServer() != null) {
            this.playerUuid.forEach(u -> {
                level.getServer().getPlayerList().getPlayers().forEach(s -> {
                    if (s.getUUID().equals(u)){
                        // Boolean Buttons
                        VENetwork.channel.send(PacketDistributor.PLAYER.with(() -> s), new BoolButtonPacket(input0sm.getStatus(), input0sm.getSlotNum()));
                        VENetwork.channel.send(PacketDistributor.PLAYER.with(() -> s), new BoolButtonPacket(input1sm.getStatus(), input1sm.getSlotNum()));
                        VENetwork.channel.send(PacketDistributor.PLAYER.with(() -> s), new BoolButtonPacket(output0sm.getStatus(), output0sm.getSlotNum()));
                        VENetwork.channel.send(PacketDistributor.PLAYER.with(() -> s), new BoolButtonPacket(output1sm.getStatus(), output1sm.getSlotNum()));
                        VENetwork.channel.send(PacketDistributor.PLAYER.with(() -> s), new TankBoolPacket(inputTank.getSideStatus(), inputTank.getId()));
                        VENetwork.channel.send(PacketDistributor.PLAYER.with(() -> s), new TankBoolPacket(outputTank0.getSideStatus(), outputTank0.getId()));
                        VENetwork.channel.send(PacketDistributor.PLAYER.with(() -> s), new TankBoolPacket(outputTank1.getSideStatus(), outputTank1.getId()));

                        // Direction Buttons
                        VENetwork.channel.send(PacketDistributor.PLAYER.with(() -> s), new DirectionButtonPacket(input0sm.getDirection().get3DDataValue(),input0sm.getSlotNum()));
                        VENetwork.channel.send(PacketDistributor.PLAYER.with(() -> s), new DirectionButtonPacket(input1sm.getDirection().get3DDataValue(),input1sm.getSlotNum()));
                        VENetwork.channel.send(PacketDistributor.PLAYER.with(() -> s), new DirectionButtonPacket(output0sm.getDirection().get3DDataValue(),output0sm.getSlotNum()));
                        VENetwork.channel.send(PacketDistributor.PLAYER.with(() -> s), new DirectionButtonPacket(output1sm.getDirection().get3DDataValue(),output1sm.getSlotNum()));
                        VENetwork.channel.send(PacketDistributor.PLAYER.with(() -> s), new TankDirectionPacket(inputTank.getSideDirection().get3DDataValue(), inputTank.getId()));
                        VENetwork.channel.send(PacketDistributor.PLAYER.with(() -> s), new TankDirectionPacket(outputTank0.getSideDirection().get3DDataValue(), outputTank0.getId()));
                        VENetwork.channel.send(PacketDistributor.PLAYER.with(() -> s), new TankDirectionPacket(outputTank1.getSideDirection().get3DDataValue(), outputTank1.getId()));
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

            // Boolean Buttons
            VENetwork.channel.send(PacketDistributor.NEAR.with(() -> targetPoint), new BoolButtonPacket(input0sm.getStatus(), input0sm.getSlotNum()));
            VENetwork.channel.send(PacketDistributor.NEAR.with(() -> targetPoint), new BoolButtonPacket(input1sm.getStatus(), input1sm.getSlotNum()));
            VENetwork.channel.send(PacketDistributor.NEAR.with(() -> targetPoint), new BoolButtonPacket(output0sm.getStatus(), output0sm.getSlotNum()));
            VENetwork.channel.send(PacketDistributor.NEAR.with(() -> targetPoint), new BoolButtonPacket(output1sm.getStatus(), output1sm.getSlotNum()));
            VENetwork.channel.send(PacketDistributor.NEAR.with(() -> targetPoint), new TankBoolPacket(inputTank.getSideStatus(), inputTank.getId()));
            VENetwork.channel.send(PacketDistributor.NEAR.with(() -> targetPoint), new TankBoolPacket(outputTank0.getSideStatus(), outputTank0.getId()));
            VENetwork.channel.send(PacketDistributor.NEAR.with(() -> targetPoint), new TankBoolPacket(outputTank1.getSideStatus(), outputTank1.getId()));

            // Direction Buttons
            VENetwork.channel.send(PacketDistributor.NEAR.with(() -> targetPoint), new DirectionButtonPacket(input0sm.getDirection().get3DDataValue(),input0sm.getSlotNum()));
            VENetwork.channel.send(PacketDistributor.NEAR.with(() -> targetPoint), new DirectionButtonPacket(input1sm.getDirection().get3DDataValue(),input1sm.getSlotNum()));
            VENetwork.channel.send(PacketDistributor.NEAR.with(() -> targetPoint), new DirectionButtonPacket(output0sm.getDirection().get3DDataValue(),output0sm.getSlotNum()));
            VENetwork.channel.send(PacketDistributor.NEAR.with(() -> targetPoint), new DirectionButtonPacket(output1sm.getDirection().get3DDataValue(),output1sm.getSlotNum()));
            VENetwork.channel.send(PacketDistributor.NEAR.with(() -> targetPoint), new TankDirectionPacket(inputTank.getSideDirection().get3DDataValue(), inputTank.getId()));
            VENetwork.channel.send(PacketDistributor.NEAR.with(() -> targetPoint), new TankDirectionPacket(outputTank0.getSideDirection().get3DDataValue(), outputTank0.getId()));
            VENetwork.channel.send(PacketDistributor.NEAR.with(() -> targetPoint), new TankDirectionPacket(outputTank1.getSideDirection().get3DDataValue(), outputTank1.getId()));
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
                    if(!(player.containerMenu instanceof CentrifugalAgitatorContainer)){
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