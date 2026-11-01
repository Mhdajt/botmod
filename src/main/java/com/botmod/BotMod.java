package com.botmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class BotMod implements ModInitializer {
    public static final String ID = "botmod";

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((d, access, env) -> BotCommands.register(d));
        ServerTickEvents.END_SERVER_TICK.register(BotManager::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> BotManager.clear());
    }
}
