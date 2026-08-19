package dev.arena.golemboss;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;

import java.util.Locale;

/**
 * Помощник работы с атрибутами. Перебирает возможные имена атрибутов,
 * чтобы код корректно работал на разных версиях Paper API.
 */
public final class Attr {

    private Attr() {}

    private static AttributeInstance find(LivingEntity e, String... names) {
        for (String n : names) {
            try {
                // Прямые константы Attribute — самый надёжный способ на Paper 1.21+
                Attribute attr = switch (n.toUpperCase(Locale.ROOT)) {
                    case "GENERIC_MAX_HEALTH", "MAX_HEALTH"          -> Attribute.MAX_HEALTH;
                    case "GENERIC_MOVEMENT_SPEED", "MOVEMENT_SPEED"  -> Attribute.MOVEMENT_SPEED;
                    case "GENERIC_ATTACK_DAMAGE", "ATTACK_DAMAGE"    -> Attribute.ATTACK_DAMAGE;
                    case "GENERIC_ATTACK_SPEED", "ATTACK_SPEED"      -> Attribute.ATTACK_SPEED;
                    case "GENERIC_FOLLOW_RANGE", "FOLLOW_RANGE"      -> Attribute.FOLLOW_RANGE;
                    case "GENERIC_SCALE", "SCALE"                    -> Attribute.SCALE;
                    default -> null;
                };
                if (attr != null) {
                    AttributeInstance inst = e.getAttribute(attr);
                    if (inst != null) return inst;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static void setMaxHealth(LivingEntity e, double v) {
        AttributeInstance a = find(e, "GENERIC_MAX_HEALTH", "MAX_HEALTH", "GENERIC_MAX_HEALTH");
        if (a != null) a.setBaseValue(v);
        e.setHealth(v);
    }

    public static void setScale(LivingEntity e, double v) {
        AttributeInstance a = find(e, "GENERIC_SCALE", "SCALE", "GENERIC_SCALE");
        if (a != null) a.setBaseValue(v);
    }

    public static void setSpeed(LivingEntity e, double v) {
        AttributeInstance a = find(e, "GENERIC_MOVEMENT_SPEED", "MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED");
        if (a != null) a.setBaseValue(v);
    }

    public static void setAttackSpeed(LivingEntity e, double v) {
        AttributeInstance a = find(e, "GENERIC_ATTACK_SPEED", "ATTACK_SPEED", "GENERIC_ATTACK_SPEED");
        if (a != null) a.setBaseValue(v);
    }

    public static void setAttackDamage(LivingEntity e, double v) {
        AttributeInstance a = find(e, "GENERIC_ATTACK_DAMAGE", "ATTACK_DAMAGE", "GENERIC_ATTACK_DAMAGE");
        if (a != null) a.setBaseValue(v);
    }

    public static void setFollowRange(LivingEntity e, double v) {
        AttributeInstance a = find(e, "FOLLOW_RANGE", "GENERIC_FOLLOW_RANGE");
        if (a != null) a.setBaseValue(v);
    }

    public static double getSpeed(LivingEntity e) {
        AttributeInstance a = find(e, "GENERIC_MOVEMENT_SPEED", "MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED");
        return a == null ? 0.25 : a.getValue();
    }
}
