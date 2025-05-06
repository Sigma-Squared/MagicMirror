package org.papermc.magicMirror;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

import java.util.*;

public final class MagicMirror extends JavaPlugin implements Listener, CommandExecutor {

    private FileConfiguration config;
    private static final String homesKey = "homes";
    private Set<UUID> warpingPlayers = new HashSet<>();

    private String itemName = "";
    private int teleportWindup = 3;
    private boolean enableSounds  = true;
    private boolean enableParticles = true;
    private boolean defaultWorldSpawn = true;
    private float soundVolume = 1.0f;
    private boolean enableMessages = true;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        readConfig();
        getLogger().info(String.format("Loaded %d homes.", getHomeCount()));
        Bukkit.getPluginManager().registerEvents(this, this);
        this.getCommand("sethome").setExecutor(this);
        this.getCommand("reloadmagicmirror").setExecutor(this);
    }

    @Override
    public void onDisable() {
        warpingPlayers.clear();
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
        config.set(String.format("%s.%s.world", homesKey, uuid), loc.getWorld().getName());
        config.set(String.format("%s.%s.x",     homesKey, uuid), loc.getX());
        config.set(String.format("%s.%s.y",     homesKey, uuid), loc.getY());
        config.set(String.format("%s.%s.z",     homesKey, uuid), loc.getZ());
        config.set(String.format("%s.%s.yaw",   homesKey, uuid), loc.getYaw());
        config.set(String.format("%s.%s.pitch", homesKey, uuid), loc.getPitch());

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
            if (!meta.customName().equals(Component.text(itemName))) return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (warpingPlayers.contains(uuid)) {
            player.sendMessage(Component.text("You are already teleporting.", NamedTextColor.DARK_RED));
            return;
        }

        Location target = defaultWorldSpawn ? getServer().getWorlds().get(0).getSpawnLocation() : null; // target is world spawn by default
        String worldSpawnText = defaultWorldSpawn ? " Teleporting to world spawn instead." : "";
        String playerHomeConfigPath = String.format("%s.%s", homesKey, uuid);
        if (!config.contains(playerHomeConfigPath)) {
            player.sendMessage(Component.text(String.format("No home set. Use /sethome to set your home.%s", worldSpawnText), NamedTextColor.YELLOW));
        } else {
            World world = Bukkit.getWorld(config.getString(playerHomeConfigPath + ".world"));
            if (world == null) {
                player.sendMessage(Component.text(String.format("Your home world is deleted!%s", worldSpawnText), NamedTextColor.DARK_RED));
            } else {
                Location homeLoc = new Location(
                        world,
                        config.getDouble(playerHomeConfigPath + ".x"),
                        config.getDouble(playerHomeConfigPath + ".y"),
                        config.getDouble(playerHomeConfigPath + ".z"),
                        (float) config.getDouble(playerHomeConfigPath + ".yaw"),
                        (float) config.getDouble(playerHomeConfigPath + ".pitch")
                );
                if (isLocationSafe(homeLoc)) {
                    target = homeLoc;
                } else {
                    player.sendMessage(Component.text(String.format("Your home location is blocked.%s", worldSpawnText), NamedTextColor.DARK_RED));
                }
            }
        }

        if (target == null) return;

        warpingPlayers.add(uuid);
        if (teleportWindup == 0) {
            teleportPlayer(player, target);
        } else {
            if (enableMessages) player.sendMessage(Component.text(String.format("Warping home in %d...", teleportWindup), NamedTextColor.GREEN));
            if (enableSounds) player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, SoundCategory.PLAYERS, soundVolume, 0.5f);
            BukkitScheduler scheduler = Bukkit.getScheduler();
            if (enableMessages || enableSounds) {
                for (int i = 1; i < teleportWindup; i++) {
                    Component message = Component.text(String.format("Warping home in %d...", teleportWindup - i), NamedTextColor.GREEN);
                    float pitch = 0.5f + (i / (float) teleportWindup) * 0.5f;
                    scheduler.runTaskLater(
                            this,
                            () -> {
                                if (!warpingPlayers.contains(player.getUniqueId())) return; // no-op for players removed from the active warping
                                if (enableSounds)
                                    player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, SoundCategory.PLAYERS, soundVolume, pitch);
                                if (enableMessages) player.sendMessage(message);
                            },
                            20L * i);
                }
            }
            Location finalTarget = target;
            scheduler.runTaskLater(
                    this,
                    () -> teleportPlayer(player, finalTarget),
                    20L * teleportWindup
            );
        }
    }

    @EventHandler
    public void onPlayerDeathEvent(PlayerDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();
        warpingPlayers.remove(uuid); // remove player from warping players if they died during the windup
    }

    @EventHandler
    public void onPlayerQuitEvent(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        warpingPlayers.remove(uuid); // remove player from warping players if they quit during the windup
    }

    private void teleportPlayer(Player player, Location loc) {
        UUID uuid = player.getUniqueId();
        boolean shouldWarp = warpingPlayers.remove(uuid);
        if (!shouldWarp) return; // Don't warp players that died during the windup
        if (player.isDead()) return; // Don't warp currently dead players
        player.teleport(loc);
        World world = loc.getWorld();
        if (enableSounds) world.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, soundVolume, 1.0f);
        if (enableParticles) world.spawnParticle(Particle.SONIC_BOOM, loc.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
    }

    private int getHomeCount() {
        ConfigurationSection homesSection = config.getConfigurationSection(homesKey);
        if (homesSection == null) {
            getLogger().warning("homes configuration is null.");
            return 0;
        }
        return homesSection.getKeys(false).size();
    }

    private void readConfig() {
        config = this.getConfig();
        itemName = config.getString("item-name", "");
        teleportWindup = Math.max(0, config.getInt("windup", 3));
        enableParticles = config.getBoolean("enable-particles", true);
        enableSounds = config.getBoolean("enable-sounds", true);
        defaultWorldSpawn = config.getBoolean("default-world-spawn", true);
        soundVolume = Math.clamp((float) config.getDouble("sound-effects-volume", 1.0), 0.0f, 1.0f);
        enableMessages = config.getBoolean("enable-messages", true);
    }

    private boolean isLocationSafe(Location loc) {
        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();
        return !feet.getType().isSolid() && !head.getType().isSolid();
    }
}
