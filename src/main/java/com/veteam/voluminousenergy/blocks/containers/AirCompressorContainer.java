package com.veteam.voluminousenergy.blocks.containers;

import com.veteam.voluminousenergy.blocks.blocks.VEBlocks;
import com.veteam.voluminousenergy.blocks.inventory.slots.VEInsertSlot;
import com.veteam.voluminousenergy.blocks.screens.AirCompressorScreen;
import com.veteam.voluminousenergy.tools.energy.VEEnergyStorage;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IWorldPosCallable;
import net.minecraft.util.IntReferenceHolder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;

import static com.veteam.voluminousenergy.blocks.blocks.VEBlocks.AIR_COMPRESSOR_CONTAINER;

public class AirCompressorContainer extends VoluminousContainer {

    private PlayerEntity playerEntity;
    private IItemHandler playerInventory;
    protected AirCompressorScreen airCompressorScreen;
    private static final Logger LOGGER = LogManager.getLogger();

    public AirCompressorContainer(int id, World world, BlockPos pos, PlayerInventory inventory, PlayerEntity player){
        super(AIR_COMPRESSOR_CONTAINER,id);
        this.tileEntity = world.getBlockEntity(pos);
        this.tileEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);
        this.playerEntity = player;
        this.playerInventory = new InvWrapper(inventory);

        tileEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY).ifPresent(h -> {
            addSlot(new VEInsertSlot(h, 0, 70, 49)); // Air Compressor extract slot
            addSlot(new VEInsertSlot(h, 1, 154, -14)); // Upgrade Slot
        });
        layoutPlayerInventorySlots(8, 84);

        addDataSlot(new IntReferenceHolder() { // TrackInt is now addDataSlot
            @Override
            public int get() {
                return getEnergy();
            }

            @Override
            public void set(int value) {
                tileEntity.getCapability(CapabilityEnergy.ENERGY).ifPresent(h -> ((VEEnergyStorage)h).setEnergy(value));
            }
        });
    }

    public int getEnergy(){
        return tileEntity.getCapability(CapabilityEnergy.ENERGY).map(IEnergyStorage::getEnergyStored).orElse(0);
    }

    public int powerScreen(int px){
        int stored = tileEntity.getCapability(CapabilityEnergy.ENERGY).map(IEnergyStorage::getEnergyStored).orElse(0);
        int max = tileEntity.getCapability(CapabilityEnergy.ENERGY).map(IEnergyStorage::getMaxEnergyStored).orElse(0);
        int ret = (((stored*100/max*100)/100)*px)/100;
        return ret;
    }

    @Override
    public boolean stillValid(PlayerEntity playerIn) {
        return stillValid(IWorldPosCallable.create(tileEntity.getLevel(),tileEntity.getBlockPos()),playerEntity, VEBlocks.AIR_COMPRESSOR_BLOCK);
    }

    private void layoutPlayerInventorySlots(int leftCol, int topRow) {
        // Player inventory
        addSlotBox(playerInventory, 9, leftCol, topRow, 9, 18, 3, 18);

        // Hotbar
        topRow += 58;
        addSlotRange(playerInventory, 0, leftCol, topRow, 9, 18);
    }

    @Nonnull
    @Override
    public ItemStack quickMoveStack(final PlayerEntity player, final int index) {
        ItemStack returnStack = ItemStack.EMPTY;
        final Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            final ItemStack slotStack = slot.getItem();
            returnStack = slotStack.copy();

            final int containerSlots = this.slots.size() - player.inventory.items.size();
            if (index < containerSlots) {
                if (!moveItemStackTo(slotStack, containerSlots, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(slotStack, 0, containerSlots, false)) {
                return ItemStack.EMPTY;
            }
            if (slotStack.getCount() == 0) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (slotStack.getCount() == returnStack.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, slotStack);
        }
        return returnStack;
    }

    // Unauthorized call to this method can be dangerous. Can't not be public AFAIK. :(
    public void setAirCompressorScreen(AirCompressorScreen airCompressorScreen){
        this.airCompressorScreen = airCompressorScreen;
    }

    public void updateDirectionButton(int direction, int slotId){
        this.airCompressorScreen.updateButtonDirection(direction,slotId);
    }

    public void updateStatusButton(boolean status, int slotId){
        this.airCompressorScreen.updateBooleanButton(status, slotId);
    }

    public void updateStatusTank(boolean status, int id){
        this.airCompressorScreen.updateTankStatus(status, id);
    }

    public void updateDirectionTank(int direction, int id){
        this.airCompressorScreen.updateTankDirection(direction, id);
    }
}

