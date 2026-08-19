package dev.arena.golemboss;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

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

        // /event golem info
        if (args.length >= 2 && args[1].equalsIgnoreCase("info")) {
            info(sender);
            return true;
        }

        // /event golem cancel
        if (args.length >= 2 && args[1].equalsIgnoreCase("cancel")) {
            if (!plugin.getBossManager().isScheduled()) {
                Msg.send(sender, Msg.parse("<color:#f87171>Сейчас нет запланированного призыва голема.</color>"));
                return true;
            }
            plugin.getBossManager().cancelScheduled();
            return true;
        }

        // /event golem spawn  — мгновенный призыв (для теста)
        if (args.length >= 2 && args[1].equalsIgnoreCase("spawn")) {
            if (!(sender instanceof Player)) {
                Msg.send(sender, Msg.parse("<red>Укажите координаты или используйте как игрок.</red>"));
                return true;
            }
            Player p = (Player) sender;
            plugin.getBossManager().scheduleBoss(p.getWorld(), p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ());
            return true;
        }

        // /event golem [x y z]
        double x, y, z;
        if (args.length >= 4) {
            try {
                x = Double.parseDouble(args[1]);
                y = Double.parseDouble(args[2]);
                z = Double.parseDouble(args[3]);
            } catch (NumberFormatException e) {
                Msg.send(sender, Msg.parse("<red>Неверные координаты. Пример: /event golem 100 64 -200</red>"));
                return true;
            }
            Location l = sender instanceof Player pl ? pl.getWorld().getBlockAt((int) x, (int) y, (int) z).getLocation() : null;
            if (l == null) {
                Msg.send(sender, Msg.parse("<red>Введите координаты или используйте команду как игрок.</red>"));
                return true;
            }
            plugin.getBossManager().scheduleBoss(l.getWorld(), l.getX(), l.getY(), l.getZ());
            return true;
        }

        // /event golem — свои координаты
        if (sender instanceof Player p) {
            Location l = p.getLocation();
            plugin.getBossManager().scheduleBoss(l.getWorld(), l.getX(), l.getY(), l.getZ());
            return true;
        }

        usage(sender);
        return true;
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
        Msg.send(sender, Msg.parse(Msg.DARK + "✦ </gradient><color:" + Msg.P4 + ">Спавн: </color><white><b>/event golem &lt;x&gt; &lt;y&gt; &lt;z&gt;</b></white>"));
        Msg.send(sender, Msg.line());
    }

    private void usage(CommandSender sender) {
        Msg.send(sender, Msg.line());
        Msg.send(sender, Msg.parse(Msg.GRAD + Msg.B + "       ✦   КОМАНДЫ ГОЛЕМА   ✦</b></gradient>"));
        Msg.send(sender, Msg.parse(Msg.DARK + "✦ </gradient><color:" + Msg.P4 + "><white><b>/event golem &lt;x&gt; &lt;y&gt; &lt;z&gt;</b></white> — призвать босса</color>"));
        Msg.send(sender, Msg.parse(Msg.DARK + "✦ </gradient><color:" + Msg.P4 + "><white><b>/event golem</b></white> — призвать на свои координаты</color>"));
        Msg.send(sender, Msg.parse(Msg.DARK + "✦ </gradient><color:" + Msg.P4 + "><white><b>/event golem cancel</b></white> — отменить призыв</color>"));
        Msg.send(sender, Msg.parse(Msg.DARK + "✦ </gradient><color:" + Msg.P4 + "><white><b>/event golem info</b></white> — статус босса</color>"));
        Msg.send(sender, Msg.line());
    }
}
