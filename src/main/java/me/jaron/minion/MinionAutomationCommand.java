package me.jaron.minion;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

public class MinionAutomationCommand implements CommandExecutor {
    private final MinionPlugin plugin;

    public MinionAutomationCommand(MinionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can run this command.");
            return true;
        }
        // determine start or stop, default start
        boolean start = true;
        if (args.length > 0 && args[0].equalsIgnoreCase("stop")) start = false;
        // prevent redundant toggles
        boolean wasActive = plugin.isAutomationActive(player.getUniqueId());
        if (start && wasActive) {
            player.sendMessage(ChatColor.YELLOW + "Automation already running.");
            return true;
        }
        if (!start && !wasActive) {
            player.sendMessage(ChatColor.YELLOW + "Automation is not running.");
            return true;
        }
        // update global state
        plugin.setAutomationActive(player.getUniqueId(), start);
        int count = 0;
        for (Entity e : player.getWorld().getEntities()) {
            if (e instanceof ArmorStand as &&
                as.getPersistentDataContainer().has(plugin.isMinionKey, PersistentDataType.BYTE)) {
                String ownerStr = as.getPersistentDataContainer()
                    .get(plugin.ownerKey, PersistentDataType.STRING);
                if (ownerStr != null && ownerStr.equals(player.getUniqueId().toString())) {
                    Minion minion = new Minion(plugin, as);
                    if (start) minion.startAutomation(); else minion.stopAutomation();
                    count++;
                }
            }
        }
        player.sendMessage(ChatColor.GREEN + (start ? "Started" : "Stopped") +
            " automation for " + count + " minion(s).");
        return true;
    }
}
