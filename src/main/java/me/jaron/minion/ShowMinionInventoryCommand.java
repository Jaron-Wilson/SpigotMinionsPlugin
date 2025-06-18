package me.jaron.minion;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

public class ShowMinionInventoryCommand implements CommandExecutor {
    private final MinionPlugin plugin;

    public ShowMinionInventoryCommand(MinionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        ArmorStand closestMinion = null;
        double closestDistance = Double.MAX_VALUE;

        for (ArmorStand armorStand : player.getWorld().getEntitiesByClass(ArmorStand.class)) {
            if (armorStand.getPersistentDataContainer().has(plugin.isMinionKey, PersistentDataType.BYTE)) {
                double distance = armorStand.getLocation().distanceSquared(player.getLocation());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestMinion = armorStand;
                }
            }
        }

        if (closestMinion != null) {
            Minion minion = new Minion(plugin, closestMinion);
            player.openInventory(minion.getActionInventory());
        } else {
            player.sendMessage(ChatColor.RED + "No minions found nearby.");
        }

        return true;
    }
}