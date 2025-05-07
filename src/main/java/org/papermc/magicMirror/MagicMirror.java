package org.papermc.magicMirror;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class MagicMirror extends JavaPlugin implements Listener, CommandExecutor {

    private FileConfiguration config;
    private static final String playerDataKey = "player-data";
    private Map<UUID, List<BukkitTask>> warpingTasks = new HashMap<>();

    private String itemName = "";
    private int teleportWindup = 3;
    private boolean enableSounds  = true;
    private boolean enableParticles = true;
    private float soundVolume = 1.0f;
    private boolean enableMessages = true;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        readConfig();
        getLogger().info(String.format("Loaded configuration for %d players.", getPlayerDataCount()));
        Bukkit.getPluginManager().registerEvents(this, this);
        this.getCommand("sethome").setExecutor(this);
        this.getCommand("reloadmagicmirror").setExecutor(this);
    }

    @Override
    public void onDisable() {
        for (UUID uuid: warpingTasks.keySet()) {
            warpingTasks.get(uuid).forEach(BukkitTask::cancel);
        }
        warpingTasks.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();
        if (cmd.equals("reloadmagicmirror")) {
            reloadConfig();
            readConfig();
            sender.sendMessage("MagicMirror configuration reloaded.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return false;
        }
        UUID uuid = player.getUniqueId();
        Location loc = player.getLocation();

        // Store location in config
        config.set(String.format("%s.%s.home.world", playerDataKey, uuid), loc.getWorld().getName());
        config.set(String.format("%s.%s.home.x", playerDataKey, uuid), loc.getX());
        config.set(String.format("%s.%s.home.y", playerDataKey, uuid), loc.getY());
        config.set(String.format("%s.%s.home.z", playerDataKey, uuid), loc.getZ());
        config.set(String.format("%s.%s.home.yaw", playerDataKey, uuid), loc.getYaw());
        config.set(String.format("%s.%s.home.pitch", playerDataKey, uuid), loc.getPitch());

        saveConfig();

        player.sendMessage(
                Component.text("Home for player ")
                        .append(Component.text(player.getName(), NamedTextColor.GOLD))
                        .append(Component.text(" set!"))
        );
        return true;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (item.getType() != Material.RECOVERY_COMPASS) return;
        Action action = event.getAction();
        if (!(action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) return;

        if (!itemName.isEmpty()) {
            if (!item.hasItemMeta()) return;
            ItemMeta meta  = item.getItemMeta();
            if (!meta.hasCustomName()) return;
            String plainTextCustomName = PlainTextComponentSerializer.plainText().serialize(meta.customName());
            if (!plainTextCustomName.equals(itemName)) return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("magicmirror.use")) return;
        UUID uuid = player.getUniqueId();

        if (warpingTasks.containsKey(uuid)) {
            player.sendMessage(Component.text("You are already warping home.", NamedTextColor.DARK_RED));
            return;
        }

        Location home = getWarpLocation(player);

        if (teleportWindup == 0) {
            warpingTasks.put(player.getUniqueId(), new ArrayList<>());
            teleportPlayer(player, home);
        } else {
            if (enableMessages) player.sendMessage(Component.text(String.format("Warping home in %d...", teleportWindup), NamedTextColor.GREEN));
            if (enableSounds) player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, SoundCategory.PLAYERS, soundVolume, 0.5f);
            if (enableParticles) player.spawnParticle(Particle.GLOW, player.getLocation().clone().add(0, 1, 0), 100, 1, 1, 1, 1);
            BukkitScheduler scheduler = Bukkit.getScheduler();
            List<BukkitTask> tasks = new ArrayList<>();
            if (enableMessages || enableSounds) {
                for (int i = 1; i < teleportWindup; i++) {
                    Component message = Component.text(String.format("Warping home in %d...", teleportWindup - i), NamedTextColor.GREEN);
                    float pitch = 0.5f + (i / (float) teleportWindup) * 0.5f;
                    tasks.add(scheduler.runTaskLater(
                            this,
                            () -> {
                                if (enableSounds)
                                    player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, SoundCategory.PLAYERS, soundVolume, pitch);
                                if (enableMessages) player.sendMessage(message);
                            },
                            20L * i));
                }
            }
            tasks.add(scheduler.runTaskLater(
                    this,
                    () -> teleportPlayer(player, home),
                    20L * teleportWindup
            ));
            warpingTasks.put(player.getUniqueId(), tasks);
        }
    }

    @EventHandler
    public void onPlayerDeathEvent(PlayerDeathEvent event) {
        terminatePlayerWarp(event.getPlayer()); // remove player from warping players if they died during the windup
    }

    @EventHandler
    public void onPlayerQuitEvent(PlayerQuitEvent event) {
        terminatePlayerWarp(event.getPlayer()); // remove player from warping players if they quit during the windup
    }

    private void terminatePlayerWarp(Player player) {
        UUID uuid = player.getUniqueId();
        List<BukkitTask> tasks = warpingTasks.remove(uuid);
        if (tasks != null) {
            tasks.forEach(BukkitTask::cancel);
        }
    }

    private void teleportPlayer(Player player, Location loc) {
        UUID uuid = player.getUniqueId();
        List<BukkitTask> tasks = warpingTasks.remove(uuid);
        if (tasks == null) return; // Don't warp players that got terminated during the windup
        if (player.isDead()) return; // Don't warp currently dead players
        player.teleport(loc);
        if (enableSounds) loc.getWorld().playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, soundVolume, 1.0f);
        if (enableParticles) player.spawnParticle(Particle.GLOW, player.getLocation().clone().add(0, 1, 0), 100, 1, 1, 1, 1);
    }

    private int getPlayerDataCount() {
        ConfigurationSection playerSection = config.getConfigurationSection(playerDataKey);
        if (playerSection == null) {
            return 0;
        }
        return playerSection.getKeys(false).size();
    }

    private void readConfig() {
        config = this.getConfig();
        itemName = config.getString("item-name", "");
        teleportWindup = Math.max(0, config.getInt("windup", 3));
        enableParticles = config.getBoolean("enable-particles", true);
        enableSounds = config.getBoolean("enable-sounds", true);
        soundVolume = Math.clamp((float) config.getDouble("sound-effects-volume", 1.0), 0.0f, 1.0f);
        enableMessages = config.getBoolean("enable-messages", true);
    }

    private boolean isLocationVacant(Location loc) {
        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();
        return !feet.getType().isSolid() && !head.getType().isSolid();
    }

    private Location getWarpLocation(Player player) {
        Location fallback = player.getRespawnLocation();
        String fallbackDescription = "player spawn";
        if (fallback == null) {
            fallback = Bukkit.getWorlds().getFirst().getSpawnLocation();
            fallbackDescription = "world spawn";
        }

        String playerConfigPath = String.format("%s.%s", playerDataKey, player.getUniqueId());
        if (!config.contains(playerConfigPath + ".home")) {
            if (!config.getBoolean(playerConfigPath + ".setHomeMessageShown", false)) {
                player.sendMessage(Component.text(String.format("No home set. Use /sethome to set your home. Warping to %s instead.", fallbackDescription), NamedTextColor.YELLOW));
                config.set(playerConfigPath + ".setHomeMessageShown", true);
                saveConfig();
            }
            return fallback;
        }

        World world = Bukkit.getWorld(config.getString(playerConfigPath + ".home.world"));
        if (world == null) {
            player.sendMessage(Component.text(String.format("Your home world is deleted! Warping to %s instead.", fallbackDescription), NamedTextColor.YELLOW));
            return fallback;
        }

        Location home = new Location(
                world,
                config.getDouble(playerConfigPath + ".home.x"),
                config.getDouble(playerConfigPath + ".home.y"),
                config.getDouble(playerConfigPath + ".home.z"),
                (float) config.getDouble(playerConfigPath + ".home.yaw"),
                (float) config.getDouble(playerConfigPath + ".home.pitch")
        );
        if (!isLocationVacant(home)) {
            player.sendMessage(Component.text(String.format("Your home location is blocked. Warping to %s instead.", fallbackDescription), NamedTextColor.YELLOW));
            return fallback;
        }

        return home;
    }
}
