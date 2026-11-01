package com.botmod;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.function.Predicate;

/** Priority-based combat brain. Cheap enough to run for thousands of bots. */
public class BotBrain {
    private enum State { FORMATION, ENGAGE, RETREAT, HEAL }

    private final BotPlayer bot;
    private State state = State.FORMATION;
    private LivingEntity target;
    private int lastBrainTick = 0;
    private int usingUntil = 0;

    public BotBrain(BotPlayer bot) { this.bot = bot; }

    public void tick(int totalBots, ServerPlayer anchor) {
        int dt = Math.max(1, bot.tickCount - lastBrainTick);
        lastBrainTick = bot.tickCount;

        manageTotem();
        if (bot.isUsingItem() && bot.tickCount < usingUntil) { stepPhysics(Vec3.ZERO, dt); return; } // finish eating/drinking

        float hp = bot.getHealth();
        target = pickTarget();

        // ---- state selection ----
        if (hp <= BotManager.retreatHp && (state != State.HEAL)) state = State.RETREAT;
        if (state == State.RETREAT || state == State.HEAL) {
            if (hp >= BotManager.resumeHp) state = target != null ? State.ENGAGE : State.FORMATION;
        } else {
            state = target != null && !BotManager.hold ? State.ENGAGE : State.FORMATION;
        }

        switch (state) {
            case RETREAT -> retreat(dt);
            case HEAL -> heal(dt);
            case ENGAGE -> engage(dt);
            default -> formation(totalBots, anchor, dt);
        }
    }

