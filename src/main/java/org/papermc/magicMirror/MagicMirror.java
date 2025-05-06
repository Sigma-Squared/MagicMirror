package org.papermc.magicMirror;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class MagicMirror extends JavaPlugin implements Listener, CommandExecutor {

    private FileConfiguration config;
    private String itemName;
    private static final String homesKey = "homes";

    @Override
    public void onEnable() {
        // Plugin startup logic
        this.saveDefaultConfig();
        config = this.getConfig();
        getLogger().info(String.format("Loaded %d homes.", getHomeCount()));
        Bukkit.getPluginManager().registerEvents(this, this);
        this.getCommand("sethome").setExecutor(this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
        if (!item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) return;
        if (!meta.displayName().equals("Magic Mirror")) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

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

        // Teleport and play sound
        player.teleport(target);
        player.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }

    private int getHomeCount() {
        ConfigurationSection homesSection = config.getConfigurationSection(homesKey);
        if (homesSection == null) {
            return 0;
        }
        // Count direct child keys (UUIDs)
        return homesSection.getKeys(false).size();
    }
}
