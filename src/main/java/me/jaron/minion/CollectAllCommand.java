package me.jaron.minion;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class CollectAllCommand implements CommandExecutor {
    private final MinionPlugin plugin;

    public CollectAllCommand(MinionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        // First check if player has a bundle
        boolean hasBundle = false;
        for (ItemStack invItem : player.getInventory().getContents()) {
            if (invItem != null && plugin.getBundleManager().isBundle(invItem)) {
                hasBundle = true;
                break;
            }
        }

        if (!hasBundle) {
            player.sendMessage(ChatColor.RED + "You need a Minion Collection Bundle! Use /getbundle to get one.");
            return true;
        }

        // Close the player's inventory to prevent title update errors
        player.closeInventory();

        int itemsCollected = collectAllForPlayer(player);

        if (itemsCollected > 0) {
            player.sendMessage(ChatColor.GREEN + "Collected " + itemsCollected + " items from all your minions!");
            // Reopen the bundle inventory after collecting
            Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.getBundleManager().refreshInventory(player)
            );
        } else {
            player.sendMessage(ChatColor.YELLOW + "No items to collect!");
        }

        return true;
    }

    public int collectAllForPlayer(Player player) {
        int itemsCollected = 0;
        for (Entity entity : player.getWorld().getEntities()) {
            if (!(entity instanceof ArmorStand armorStand)) continue;

            // Check if it's a minion and belongs to the player
            if (!armorStand.getPersistentDataContainer().has(plugin.isMinionKey, PersistentDataType.BYTE)) continue;
            String ownerUUID = armorStand.getPersistentDataContainer().get(plugin.ownerKey, PersistentDataType.STRING);
            if (ownerUUID == null || !player.getUniqueId().toString().equals(ownerUUID)) continue;

            // Get minion storage and connected chest
            Minion minion = new Minion(plugin, armorStand);
            Inventory storage = plugin.getMinionStorage(armorStand.getUniqueId());
            Inventory chestInv = minion.checkForChest(player.getWorld(), armorStand.getLocation());

            // Collect from storage
            if (storage != null) {
                itemsCollected += collectFromInventory(player, storage);
            }

            // Collect from chest
            if (chestInv != null) {
                itemsCollected += collectFromInventory(player, chestInv);
            }
        }
        return itemsCollected;
    }

    public int collectFromInventory(Player player, Inventory sourceInv) {
        int itemsCollected = 0;

        for (ItemStack item : sourceInv.getContents()) {
            if (item == null) continue;
            if (item.getType().isAir()) continue;
            if (item.getItemMeta() != null &&
                (item.getItemMeta().getDisplayName().equals(ChatColor.RED + "Back") ||
                 item.getItemMeta().getDisplayName().startsWith(ChatColor.GREEN + "Collect"))) continue;

            // Add directly to bundle
            plugin.getBundleManager().addItemToBundle(player, item.clone());
            itemsCollected += item.getAmount();
            sourceInv.remove(item);
        }
        return itemsCollected;
    }
}
