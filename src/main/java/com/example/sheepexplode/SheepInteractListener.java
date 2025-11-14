package com.example.sheepexplode;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;


public class SheepInteractListener implements Listener {

    private final JavaPlugin plugin;

    // храним заряд каждой сущности по её UUID
    private final Map<UUID, Integer> chargeMap = new ConcurrentHashMap<>();
    // активные таймеры (если овца уже зажжена)
    private final Map<UUID, BukkitTask> activeTimers = new ConcurrentHashMap<>();

    public SheepInteractListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // При удалении/смерти сущности — очищаем карты и отменяем таймер
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity ent = event.getEntity();
        if (ent instanceof Sheep) {
            UUID id = ent.getUniqueId();
            chargeMap.remove(id);
            BukkitTask t = activeTimers.remove(id);
            if (t != null) t.cancel();
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // используем только основную руку
        if (event.getHand() != EquipmentSlot.HAND) return;

        Entity clicked = event.getRightClicked();
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        FileConfiguration cfg = plugin.getConfig();

        // читаем из config.yml (все значения есть по умолчанию)
        Material chargeItem = Material.valueOf(cfg.getString("charge-item", "TNT").toUpperCase());
        boolean chargeConsume = cfg.getBoolean("charge-consume", true);
        int maxCharges = cfg.getInt("max-charges", 5);

        Material igniteItem = Material.valueOf(cfg.getString("trigger-item", "FLINT_AND_STEEL").toUpperCase());
        String targetEntityName = cfg.getString("target-entity", "SHEEP").toUpperCase();

        int timerSeconds = cfg.getInt("timer-seconds", 3);
        final float basePower = (float) cfg.getDouble("base-power", 1.0);
        final float perChargePower = (float) cfg.getDouble("per-charge-power", 1.5);
        final boolean breakBlocks = cfg.getBoolean("break-blocks", true);
        final boolean setFire = cfg.getBoolean("set-fire", true);
        final double followRadius = cfg.getDouble("follow-radius", 20.0);
        final double speed = cfg.getDouble("follow-speed", 0.15);
        final double scalePerCharge = cfg.getDouble("scale-per-charge", 0.1);
        final String chargeItemLore = cfg.getString("charge-item-lore", "");

        // проверка существа по имени
        if (!clicked.getType().name().equalsIgnoreCase(targetEntityName)) return;
        if (!(clicked instanceof Sheep)) return;
        final Sheep sheep = (Sheep) clicked;
        final UUID sid = sheep.getUniqueId();

        // ------------- Зарядка (правый клик TNT) -------------
        if (item != null && item.getType() == chargeItem && item.getItemMeta().hasLore() && item.getItemMeta().getLore() != null && item.getItemMeta().getLore().contains(chargeItemLore)) {
            event.setCancelled(true);

            // Если овца уже в процессе отсчёта — нельзя заряжать
            if (activeTimers.containsKey(sid)) {
                player.sendMessage("§cЭта сущность уже активирована и не может быть дополнительно заряжена.");
                return;
            }

            int current = chargeMap.containsKey(sid) ? chargeMap.get(sid) : 0;
            if (current >= maxCharges) {
                player.sendMessage("§eОвца уже заряжена на максимум (" + maxCharges + ").");
                // эффект/звук, чтобы показать насыщенность
                sheep.getWorld().spawnParticle(Particle.LARGE_SMOKE, sheep.getLocation().add(0, 0.5, 0), 8, 0.2, 0.2, 0.2, 0.0);
                sheep.getWorld().playSound(sheep.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 1.2f);
                return;
            }

            // увеличиваем заряд
            int newCharge = current + 1;
            chargeMap.put(sid, newCharge);

            AttributeInstance scale = sheep.getAttribute(Attribute.SCALE);
            if (scale != null) {
                // размер = 1.0 + (заряды * 0.2)
                double newScale = 1.0 + (newCharge * scalePerCharge);
                scale.setBaseValue(newScale);
            }

            // расходуем TNT (не в Creative)
            if (chargeConsume && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                int amt = item.getAmount();
                if (amt <= 1) {
                    player.getInventory().setItemInMainHand(null);
                } else {
                    item.setAmount(amt - 1);
                }
            }

            // визуальная обратная связь
            sheep.getWorld().spawnParticle(Particle.FLAME, sheep.getLocation().add(0, 0.7, 0), 10, 0.2, 0.3, 0.2);
            sheep.getWorld().playSound(sheep.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

            // можно менять цвет шерсти зависимо от заряда (по кругу) — опционально
            // оставим белый/красный мигание при активации, но при зарядке покажем частицы

            player.sendMessage("§aЗаряд овцы: §f" + newCharge + "§a/" + maxCharges);
            return;
        }

        // ------------- Активация/воспламенение (правый клик Flint&Steel) -------------
        if (item != null && item.getType() == igniteItem) {
            event.setCancelled(true);

            // Если уже активирован таймер — не запускать второй
            if (activeTimers.containsKey(sid)) {
                player.sendMessage("§cЭта сущность уже активирована.");
                return;
            }

            final int charges = chargeMap.containsKey(sid) ? chargeMap.get(sid) : 0;
            // если нужно требовать хотя бы 1 заряд — можно проверить (опция)
            boolean requireCharge = cfg.getBoolean("require-charge-to-ignite", false);
            if (requireCharge && charges <= 0) {
                player.sendMessage("§cНельзя активировать: овца не заряжена.");
                return;
            }

            // уменьшение прочности/использование зажигалки можно реализовать, но опустим

            // звук и частицы при старте отсчёта
            Location startLoc = sheep.getLocation();
            sheep.getWorld().playSound(startLoc, Sound.ENTITY_CREEPER_PRIMED, 1.0f, 1.0f);
            sheep.getWorld().spawnParticle(Particle.SMOKE, startLoc.add(0, 0.5, 0), 15, 0.3, 0.5, 0.3, 0.05);

            // сохраняем параметры для взрыва
            final float finalPower = basePower + perChargePower * (float) charges;
            final int totalTicks = timerSeconds * 20;

            // Создаём таймер: мигание + движение + взрыв
            BukkitTask task = new BukkitRunnable() {
                int tick = 0;
                boolean red = false;

                @Override
                public void run() {
                    if (!sheep.isValid()) {
                        // если овца исчезла раньше — отменяем, очищаем заряд
                        chargeMap.remove(sid);
                        activeTimers.remove(sid);
                        cancel();
                        return;
                    }

                    // мигание
                    if (tick % 5 == 0) {
                        sheep.setColor(red ? DyeColor.WHITE : DyeColor.RED);
                        red = !red;
                    }

                    // найти ближайшего игрока
                    Player nearest = null;
                    double nearestDist = Double.MAX_VALUE;
                    for (Entity e : sheep.getNearbyEntities(followRadius, followRadius, followRadius)) {
                        if (e instanceof Player) {
                            Player p = (Player) e;
                            double dist = p.getLocation().distanceSquared(sheep.getLocation());
                            if (dist < nearestDist) {
                                nearestDist = dist;
                                nearest = p;
                            }
                        }
                    }

                    // движение естественным образом (через velocity) — не телепорт
                    if (nearest != null) {
                        Location sheepLoc = sheep.getLocation();
                        Vector direction = nearest.getLocation().toVector().subtract(sheepLoc.toVector());
                        direction.setY(0);
                        // если очень близко — не двигать слишком сильно
                        if (direction.lengthSquared() > 0.0001) {
                            direction.normalize().multiply(speed);
                            // проверяем под овцой: если нет земли — делаем небольшой отрицательный Y
                            Location locBelow = sheepLoc.clone();
                            locBelow.setY(locBelow.getY() - 1);
                            if (!locBelow.getBlock().getType().isSolid()) {
                                direction.setY(-0.12);
                            }
                            sheep.setVelocity(direction);
                        }
                    }

                    // дополнительные визуалы ближе к взрыву
                    int remaining = totalTicks - tick;
                    if (remaining <= 20) { // последние секундa
                        sheep.getWorld().spawnParticle(Particle.SMOKE, sheep.getLocation().add(0, 0.5, 0), 8, 0.2, 0.2, 0.2, 0.02);
                        sheep.getWorld().playSound(sheep.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 0.6f, 1.2f);
                    }

                    tick++;

                    if (tick >= totalTicks) {

                        // создаём взрыв с источником (sheep) — чтобы игроки получили урон
                        sheep.getWorld().createExplosion(sheep.getLocation(), finalPower, setFire, breakBlocks, sheep);
                        // убираем запись о заряде
                        chargeMap.remove(sid);
                        activeTimers.remove(sid);
                        // удаляем сущность (на всякий случай)
                        try {
                            sheep.remove();
                        } catch (Exception ex) {
                            // ignore
                        }
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, 0L, 1L);

            // сохраняем задачу
            activeTimers.put(sid, task);

            player.sendMessage("§aОвца активирована! Заряд: §f" + charges + " §a(мощность: " + finalPower + ")");
            return;
        }
    }
}
