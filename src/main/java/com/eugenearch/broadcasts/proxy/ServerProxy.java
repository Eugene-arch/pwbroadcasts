package com.eugenearch.broadcasts.proxy;

import com.eugenearch.broadcasts.Reference;
import com.eugenearch.broadcasts.server.commands.CommandTournament;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

import java.io.IOException;

public class ServerProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {

        try {
            CommandTournament.INSTANCE.loadConfig(event.getModConfigurationDirectory().getPath() + "\\" + Reference.MOD_ID + ".json");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onCommandRegister(FMLServerStartingEvent event) {
        super.onCommandRegister(event);
        event.registerServerCommand(CommandTournament.INSTANCE);
    }
}
