package dev.arena.golemboss;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Утилита красивых пурпурных сообщений на базе Adventure MiniMessage.
 */
public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // Пурпурная гамма
    public static final String P1  = "#4c1d95";   // тёмный пурпур
    public static final String P2  = "#7c3aed";   // фиолетовый
    public static final String P3  = "#a855f7";   // средний пурпур
    public static final String P4  = "#c084fc";   // светлый пурпур
    public static final String P5  = "#e9d5ff";   // почти белый пурпур
    public static final String SOFT = "#a78bfa";  // мягкий лавандовый

    public static final String GRAD = "<gradient:" + P2 + ":" + P4 + ">";
    public static final String DARK = "<gradient:" + P1 + ":" + P3 + ">";
    public static final String B = "<b>";

    private Msg() {}

    /** Преобразует MiniMessage-строку в Component. */
    public static Component parse(String mm) {
        return MM.deserialize(mm);
    }

    /** Широкая разделительная линия в пурпурном градиенте. */
    public static Component line() {
        return parse(GRAD + B + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    /** Тонкая линия. */
    public static Component thin() {
        return parse(GRAD + "· · · · · · · · · · · · · · · · · · · · · · · ·");
    }

    /** Яркое (белое) жирное сообщение. */
    public static Component accent(String text) {
        return parse("<color:#ffffff>" + B + text + "</b></color>");
    }

    public static void broadcast(String mm) {
        Bukkit.broadcast(parse(mm));
    }

    public static void broadcast(Component c) {
        Bukkit.broadcast(c);
    }

    public static void send(CommandSender target, String mm) {
        target.sendMessage(parse(mm));
    }

    public static void send(CommandSender target, Component c) {
        target.sendMessage(c);
    }

    public static void sendActionBar(Player p, String mm) {
        p.sendActionBar(parse(mm));
    }

    public static String legacy(String mm) {
        return LegacyComponentSerializer.legacySection().serialize(parse(mm));
    }
}