package dev.arena.golemboss;

import org.bukkit.plugin.java.JavaPlugin;

public final class GolemBossPlugin extends JavaPlugin {

    private BossManager bossManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        bossManager = new BossManager(this);
        getServer().getPluginManager().registerEvents(bossManager, this);

        GolemCommand cmd = new GolemCommand(this);
        getCommand("event").setExecutor(cmd);

        getServer().getConsoleSender().sendMessage(Msg.parse(Msg.GRAD + Msg.B
                + " ✦  GolemBoss включён! Команда: /event golem <x> <y> <z>  ✦</b></gradient>"));
    }

    @Override
    public void onDisable() {
        if (bossManager != null) bossManager.shutdown();
    }

    public BossManager getBossManager() {
        return bossManager;
    }
}
