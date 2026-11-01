package com.botmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.Arrays;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class BotCommands {
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(literal("bots")
            // NOTE: permission API changed in 1.21.11+/26.x. If this line fails to compile, use the
            // permission helper in Commands (e.g. Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(literal("spawn").then(argument("count", IntegerArgumentType.integer(1, 20000))
                .executes(c -> spawn(c.getSource(), IntegerArgumentType.getInteger(c, "count"), "Bot"))
                .then(argument("prefix", StringArgumentType.word())
                    .executes(c -> spawn(c.getSource(), IntegerArgumentType.getInteger(c, "count"),
                            StringArgumentType.getString(c, "prefix"))))))
            .then(literal("remove").executes(c -> {
                int n = BotManager.removeAll(c.getSource().getServer());
                c.getSource().sendSuccess(() -> Component.literal("Removed " + n + " bots"), true);
                return n;
            }))
            .then(literal("formation")
                .then(argument("type", StringArgumentType.word())
                    .suggests((c, b) -> { for (Formation f : Formation.values()) b.suggest(f.name().toLowerCase()); return b.buildFuture(); })
                    .executes(c -> setFormation(c.getSource(), StringArgumentType.getString(c, "type"), BotManager.spacing))
                    .then(argument("spacing", DoubleArgumentType.doubleArg(1, 20))
                        .executes(c -> setFormation(c.getSource(), StringArgumentType.getString(c, "type"),
                                DoubleArgumentType.getDouble(c, "spacing"))))))
            .then(literal("follow").executes(c -> {
                BotManager.anchorOwner = c.getSource().getPlayerOrException().getUUID();
                c.getSource().sendSuccess(() -> Component.literal("Formation now follows you"), false);
                return 1;
            }))
            .then(literal("target")
                .then(argument("player", EntityArgument.player()).executes(c -> {
                    ServerPlayer p = EntityArgument.getPlayer(c, "player");
                    BotManager.targetPlayer = p.getUUID();
                    c.getSource().sendSuccess(() -> Component.literal("Target: " + p.getName().getString()), true);
                    return 1;
                }))
                .then(literal("none").executes(c -> { BotManager.targetPlayer = null; return 1; })))
            .then(literal("hold").then(argument("on", StringArgumentType.word()).executes(c -> {
                BotManager.hold = StringArgumentType.getString(c, "on").equalsIgnoreCase("on"); return 1; })))
            .then(literal("aggro").then(argument("on", StringArgumentType.word()).executes(c -> {
                BotManager.aggroMobs = StringArgumentType.getString(c, "on").equalsIgnoreCase("on"); return 1; })))
            .then(literal("kit").executes(c -> kit(c.getSource())))
            .then(literal("hp")
                .then(argument("retreat", DoubleArgumentType.doubleArg(1, 19))
                    .then(argument("resume", DoubleArgumentType.doubleArg(2, 20)).executes(c -> {
                        BotManager.retreatHp = DoubleArgumentType.getDouble(c, "retreat");
                        BotManager.resumeHp = DoubleArgumentType.getDouble(c, "resume");
                        return 1; }))))
            .then(literal("budget").then(argument("n", IntegerArgumentType.integer(10, 20000)).executes(c -> {
                BotManager.brainBudgetPerTick = IntegerArgumentType.getInteger(c, "n"); return 1; })))
            .then(literal("stats").executes(c -> {
                c.getSource().sendSuccess(() -> Component.literal("Bots: " + BotManager.bots.size()
                        + " | queued: " + BotManager.pendingCount()
                        + " | formation: " + BotManager.formation + " @" + BotManager.spacing
                        + " | brains/tick: " + BotManager.brainBudgetPerTick), false);
                return 1; }))
        );
    }

    private static int spawn(CommandSourceStack src, int count, String prefix) {
        var pos = src.getPosition();
        BotManager.queueSpawn(src.getLevel(), prefix, count, pos, src.getRotation().y);
        src.sendSuccess(() -> Component.literal("Queued " + count + " bots (" + BotManager.spawnPerTick + "/tick)"), true);
        return count;
    }

    private static int setFormation(CommandSourceStack src, String name, double spacing) {
        try {
            BotManager.formation = Formation.valueOf(name.toUpperCase());
            BotManager.spacing = spacing;
            src.sendSuccess(() -> Component.literal("Formation: " + BotManager.formation + ", spacing " + spacing), true);
            return 1;
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal("Unknown formation. Options: " + Arrays.toString(Formation.values())));
            return 0;
        }
    }

    /** Gives every bot a standard PvP kit (op-only command). */
    private static int kit(CommandSourceStack src) {
        for (BotPlayer b : BotManager.bots) {
            var inv = b.getInventory();
            inv.clearContent();
            inv.setItem(0, new ItemStack(Items.NETHERITE_SWORD));
            inv.setItem(1, new ItemStack(Items.NETHERITE_AXE));
            inv.setItem(2, new ItemStack(Items.SHIELD));
            inv.setItem(3, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 8));
            inv.setItem(4, potion(Items.POTION, Potions.STRONG_HEALING));
            inv.setItem(5, potion(Items.SPLASH_POTION, Potions.STRONG_HEALING));
            inv.setItem(6, potion(Items.POTION, Potions.STRONG_STRENGTH));
            inv.setItem(7, potion(Items.POTION, Potions.STRONG_SWIFTNESS));
            inv.setItem(9, new ItemStack(Items.TOTEM_OF_UNDYING, 4));
            inv.setItem(10, potion(Items.SPLASH_POTION, Potions.STRONG_HEALING));
            b.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            b.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
            b.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
            b.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
            b.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
        }
        src.sendSuccess(() -> Component.literal("Kitted " + BotManager.bots.size() + " bots"), true);
        return BotManager.bots.size();
    }

    private static ItemStack potion(net.minecraft.world.item.Item type, net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> p) {
        ItemStack s = new ItemStack(type);
        s.set(DataComponents.POTION_CONTENTS, new PotionContents(p));
        return s;
    }
}
