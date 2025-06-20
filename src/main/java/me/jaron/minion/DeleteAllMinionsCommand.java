package me.jaron.minion;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DeleteAllMinionsCommand implements CommandExecutor {
    private final MinionPlugin plugin;

    public DeleteAllMinionsCommand(MinionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        // Confirm if they want to delete all minions
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Warning: This will delete ALL your minions!");
            player.sendMessage(ChatColor.RED + "Type '/deleteallminions confirm' to continue.");
            return true;
        }

        if (!args[0].equalsIgnoreCase("confirm")) {
            player.sendMessage(ChatColor.RED + "Warning: This will delete ALL your minions!");
            player.sendMessage(ChatColor.RED + "Type '/deleteallminions confirm' to continue.");
            return true;
        }

        return deleteAllMinions(player);
    }

    // This method can also be called from the GUI if we add a "Delete All Minions" button
    public boolean deleteAllMinions(Player player) {
        UUID playerUuid = player.getUniqueId();
        List<Minion> minions = plugin.getMinions().get(playerUuid);

        if (minions == null || minions.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "You don't have any minions to delete.");
            return true;
        }

        int totalMinions = minions.size();
        int itemsTransferred = 0;

        // Create a copy of the list to avoid ConcurrentModificationException
        // when removing minions
        List<Minion> minionsCopy = new ArrayList<>(minions);

        for (Minion minion : minionsCopy) {
            // Get the minion's storage
            Inventory storage = plugin.getMinionStorage(minion.getUUID());

            if (storage != null) {
                // Transfer all items to bundle
                for (ItemStack item : storage.getContents()) {
                    if (item != null &&
                        item.getType() != org.bukkit.Material.BARRIER &&
                        item.getType() != org.bukkit.Material.HOPPER) {
                        plugin.getBundleManager().addItemToBundle(player, item.clone());
                        itemsTransferred++;
                    }
                }

                // Check for adjacent chest if required
                Inventory chestInv = minion.checkForChest(player.getWorld(), minion.getLocation());
                if (chestInv != null) {
                    // Transfer chest items too - first collect all items to avoid ConcurrentModificationException
                    List<ItemStack> chestItems = new ArrayList<>();
                    for (ItemStack item : chestInv.getContents()) {
                        if (item != null) {
                            chestItems.add(item.clone());
                            plugin.getBundleManager().addItemToBundle(player, item.clone());
                            itemsTransferred++;
                        }
                    }

                    // Now clear the chest inventory
                    chestInv.clear();
                }
            }

            // Get the ArmorStand entity - search in all worlds, not just player's world
            ArmorStand armorStand = null;
            for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
                // Since getEntity(UUID) is not available in older Bukkit versions,
                // iterate through all entities in the world and find by UUID
                for (Entity entity : world.getEntities()) {
                    if (entity instanceof ArmorStand && entity.getUniqueId().equals(minion.getUUID())) {
                        armorStand = (ArmorStand) entity;
                        break;
                    }
                }
                if (armorStand != null) break; // Exit outer loop if we found the entity
            }

            if (armorStand != null) {
                // Remove the minion
                plugin.removeMinion(player, armorStand);
            }
        }

        // Refresh the bundle view if it's open
        plugin.getBundleManager().refreshInventory(player);

        player.sendMessage(ChatColor.GREEN + "Successfully deleted " + totalMinions + " minions.");
        player.sendMessage(ChatColor.GREEN + "Transferred " + itemsTransferred + " items to your bundle.");

        return true;
    }
}
