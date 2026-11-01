package com.botmod;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class BotPlayer extends ServerPlayer {
    public final BotConnection conn;
    public final BotBrain brain = new BotBrain(this);
    public int formationIndex = 0;
    public Vec3 vel = Vec3.ZERO;
    public double vy = 0;

    public BotPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, BotConnection conn) {
        super(server, level, profile, ClientInformation.createDefault());
        this.conn = conn;
    }

    @Override
    public void tick() {
        super.tick();
        this.doTick(); // normally driven by the network listener, which bots don't have
        if (tickCount % 40 == 0) conn.drain();
    }
}
