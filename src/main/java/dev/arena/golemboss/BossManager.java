package dev.arena.golemboss;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class BossManager implements Listener {

    private final GolemBossPlugin plugin;
    private final Random random = new Random();

    private final double MAX_HP;
    private final double BOSS_DMG;
    private final double P1_SCALE;
    private final double P2_SCALE;
    private final double P1_SPEED;
    private final double P2_SLOW;
    private final double P2_ATK_SLOW;
    private final int    BOSS_RADIUS;
    private final int    MINIONS;
    private final double MINION_HP;
    private final double MINION_DMG;
    private final double MINION_SCALE;
    private final double MINION_SPEED;
    private final double MINION_AGGRO;
    private final int    IRON_MIN_STACKS;
    private final int    IRON_MAX_STACKS;
    private final int    IRON_HIT_MIN;
    private final int    IRON_HIT_MAX;
    private final double PHASE2_TRIGGER;

    private final NamespacedKey BOSS_KEY;
    private final NamespacedKey MINION_KEY;

    private ActiveBoss active;
    private Scheduled summon;

    private static class Scheduled {
        World world; double x, y, z;
        long delayTicks;
        BukkitTask task;
    }

    private static class ActiveBoss {
        World world; double x, y, z;
        IronGolem entity;
        String phase = "1";
        double maxHp, hp;
        boolean phase1MinionsSpawned = false;
        int ironDropped = 0;
        final int ironTotal;
        final Map<UUID, Double> damage = new HashMap<>();
        final Set<LivingEntity> minions = new HashSet<>();
        boolean defeated = false;
        BossBar bar;
        BukkitTask barTask, particleTask, minionTask, aggroTask;

        ActiveBoss(int ironTotal) { this.ironTotal = ironTotal; }
    }

    public BossManager(GolemBossPlugin plugin) {
        this.plugin = plugin;
        var c = plugin.getConfig();

        MAX_HP      = c.getDouble("golem.max-hp", 1500.0);
        BOSS_DMG    = c.getDouble("golem.damage", 15.0);
        P1_SCALE    = c.getDouble("golem.phase1-scale", 1.35);
        P2_SCALE    = c.getDouble("golem.phase2-scale", 2.0);
        P1_SPEED    = c.getDouble("golem.phase1-speed", 0.25);
        P2_SLOW     = c.getDouble("golem.phase2-slowdown", 0.7);
        P2_ATK_SLOW = c.getDouble("golem.phase2-attack-slowdown", 0.55);
        BOSS_RADIUS = c.getInt("golem.bossbar-radius", 300);

        MINIONS      = c.getInt("minions.count", 5);
        MINION_HP    = c.getDouble("minions.hp", 60.0);
        MINION_DMG   = c.getDouble("minions.damage", 10.0);
        MINION_SCALE = c.getDouble("minions.scale", 0.7);
        MINION_SPEED = c.getDouble("minions.speed", 0.33);
        MINION_AGGRO = c.getDouble("minions.aggro-radius", 16.0);

        IRON_MIN_STACKS = c.getInt("loot.ingot-stacks-min", 5);
        IRON_MAX_STACKS = c.getInt("loot.ingot-stacks-max", 7);
        IRON_HIT_MIN    = c.getInt("loot.ingot-per-hit-min", 1);
        IRON_HIT_MAX    = c.getInt("loot.ingot-per-hit-max", 4);

        PHASE2_TRIGGER  = c.getDouble("phases.phase2-trigger", 0.5);

        BOSS_KEY   = new NamespacedKey(plugin, "golem_boss");
        MINION_KEY = new NamespacedKey(plugin, "golem_minion");
    }

    public boolean isScheduled() { return summon != null; }

    public boolean isActive() { return active != null && active.entity != null && !active.entity.isDead(); }

    /**
     * @param delayTicks задержка до появления (0 = сразу)
     */
    public void scheduleBoss(World world, double x, double y, double z, long delayTicks) {
        if (isActive()) { throw new IllegalStateException("ACTIVE"); }
        if (isScheduled()) { throw new IllegalStateException("SCHEDULED"); }

        summon = new Scheduled();
        summon.world = world; summon.x = x; summon.y = y; summon.z = z;
        summon.delayTicks = Math.max(0L, delayTicks);

        if (summon.delayTicks <= 0L) {
            spawnBoss();
            return;
        }

        announceSummoned(x, y, z, world, summon.delayTicks);
        summon.task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (summon != null) spawnBoss();
        }, summon.delayTicks);
    }

    public void cancelScheduled() {
        if (summon != null && summon.task != null) summon.task.cancel();
        summon = null;
        Msg.broadcast(Msg.GRAD + Msg.B + "Призыв голема отменён!</b></gradient>");
    }

    public void forceSpawn() {
        if (summon == null) return;
        if (summon.task != null) summon.task.cancel();
        spawnBoss();
    }

    private void spawnBoss() {
        if (summon == null) return;
        Scheduled s = summon;
        summon = null;

        int totalStacks = IRON_MIN_STACKS + random.nextInt(IRON_MAX_STACKS - IRON_MIN_STACKS + 1);
        ActiveBoss b = new ActiveBoss(totalStacks * 64);
        b.world = s.world; b.x = s.x; b.y = s.y; b.z = s.z;
        b.maxHp = MAX_HP; b.hp = MAX_HP;

        b.entity = spawnGolem(s.world, s.x, s.y, s.z, P1_SCALE, P1_SPEED, 4.0, "1", false);
        Attr.setMaxHealth(b.entity, MAX_HP);
        b.bar = Bukkit.createBossBar(Msg.legacy(bossTitle()), BarColor.PURPLE, BarStyle.SOLID);
        b.bar.setVisible(true);
        b.bar.setProgress(1.0);

        active = b;
        startTasks(b);
        updateBossBar(b);
        playAt(b.entity.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.6f);
        playAt(b.entity.getLocation(), Sound.ENTITY_IRON_GOLEM_REPAIR, 1.4f, 0.5f);

        Msg.broadcast(line());
        Msg.broadcast(Msg.GRAD + Msg.B + "        ✦   БОСС ПРИЗВАН   ✦</b></gradient>");
        Msg.broadcast(Msg.parse(Msg.DARK + "✦ </gradient><color:" + Msg.P5 + ">" + Msg.B + "Голем Разрушитель</b></color> вышел из глубин!</color>"));
        Msg.broadcast(Msg.parse(Msg.DARK + "✦ </gradient><color:" + Msg.P4 + ">Найдите его в точке </color><white><b>"
                + (int) b.x + " " + (int) b.y + " " + (int) b.z + "</b></white><color:" + Msg.P4 + "> (" + b.world.getName() + ")</color>"));
        Msg.broadcast(Msg.parse(Msg.DARK + "✦ </gradient><color:" + Msg.P4 + ">Здоровье: </color><gradient:#ffffff:#c084fc><b>" + (int) MAX_HP + "</b></gradient> "
                + "<color:#a78bfa>· Фаза 1</color>"));
        Msg.broadcast(line());
    }

    private String bossTitle() {
        return "<gradient:#7c3aed:#d8b4fe><b>✦ ГОЛЕМ РАЗРУШИТЕЛЬ ✦</b></gradient>";
    }

    private IronGolem spawnGolem(World w, double x, double y, double z, double scale, double speed, double atkSpeed, String tag, boolean cracked) {
        IronGolem g = w.spawn(new Location(w, x, y, z), IronGolem.class);
        g.setPersistent(true);
        g.setRemoveWhenFarAway(false);
        g.getPersistentDataContainer().set(BOSS_KEY, PersistentDataType.STRING, tag);
        g.customName(Msg.parse("<gradient:#7c3aed:#d8b4fe><b>✦ ГОЛЕМ РАЗРУШИТЕЛЬ ✦</b></gradient>"));
        g.setCustomNameVisible(true);
        g.setAI(true);
        g.setAware(true);
        // false = враждебен к игрокам (не «создан игроком»)
        g.setPlayerCreated(false);
        Attr.setScale(g, scale);
        Attr.setSpeed(g, speed);
        Attr.setAttackSpeed(g, atkSpeed);
        Attr.setAttackDamage(g, BOSS_DMG);
        if (cracked) {
            Attr.setMaxHealth(g, 4.0);
            g.setHealth(1.0);
        } else {
            Attr.setMaxHealth(g, MAX_HP);
        }
        Player first = nearestPlayer(g, BOSS_RADIUS);
        if (first != null) g.setTarget(first);
        return g;
    }

    private void startTasks(ActiveBoss b) {
        b.barTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> updateBossBar(b), 0L, 10L);
        if (b.minionTask == null) {
            b.minionTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                for (LivingEntity le : b.minions) {
                    if (le == null || le.isDead() || !le.isValid()) continue;
                    if (!(le instanceof Mob m)) continue;
                    LivingEntity target = nearestPlayer(m, MINION_AGGRO);
                    if (target != null && m.getTarget() != target) m.setTarget(target);
                }
            }, 0L, 10L);
        }
        if (b.aggroTask == null) {
            b.aggroTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                if (b.defeated || b.entity == null || b.entity.isDead()) return;
                LivingEntity target = nearestPlayer(b.entity, BOSS_RADIUS);
                if (target != null && b.entity.getTarget() != target) {
                    b.entity.setTarget(target);
                }
            }, 5L, 15L);
        }
    }

    private void startPhase2Tasks(ActiveBoss b) {
        if (b.particleTask == null) {
            b.particleTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                if (b.entity == null || b.entity.isDead()) return;
                Location l = b.entity.getLocation().add(0, b.entity.getHeight() / 2, 0);
                l.getWorld().spawnParticle(Particle.FLAME, l, 4, 1.2, 1.5, 1.2, 0.01);
                l.getWorld().spawnParticle(Particle.PORTAL, l, 6, 1.2, 1.5, 1.2, 0.5);
                l.getWorld().spawnParticle(Particle.END_ROD, l, 3, 1.0, 1.2, 1.0, 0.05);
            }, 0L, 2L);
        }
    }

    private LivingEntity nearestPlayer(LivingEntity origin, double range) {
        Player best = null; double bestD = range * range;
        World ow = origin.getWorld();
        Location ol = origin.getLocation();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead() || !p.isValid()) continue;
            if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            if (!sameWorld(p.getWorld(), ow)) continue;
            double d = p.getLocation().distanceSquared(ol);
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    private boolean sameWorld(World a, World b) {
        if (a == null || b == null) return false;
        return a.getUID().equals(b.getUID());
    }

    private void updateBossBar(ActiveBoss b) {
        if (b.defeated || b.entity == null || b.entity.isDead() || b.bar == null) return;
        Location bl = b.entity.getLocation();
        double progress = Math.max(0.0, Math.min(1.0, b.hp / b.maxHp));
        b.bar.setProgress(progress);
        b.bar.setVisible(true);

        Set<Player> in = Bukkit.getOnlinePlayers().stream()
                .filter(p -> sameWorld(p.getWorld(), bl.getWorld()))
                .filter(p -> p.getLocation().distanceSquared(bl) <= (double) BOSS_RADIUS * BOSS_RADIUS)
                .collect(Collectors.toSet());

        for (Player p : List.copyOf(b.bar.getPlayers())) {
            if (!in.contains(p)) b.bar.removePlayer(p);
        }
        for (Player p : in) {
            if (!b.bar.getPlayers().contains(p)) b.bar.addPlayer(p);
        }
    }

    private void spawnMinions(ActiveBoss b, Player attacker) {
        World w = b.entity.getWorld();
        int spawned = 0;
        for (int i = 0; i < MINIONS; i++) {
            IronGolem m = w.spawn(b.entity.getLocation().add(0, 1, 0), IronGolem.class);
            m.setPersistent(true);
            m.setRemoveWhenFarAway(false);
            m.getPersistentDataContainer().set(MINION_KEY, PersistentDataType.BOOLEAN, true);
            m.customName(Msg.parse("<color:#a78bfa><b>⚠ Големчик-страж</b></color>"));
            m.setCustomNameVisible(false);
            m.setAI(true);
            m.setAware(true);
            m.setPlayerCreated(false);
            Attr.setScale(m, MINION_SCALE);
            Attr.setSpeed(m, MINION_SPEED);
            Attr.setMaxHealth(m, MINION_HP);
            Attr.setAttackDamage(m, MINION_DMG);
            Player t = attacker != null ? attacker : (Player) nearestPlayer(m, MINION_AGGRO);
            if (t != null) m.setTarget(t);
            b.minions.add(m);
            spawned++;
        }
        if (spawned > 0) {
            playAt(b.entity.getLocation(), Sound.ENTITY_IRON_GOLEM_HURT, 1.0f, 1.4f);
            Msg.broadcast(Msg.GRAD + Msg.B + " ⚠ " + spawned + " Големчик-страж " + (spawned == 1 ? "появился" : "появились") + " из земли! ⚠</b></gradient>");
        }
    }

    private void onBossHit(Player attacker, double dmg) {
        ActiveBoss b = active;
        if (b == null || b.defeated) return;

        if (b.phase.equals("1") && !b.phase1MinionsSpawned) {
            b.phase1MinionsSpawned = true;
            Msg.broadcast(Msg.GRAD + Msg.B + " ⚔ " + attacker.getName() + " атакует Голема! Босс призывает стражей! ⚔</b></gradient>");
            spawnMinions(b, attacker);
        }

        b.damage.merge(attacker.getUniqueId(), dmg, Double::sum);
        b.hp -= dmg;

        playAt(b.entity.getLocation(), Sound.ENTITY_IRON_GOLEM_HURT, 1.1f, 0.7f);
        playAt(b.entity.getLocation(), Sound.ENTITY_GENERIC_HURT, 0.7f, 0.5f);

        int n = IRON_HIT_MIN + random.nextInt(IRON_HIT_MAX - IRON_HIT_MIN + 1);
        if (b.ironDropped + n > b.ironTotal) n = Math.max(0, b.ironTotal - b.ironDropped);
        if (n > 0) {
            b.entity.getWorld().dropItemNaturally(b.entity.getLocation(), new ItemStack(Material.IRON_INGOT, n));
            b.ironDropped += n;
        }

        if (b.phase.equals("1") && b.hp <= b.maxHp * PHASE2_TRIGGER) {
            startPhase2(b);
        }

        if (b.hp <= 0) {
            defeatBoss(b);
        } else {
            updateBossBar(b);
        }
    }

    private void startPhase2(ActiveBoss b) {
        b.phase = "2";
        Location loc = b.entity.getLocation();
        playAt(loc, Sound.ENTITY_WITHER_SPAWN, 1.6f, 0.45f);
        playAt(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.4f, 0.7f);
        playAt(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.6f);
        loc.getWorld().strikeLightningEffect(loc);

        b.entity.remove();
        b.entity = spawnGolem(loc.getWorld(), loc.getX(), loc.getY(), loc.getZ(), P2_SCALE, P1_SPEED * P2_SLOW, 4.0 * P2_ATK_SLOW, "2", true);

        startPhase2Tasks(b);
        spawnMinions(b, null);
        updateBossBar(b);

        Msg.broadcast(line());
        Msg.broadcast(Msg.GRAD + Msg.B + "      ✦   ФАЗА 2 — ГНЕВ ГОЛЕМА   ✦</b></gradient>");
        Msg.broadcast(Msg.parse(Msg.DARK + "✦ </gradient><color:" + Msg.P5 + ">" + Msg.B + "Голем Разрушитель</b></color> разъярён! Он увеличился и покрылся трещинами!</color>"));
        Msg.broadcast(Msg.parse(Msg.DARK + "✦ </gradient><color:" + Msg.P4 + ">Его удары теперь </color><gradient:#ff5555:#c084fc><b>уничтожают тотем бессмертия</b></gradient><color:" + Msg.P4 + ">!</color>"));
        Msg.broadcast(Msg.parse(Msg.DARK + "✦ </gradient><color:" + Msg.P4 + ">Скорость снижена, но он стал </color><color:#ff7b7b>" + Msg.B + "ещё опаснее</b></color><color:" + Msg.P4 + ">…</color>"));
        Msg.broadcast(Msg.parse(Msg.DARK + "✦ </gradient><color:" + Msg.P4 + ">Вокруг бушует </color><gradient:#ffb36b:#c084fc><b>огонь и фиолетовая магия</b></gradient><color:" + Msg.P4 + ">!</color>"));
        Msg.broadcast(line());
    }

    private void defeatBoss(ActiveBoss b) {
        if (b.defeated) return;
        b.defeated = true;

        int remain = b.ironTotal - b.ironDropped;
        if (remain > 0) {
            b.entity.getWorld().dropItemNaturally(b.entity.getLocation(), new ItemStack(Material.IRON_INGOT, remain));
            b.ironDropped = b.ironTotal;
        }

        for (LivingEntity m : b.minions) if (m != null && !m.isDead()) m.remove();
        b.minions.clear();

        List<Map.Entry<UUID, Double>> top = b.damage.entrySet().stream()
                .sorted((x, y) -> Double.compare(y.getValue(), x.getValue()))
                .collect(Collectors.toList());

        playAt(b.entity.getLocation(), Sound.ENTITY_IRON_GOLEM_DEATH, 1.4f, 0.6f);
        playAt(b.entity.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 0.8f);
        b.entity.getWorld().strikeLightningEffect(b.entity.getLocation());
        b.entity.remove();

        stopTasks(b);
        if (b.bar != null) b.bar.removeAll();
        active = null;

        Msg.broadcast(line());
        Msg.broadcast(Msg.GRAD + Msg.B + "      ✦   БОСС ПОВЕРЖЕН!   ✦</b></gradient>");
        Msg.broadcast(Msg.parse("<color:" + Msg.P4 + ">Голем Разрушитель повержен! Из него выпало <white><b>"
                + (b.ironDropped / 64) + " стак(ов)</b></white> железных слитков.</color>"));

        if (top.size() >= 1) {
            Player first = Bukkit.getPlayer(top.get(0).getKey());
            Msg.broadcast(Msg.parse(""));
            Msg.broadcast(Msg.parse(Msg.DARK + "★ </gradient><color:" + Msg.P5 + ">" + Msg.B + "🏆 ТОП ПО УРОНУ 🏆</b></color>"));
            announcePlace(1, top.get(0), "🏆", "<gold><b>МОЛОТ ГОЛЕМА</b></gold>", "<color:#fbbf24>выдаст АДМИН</color>");
            if (top.size() >= 2) {
                announcePlace(2, top.get(1), "🥈", "<light_purple><b>ДОНАТ НА 1 ВАЙП</b></light_purple>", "<color:#e879f9>выдаст АДМИН</color>");
            }
            for (int i = 2; i < Math.min(top.size(), 6); i++) {
                announceRest(i + 1, top.get(i));
            }
            if (first != null) {
                Msg.send(first, Msg.parse(Msg.GRAD + Msg.B + "      ✦   ВЫ — ПОБЕДИТЕЛЬ   ✦</b></gradient>"));
                Msg.send(first, Msg.parse(Msg.DARK + "✦ </gradient><color:" + Msg.P4 + ">Ваша награда: </color><gold><b>МОЛОТ ГОЛЕМА</b></gold> <color:#fbbf24>(выдаст админ)</color>"));
            }
        } else {
            Msg.broadcast(Msg.parse(Msg.DARK + "✦ </gradient><color:" + Msg.P4 + ">Победителей нет — босс пал без чести!</color>"));
        }
        Msg.broadcast(line());
    }

    private void announcePlace(int place, Map.Entry<UUID, Double> e, String medal, String prize, String byWhom) {
        Player p = Bukkit.getPlayer(e.getKey());
        String name = p != null ? p.getName() : "??";
        String placeWord = place == 1 ? "1 место" : "2 место";
        Msg.broadcast(Msg.parse(Msg.DARK + "✦ </gradient><color:" + Msg.P4 + ">" + medal + " " + Msg.B + placeWord + "</b></color> "
                + "<color:#ffffff>" + Msg.B + name + "</b></color> <color:#a78bfa>— урон </color><white><b>" + (int) Math.round(e.getValue()) + "</b></white>"));
        Msg.broadcast(Msg.parse(Msg.DARK + "    ➤ </gradient><color:" + Msg.P4 + ">Приз: </color>" + prize + " <color:#78716c>(" + byWhom + ")</color>"));
    }

    private void announceRest(int place, Map.Entry<UUID, Double> e) {
        Player p = Bukkit.getPlayer(e.getKey());
        String name = p != null ? p.getName() : "??";
        Msg.broadcast(Msg.parse(Msg.DARK + "✦ </gradient><color:" + Msg.P4 + ">• " + place + " место: </color><color:#ffffff>" + Msg.B + name
                + "</b></color> <color:#a78bfa>— урон </color><white><b>" + (int) Math.round(e.getValue()) + "</b></white>"));
    }

    private void stopTasks(ActiveBoss b) {
        if (b.barTask != null) b.barTask.cancel();
        if (b.particleTask != null) b.particleTask.cancel();
        if (b.minionTask != null) b.minionTask.cancel();
        if (b.aggroTask != null) b.aggroTask.cancel();
    }

    private void playAt(Location loc, Sound sound, float volume, float pitch) {
        if (loc == null || loc.getWorld() == null) return;
        loc.getWorld().playSound(loc, sound, SoundCategory.HOSTILE, volume, pitch);
    }

    /** Ванильный урон по боссу гасим, чтобы HP вёл плагин. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAnyBossDamage(EntityDamageEvent e) {
        if (!isBoss(e.getEntity())) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBossHitByPlayer(EntityDamageByEntityEvent e) {
        if (!isBoss(e.getEntity())) return;
        if (active == null || active.defeated) return;
        Player attacker = resolveDamager(e.getDamager());
        if (attacker == null) return;
        double dmg = e.getDamage();
        if (dmg <= 0) dmg = 1.0;
        onBossHit(attacker, dmg);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBossOrMinionAttacksPlayer(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (isBoss(e.getDamager())) {
            e.setCancelled(false);
            e.setDamage(BOSS_DMG);
            playAt(p.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.3f, 0.75f);
            playAt(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.9f);
            if (active != null && "2".equals(active.phase)) {
                playAt(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.3f);
                if (destroyTotem(p)) {
                    playAt(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.6f);
                    Msg.sendActionBar(p, "<color:#ff5555>" + Msg.B + "Тотем бессмертия уничтожен ударом Голема!</b></color>");
                }
            }
        } else if (isMinion(e.getDamager())) {
            e.setCancelled(false);
            e.setDamage(MINION_DMG);
            playAt(p.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 0.9f, 1.35f);
        }
    }

    private boolean destroyTotem(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it != null && it.getType() == Material.TOTEM_OF_UNDYING) {
                if (it.getAmount() > 1) { it.setAmount(it.getAmount() - 1); }
                else { p.getInventory().setItem(i, null); }
                return true;
            }
        }
        ItemStack off = p.getInventory().getItemInOffHand();
        if (off != null && off.getType() == Material.TOTEM_OF_UNDYING) {
            if (off.getAmount() > 1) off.setAmount(off.getAmount() - 1);
            else p.getInventory().setItemInOffHand(null);
            return true;
        }
        return false;
    }

    private Player resolveDamager(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    private boolean isBoss(Entity e) {
        return e != null && e.getPersistentDataContainer().has(BOSS_KEY, PersistentDataType.STRING);
    }

    private boolean isMinion(Entity e) {
        return e != null && e.getPersistentDataContainer().has(MINION_KEY, PersistentDataType.BOOLEAN);
    }

    private void announceSummoned(double x, double y, double z, World w, long ticks) {
        String wait = formatDelay(ticks);
        Msg.broadcast(line());
        Msg.broadcast(Msg.GRAD + Msg.B + "     ✦   ПРИЗЫВ ГОЛЕМА ЗАПЛАНИРОВАН   ✦</b></gradient>");
        Msg.broadcast(Msg.parse(Msg.DARK + "✦ </gradient><color:" + Msg.P4 + ">Легендарный </color><color:" + Msg.P5 + ">" + Msg.B + "Голем Разрушитель</b></color> будет призван через</color> <white><b>" + wait + "</b></white> <color:" + Msg.P4 + ">!</color>"));
        Msg.broadcast(Msg.parse(Msg.DARK + "✦ </gradient><color:" + Msg.P4 + ">Координаты призыва: </color><white><b>" + (int) x + " " + (int) y + " " + (int) z
                + "</b></white><color:" + Msg.P4 + ">  (" + w.getName() + ")</color>"));
        Msg.broadcast(Msg.parse(Msg.DARK + "✦ </gradient><color:" + Msg.P4 + ">Приготовьтесь к бою! У Голема <white><b>" + (int) MAX_HP + " HP</b></white> и две фазы.</color>"));
        Msg.broadcast(line());
    }

    static String formatDelay(long ticks) {
        long sec = Math.max(0, ticks / 20L);
        if (sec < 60) return sec + " сек.";
        long min = sec / 60;
        long rem = sec % 60;
        if (rem == 0) return min + " мин.";
        return min + " мин. " + rem + " сек.";
    }

    private Component line() {
        return Msg.line();
    }

    public void shutdown() {
        if (summon != null && summon.task != null) summon.task.cancel();
        if (active != null) stopTasks(active);
    }
}
