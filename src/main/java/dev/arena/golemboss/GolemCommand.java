package dev.arena.golemboss;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GolemCommand implements CommandExecutor {

    private final GolemBossPlugin plugin;

    public GolemCommand(GolemBossPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("golem")) {
            usage(sender);
            return true;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("info")) {
            info(sender);
            return true;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("cancel")) {
            if (!plugin.getBossManager().isScheduled()) {
                Msg.send(sender, Msg.parse("<color:#f87171>Сейчас нет запланированного призыва голема.</color>"));
                return true;
            }
            plugin.getBossManager().cancelScheduled();
            return true;
        }

        // /event golem spawn — мгновенно на игроке
        if (args.length >= 2 && args[1].equalsIgnoreCase("spawn")) {
            if (!(sender instanceof Player p)) {
                Msg.send(sender, Msg.parse("<red>Укажите время и координаты: /event golem &lt;время&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt;</red>"));
                return true;
            }
            return trySchedule(sender, p.getWorld(), p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ(), 0L);
        }

        // /event golem <время> [x y z]
        // время: минуты (0 = сразу). Можно суффикс s/m: 30s, 5m
        if (args.length >= 2) {
            Long delayTicks = parseDelay(args[1]);
            if (delayTicks != null) {
                World world;
                double x, y, z;
                if (args.length >= 5) {
                    try {
                        x = Double.parseDouble(args[2]);
                        y = Double.parseDouble(args[3]);
                        z = Double.parseDouble(args[4]);
                    } catch (NumberFormatException e) {
                        Msg.send(sender, Msg.parse("<red>Неверные координаты. Пример: /event golem 5 100 64 -200</red>"));
                        return true;
                    }
                    if (sender instanceof Player pl) {
                        world = pl.getWorld();
                    } else {
                        Msg.send(sender, Msg.parse("<red>Из консоли укажите мир, выполняя команду от игрока.</red>"));
                        return true;
                    }
                } else if (sender instanceof Player p) {
                    Location l = p.getLocation();
                    world = l.getWorld();
                    x = l.getX(); y = l.getY(); z = l.getZ();
                } else {
                    Msg.send(sender, Msg.parse("<red>Укажите координаты: /event golem &lt;время&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt;</red>"));
                    return true;
                }
                return trySchedule(sender, world, x, y, z, delayTicks);
            }
        }

        // /event golem x y z  — сразу (время 0)
        if (args.length >= 4) {
            try {
                double x = Double.parseDouble(args[1]);
                double y = Double.parseDouble(args[2]);
                double z = Double.parseDouble(args[3]);
                if (!(sender instanceof Player p)) {
                    Msg.send(sender, Msg.parse("<red>Используйте как игрок или укажите время: /event golem 0 x y z</red>"));
                    return true;
                }
                return trySchedule(sender, p.getWorld(), x, y, z, 0L);
            } catch (NumberFormatException ignored) {
                Msg.send(sender, Msg.parse("<red>Неверные координаты. Пример: /event golem 0 100 64 -200</red>"));
                return true;
            }
        }

        if (sender instanceof Player p) {
            Location l = p.getLocation();
            return trySchedule(sender, l.getWorld(), l.getX(), l.getY(), l.getZ(), 0L);
        }

        usage(sender);
        return true;
    }

    private boolean trySchedule(CommandSender sender, World world, double x, double y, double z, long delayTicks) {
        try {
            plugin.getBossManager().scheduleBoss(world, x, y, z, delayTicks);
            if (delayTicks <= 0) {
                Msg.send(sender, Msg.parse("<color:#4ade80>Голем призван немедленно.</color>"));
            } else {
                Msg.send(sender, Msg.parse("<color:#4ade80>Призыв через " + BossManager.formatDelay(delayTicks) + ".</color>"));
            }
        } catch (IllegalStateException ex) {
            if ("ACTIVE".equals(ex.getMessage())) {
                Msg.send(sender, Msg.parse("<color:#f87171>Босс уже активен.</color>"));
            } else {
                Msg.send(sender, Msg.parse("<color:#f87171>Призыв уже запланирован. /event golem cancel</color>"));
            }
        }
        return true;
    }

    /**
     * Минуты по умолчанию. 0 = сразу. Суффиксы: 30s, 5m.
     */
    static Long parseDelay(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String s = raw.trim().toLowerCase();
        try {
            if (s.endsWith("s") && s.length() > 1) {
                double sec = Double.parseDouble(s.substring(0, s.length() - 1));
                return Math.round(sec * 20.0);
            }
            if (s.endsWith("m") && s.length() > 1) {
                double min = Double.parseDouble(s.substring(0, s.length() - 1));
                return Math.round(min * 60.0 * 20.0);
            }
            // чистое число = минуты
            double min = Double.parseDouble(s);
            return Math.round(min * 60.0 * 20.0);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void info(CommandSender sender) {
        BossManager m = plugin.getBossManager();
        Msg.send(sender, Msg.line());
        Msg.send(sender, Msg.parse(Msg.GRAD + Msg.B + "       ✦   СТАТУС ГОЛЕМА   ✦</b></gradient>"));
        if (m.isActive()) {
            Msg.send(sender, Msg.parse(Msg.DARK + "✦ </gradient><color:#4ade80>" + Msg.B + "Статус: БОСС АКТИВЕН</b></color>"));
        } else if (m.isScheduled()) {
            Msg.send(sender, Msg.parse(Msg.DARK + "✦ </gradient><color:#facc15>" + Msg.B + "Статус: ПРИЗЫВ ЗАПЛАНИРОВАН</b></color>"));
        } else {
            Msg.send(sender, Msg.parse(Msg.DARK + "✦ </gradient><color:#f87171>" + Msg.B + "Статус: НЕ АКТИВЕН</b></color>"));
        }
        Msg.send(sender, Msg.parse(Msg.DARK + "✦ </gradient><color:" + Msg.P4 + ">Спавн: </color><white><b>/event golem &lt;время&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt;</b></white>"));
        Msg.send(sender, Msg.line());
    }

    private void usage(CommandSender sender) {
        Msg.send(sender, Msg.line());
        Msg.send(sender, Msg.parse(Msg.GRAD + Msg.B + "       ✦   КОМАНДЫ ГОЛЕМА   ✦</b></gradient>"));
        Msg.send(sender, Msg.parse(Msg.DARK + "✦ </gradient><color:" + Msg.P4 + "><white><b>/event golem &lt;время&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt;</b></white> — призвать (время в минутах, 0 = сразу)</color>"));
        Msg.send(sender, Msg.parse(Msg.DARK + "✦ </gradient><color:" + Msg.P4 + "><white><b>/event golem 30s</b></white> — через 30 секунд на вас</color>"));
        Msg.send(sender, Msg.parse(Msg.DARK + "✦ </gradient><color:" + Msg.P4 + "><white><b>/event golem cancel</b></white> — отменить призыв</color>"));
        Msg.send(sender, Msg.parse(Msg.DARK + "✦ </gradient><color:" + Msg.P4 + "><white><b>/event golem info</b></white> — статус босса</color>"));
        Msg.send(sender, Msg.line());
    }
}
