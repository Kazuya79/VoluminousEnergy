package com.veteam.voluminousenergy.items.ingots;

import com.veteam.voluminousenergy.setup.VESetup;
import net.minecraft.item.Item;

public class TitaniumIngot extends Item {
    public TitaniumIngot (){
        super(new Item.Properties()
                .stacksTo(64)
                .tab(VESetup.itemGroup)
        );
        setRegistryName("titanium_ingot");
    }
}