    // ------------------------------------------------------------------ targeting
    private LivingEntity pickTarget() {
        if (BotManager.targetPlayer != null) {
            ServerPlayer p = bot.level().getServer().getPlayerList().getPlayer(BotManager.targetPlayer);
            if (p != null && p.isAlive() && p.level() == bot.level() && !(p instanceof BotPlayer)) return p;
        }
        if (!BotManager.aggroMobs) return null;
        if (target != null && target.isAlive() && target.distanceToSqr(bot) < 32 * 32) return target;
        AABB box = bot.getBoundingBox().inflate(16);
        return bot.level().getEntitiesOfClass(Monster.class, box, Monster::isAlive).stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(bot))).orElse(null);
    }

    // ------------------------------------------------------------------ behaviours
    private void engage(int dt) {
        if (target == null) return;
        // Heal-on-the-fly if moderately hurt and a quick option exists
        if (bot.getHealth() <= BotManager.retreatHp + 4 && drinkInstantHealing()) return;

        double dist = bot.distanceTo(target);
        look(target.getEyePosition());

        boolean ready = bot.getAttackStrengthScale(0.5f) >= 1.0f;
        boolean hasShield = findSlot(s -> s.is(Items.SHIELD)) >= 0;

        // Shield up while cooling down and the enemy is close
        if (!ready && dist < 5 && hasShield) {
            select(findSlot(s -> s.is(Items.SHIELD)));
            if (!bot.isUsingItem()) bot.startUsingItem(InteractionHand.MAIN_HAND);
        } else {
            if (bot.isUsingItem() && bot.getUseItem().is(Items.SHIELD)) bot.stopUsingItem();
            // Disable enemy shield with an axe
            boolean enemyBlocking = target.isBlocking();
            int weapon = enemyBlocking ? findSlot(s -> s.is(ItemTags.AXES)) : findSlot(s -> s.is(ItemTags.SWORDS));
            if (weapon < 0) weapon = findSlot(s -> s.is(ItemTags.AXES) || s.is(ItemTags.SWORDS));
            select(weapon);
            if (dist <= 3.0 && ready) {
                bot.attack(target);
                bot.swing(InteractionHand.MAIN_HAND);
            }
        }

        // Movement: close in to ~2.5 blocks, strafe a bit
        Vec3 to = target.position().subtract(bot.position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        Vec3 dir = flat.lengthSqr() > 1e-4 ? flat.normalize() : Vec3.ZERO;
        Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(Math.sin(bot.tickCount * 0.15) * 0.5);
        Vec3 move = dist > 2.5 ? dir.add(side) : side;
        stepPhysics(move.lengthSqr() > 0 ? move.normalize().scale(speed()) : Vec3.ZERO, dt);
    }

    private void retreat(int dt) {
        LivingEntity threat = target != null ? target : nearestThreat();
        if (bot.isUsingItem() && bot.getUseItem().is(Items.SHIELD)) bot.stopUsingItem();
        if (threat == null || bot.distanceTo(threat) > 9) { state = State.HEAL; return; }
        Vec3 away = bot.position().subtract(threat.position());
        Vec3 flat = new Vec3(away.x, 0, away.z);
        Vec3 dir = flat.lengthSqr() > 1e-4 ? flat.normalize() : new Vec3(1, 0, 0);
        look(bot.getEyePosition().add(dir.scale(5)));
        // Emergency: drink instant health even while fleeing if very low
        if (bot.getHealth() <= 5 && drinkInstantHealing()) return;
        stepPhysics(dir.scale(speed()), dt);
    }

    private void heal(int dt) {
        stepPhysics(Vec3.ZERO, dt);
        if (drinkInstantHealing()) return;
        int gapple = findSlot(s -> s.is(Items.ENCHANTED_GOLDEN_APPLE));
        if (gapple < 0) gapple = findSlot(s -> s.is(Items.GOLDEN_APPLE));
        if (gapple >= 0) { select(gapple); consume(); return; }
        int regen = findSlot(s -> isPotion(s, Items.POTION, Potions.REGENERATION) || isPotion(s, Items.POTION, Potions.STRONG_REGENERATION));
        if (regen >= 0) { select(regen); consume(); return; }
        // nothing to heal with: natural regen, stand still until resume threshold
    }

    private void formation(int total, ServerPlayer anchor, int dt) {
        if (bot.isUsingItem() && bot.getUseItem().is(Items.SHIELD)) bot.stopUsingItem();
        useBuffPotions();
        Vec3 anchorPos = anchor != null ? anchor.position() : bot.position();
        float yaw = anchor != null ? anchor.getYRot() : bot.getYRot();
        Vec3 slot = BotManager.formation.slot(bot.formationIndex, total, BotManager.spacing, anchorPos, yaw);
        Vec3 to = slot.subtract(bot.position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        if (flat.length() < 0.6) { stepPhysics(Vec3.ZERO, dt); return; }
        look(bot.getEyePosition().add(flat.normalize().scale(4)));
        stepPhysics(flat.normalize().scale(Math.min(speed(), flat.length() / dt)), dt);
    }

    // ------------------------------------------------------------------ items
    private void manageTotem() {
        ItemStack off = bot.getItemInHand(InteractionHand.OFF_HAND);
        if (off.is(Items.TOTEM_OF_UNDYING)) return;
        int slot = findSlot(s -> s.is(Items.TOTEM_OF_UNDYING));
        if (slot < 0) return;
        ItemStack t = bot.getInventory().getItem(slot);
        bot.getInventory().setItem(slot, off);
        bot.setItemInHand(InteractionHand.OFF_HAND, t);
    }

    private boolean drinkInstantHealing() {
        int drink = findSlot(s -> isPotion(s, Items.POTION, Potions.HEALING) || isPotion(s, Items.POTION, Potions.STRONG_HEALING));
        if (drink >= 0) { select(drink); consume(); return true; }
        int splash = findSlot(s -> isPotion(s, Items.SPLASH_POTION, Potions.HEALING) || isPotion(s, Items.SPLASH_POTION, Potions.STRONG_HEALING));
        if (splash >= 0) { // throw at own feet
            select(splash);
            bot.setXRot(90);
            useHeld();
            return true;
        }
        return false;
    }

    private void useBuffPotions() {
        if (!bot.hasEffect(MobEffects.SPEED)) {
            int s = findSlot(s2 -> isPotion(s2, Items.POTION, Potions.SWIFTNESS) || isPotion(s2, Items.POTION, Potions.STRONG_SWIFTNESS));
            if (s >= 0) { select(s); consume(); return; }
        }
        if (!bot.hasEffect(MobEffects.STRENGTH)) {
            int s = findSlot(s2 -> isPotion(s2, Items.POTION, Potions.STRENGTH) || isPotion(s2, Items.POTION, Potions.STRONG_STRENGTH));
            if (s >= 0) { select(s); consume(); return; }
        }
        if (bot.isOnFire() && !bot.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            int s = findSlot(s2 -> isPotion(s2, Items.POTION, Potions.FIRE_RESISTANCE));
            if (s >= 0) { select(s); consume(); }
        }
    }

    private static boolean isPotion(ItemStack s, net.minecraft.world.item.Item type, net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> p) {
        if (!s.is(type)) return false;
        PotionContents pc = s.get(DataComponents.POTION_CONTENTS);
        return pc != null && pc.is(p);
    }

    private void consume() {
        useHeld();
        usingUntil = bot.tickCount + 40;
    }

    private void useHeld() {
        ItemStack held = bot.getItemInHand(InteractionHand.MAIN_HAND);
        if (held.isEmpty()) return;
        bot.gameMode.useItem(bot, bot.level(), held, InteractionHand.MAIN_HAND);
    }

    private int findSlot(Predicate<ItemStack> p) {
        var inv = bot.getInventory();
        for (int i = 0; i < 36; i++) if (p.test(inv.getItem(i))) return i;
        return -1;
    }

    /** Put inventory slot i into the main hand (swapping into the selected hotbar slot if needed). */
    private void select(int i) {
        if (i < 0) return;
        var inv = bot.getInventory();
        if (i < 9) { inv.setSelectedSlot(i); return; }
        int sel = inv.getSelectedSlot();
        ItemStack a = inv.getItem(i);
        inv.setItem(i, inv.getItem(sel));
        inv.setItem(sel, a);
    }

    // ------------------------------------------------------------------ movement helpers
    private double speed() { return bot.isSprinting() || true ? 0.22 : 0.15; } // blocks per tick (sprint-ish)

    private LivingEntity nearestThreat() {
        return bot.level().getEntitiesOfClass(Monster.class, bot.getBoundingBox().inflate(12), Monster::isAlive)
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(bot))).orElse(null);
    }

    private void look(Vec3 p) {
        Vec3 d = p.subtract(bot.getEyePosition());
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(d.y, h)));
        bot.setYRot(yaw); bot.setXRot(pitch); bot.setYHeadRot(yaw);
    }

    /** Own tiny physics so bots need no pathfinder or input packets. dt = server ticks since last brain run. */
    private void stepPhysics(Vec3 horiz, int dt) {
        for (int i = 0; i < dt; i++) {
            bot.vy = bot.onGround() ? 0 : (bot.vy - 0.08) * 0.98;
            Vec3 step = new Vec3(horiz.x, bot.vy, horiz.z);
            bot.move(MoverType.SELF, step);
            if (bot.horizontalCollision && bot.onGround()) bot.vy = 0.42; // hop over 1-block obstacles
        }
        bot.hurtMarked = true;
    }
}
