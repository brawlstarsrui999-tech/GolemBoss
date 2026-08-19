package dev.arena.golemboss;

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
                Attribute a = Attribute.valueOf(n.toUpperCase(Locale.ROOT));
                AttributeInstance inst = e.getAttribute(a);
                if (inst != null) return inst;
            } catch (Throwable ignored) {
                // пробуем следующее имя
            }
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

    public static double getSpeed(LivingEntity e) {
        AttributeInstance a = find(e, "GENERIC_MOVEMENT_SPEED", "MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED");
        return a == null ? 0.25 : a.getValue();
    }
}
