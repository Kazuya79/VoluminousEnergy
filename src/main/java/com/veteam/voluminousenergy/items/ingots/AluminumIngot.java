package com.veteam.voluminousenergy.items.ingots;

import com.veteam.voluminousenergy.setup.VESetup;
import net.minecraft.item.Item;

public class AluminumIngot extends Item {
    public AluminumIngot (){
        super(new Item.Properties()
                .stacksTo(64)
                .tab(VESetup.itemGroup)
        );
        setRegistryName("aluminum_ingot");
    }
}
