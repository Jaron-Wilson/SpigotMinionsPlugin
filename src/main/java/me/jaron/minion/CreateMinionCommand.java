package me.jaron.minion;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class CreateMinionCommand implements CommandExecutor {

    private final MinionPlugin plugin;

    public CreateMinionCommand(MinionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can create minions.");
            return true;
        }

        // CORRECTED: Get the block the player is in and add 0.5 to X and Z to center it.
        Location spawnLocation = player.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
        spawnLocation.setYaw(player.getLocation().getYaw());

        plugin.spawnMinion(player, spawnLocation);
        return true;
    }


}