package com.eugenearch.broadcasts.server.handlers;

import com.eugenearch.broadcasts.server.commands.CommandTournament;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod.EventBusSubscriber
public class ServerTickHandler {

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.START) return;

        World world = event.world;
        if (world == null) return;

        if ((int)world.getTotalWorldTime() % (1200 * CommandTournament.INSTANCE.DELAY_BETWEEN_MESSAGES_MINUTES) != 0) return;

        if (!world.getWorldInfo().getWorldName().equalsIgnoreCase("world")) return;

        MinecraftServer server = world.getMinecraftServer();
        if (server == null) return;


        ITextComponent message = new TextComponentString(CommandTournament.INSTANCE.getNextMessage());

        server.getPlayerList().sendMessage(message);
    }

}
