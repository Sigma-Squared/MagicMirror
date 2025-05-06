package org.papermc.magicMirror;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
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

    @Override
    public void onEnable() {
        // Plugin startup logic
        this.saveDefaultConfig();
        readConfig();
        getLogger().info(String.format("Loaded %d homes.", getHomeCount()));
        Bukkit.getPluginManager().registerEvents(this, this);
        this.getCommand("sethome").setExecutor(this);
        this.getCommand("reloadmagicmirror").setExecutor(this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();
        if (cmd.equals("reloadmagicmirror")) {
            reloadConfig();
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

        if (!itemName.isEmpty()) {
            if (!item.hasItemMeta()) return;
            ItemMeta meta  = item.getItemMeta();
            if (!meta.hasCustomName()) return;
            if (!itemName.equals(meta.customName().toString())) return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (warpingPlayers.contains(uuid)) {
            player.sendMessage(Component.text("You are already teleporting.", NamedTextColor.DARK_RED));
            return;
        }

        Location target;
        String playerHomeConfigPath = String.format("%s.%s", homesKey, uuid);
        if (!config.contains(playerHomeConfigPath)) {
            player.sendMessage(Component.text("No home set. Use /sethome to set your home. Teleporting to world spawn.", NamedTextColor.YELLOW));
            World defaultWorld  = getServer().getWorlds().get(0);
            target = defaultWorld.getSpawnLocation();
        } else {
            target = new Location(
                    Bukkit.getWorld(config.getString(playerHomeConfigPath + ".world")),
                    config.getDouble(playerHomeConfigPath + ".x"),
                    config.getDouble(playerHomeConfigPath + ".y"),
                    config.getDouble(playerHomeConfigPath + ".z"),
                    (float) config.getDouble(playerHomeConfigPath + ".yaw"),
                    (float) config.getDouble(playerHomeConfigPath + ".pitch")
            );
        }

        if (enableSounds) player.playSound(target, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 1.0f);
        player.sendMessage(Component.text(String.format("Warping in %d...", teleportWindup), NamedTextColor.GREEN));
        BukkitScheduler scheduler = Bukkit.getScheduler();
        for (int i = 1; i < teleportWindup; i++) {
            int timeLeft = teleportWindup - i;
            scheduler.runTaskLater(
                    this,
                    () -> player.sendMessage(Component.text(String.format("Warping in %d...", timeLeft), NamedTextColor.GREEN)),
                    20L*i);
        }
        scheduler.runTaskLater(
                this,
                () -> teleportPlayerHome(player, target),
                20L*teleportWindup
        );
        warpingPlayers.add(uuid);
    }

    private void teleportPlayerHome(Player player, Location home) {
        UUID uuid = player.getUniqueId();
        warpingPlayers.remove(uuid);
        if (player.isDead()) return;
        player.teleport(home);
        World world = home.getWorld();
        if (enableSounds) world.playSound(home, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.0f);
        if (enableParticles) world.spawnParticle(Particle.SONIC_BOOM, home, 20, 0.5, 0.5, 0.5, 0.1);
    }

    private int getHomeCount() {
        ConfigurationSection homesSection = config.getConfigurationSection(homesKey);
        if (homesSection == null) return 0;
        return homesSection.getKeys(false).size();
    }

    private void readConfig() {
        config = this.getConfig();
        itemName = config.getString("item-name", "");
        teleportWindup = config.getInt("windup", 3);
        enableParticles = config.getBoolean("enable-particles", true);
        enableSounds = config.getBoolean("enable-sounds", true);
    }
}
