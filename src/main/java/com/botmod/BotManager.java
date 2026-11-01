package com.botmod;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class BotManager {
    // ---- tunables (change via /bots set, or edit defaults here) ----
    public static int spawnPerTick = 4;       // join rate, protects the server from login spikes
    public static int brainBudgetPerTick = 400; // max bot brains run per server tick (round-robin)
    public static Formation formation = Formation.WEDGE;
    public static double spacing = 2.5;
    public static boolean hold = false;       // true = stay in formation, don't chase
    public static UUID anchorOwner = null;    // formation follows this player
    public static UUID targetPlayer = null;   // explicit player target
    public static boolean aggroMobs = true;   // attack nearby hostile mobs when no player target
    public static double retreatHp = 8, resumeHp = 16;

    private record Pending(ServerLevel level, String name, Vec3 pos, float yaw) {}
    private static final ConcurrentLinkedQueue<Pending> queue = new ConcurrentLinkedQueue<>();
    public static final List<BotPlayer> bots = new ArrayList<>();
    private static int cursor = 0;

    public static void queueSpawn(ServerLevel level, String prefix, int count, Vec3 pos, float yaw) {
        int start = bots.size() + queue.size();
        for (int i = 0; i < count; i++) queue.add(new Pending(level, prefix + (start + i), pos, yaw));
    }

    public static int pendingCount() { return queue.size(); }

    public static void tick(MinecraftServer server) {
        // 1) staged spawning
        for (int i = 0; i < spawnPerTick; i++) {
            Pending p = queue.poll();
            if (p == null) break;
            spawn(server, p);
        }
        // 2) brains, round-robin with a per-tick budget
        bots.removeIf(b -> b.isRemoved() || b.hasDisconnected());
        int n = bots.size();
        if (n == 0) return;
        ServerPlayer anchor = anchorOwner == null ? null : server.getPlayerList().getPlayer(anchorOwner);
        for (int k = 0; k < Math.min(brainBudgetPerTick, n); k++) {
            cursor = (cursor + 1) % n;
            BotPlayer b = bots.get(cursor);
            b.formationIndex = cursor;
            b.brain.tick(n, anchor);
        }
    }

    private static void spawn(MinecraftServer server, Pending p) {
        GameProfile profile = new GameProfile(
                UUID.nameUUIDFromBytes(("OfflinePlayer:" + p.name).getBytes()), p.name);
        BotConnection conn = new BotConnection();
        BotPlayer bot = new BotPlayer(server, p.level, profile, conn);
        bot.setPos(p.pos.x, p.pos.y, p.pos.z);
        bot.setYRot(p.yaw);
        server.getPlayerList().placeNewPlayer(conn, bot, CommonListenerCookie.createInitial(profile, false));
        bot.setPos(p.pos.x, p.pos.y, p.pos.z);
        bots.add(bot);
    }

    public static int removeAll(MinecraftServer server) {
        int c = bots.size();
        for (BotPlayer b : new ArrayList<>(bots)) server.getPlayerList().remove(b);
        bots.clear();
        queue.clear();
        return c;
    }

    public static void clear() { bots.clear(); queue.clear(); }
}
