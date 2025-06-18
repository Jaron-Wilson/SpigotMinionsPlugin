package me.jaron.minion;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

public class InventoryClick implements Listener {

    private final MinionPlugin plugin;

    public InventoryClick(MinionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;

        if (event.getClickedInventory().getHolder() instanceof Minion.MinionInventoryHolder holder) {
            event.setCancelled(true);

            UUID minionUUID = holder.getMinionUUID();
            Entity entity = Bukkit.getServer().getEntity(minionUUID);

            if (!(entity instanceof ArmorStand armorStand)) {
                player.closeInventory();
                return;
            }

            Minion minion = new Minion(plugin, armorStand);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null) return;
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta == null || !meta.hasDisplayName()) return;
            String displayName = meta.getDisplayName();

            if (displayName.equals(ChatColor.GOLD + "Mine Once")) {
                minion.mineBlock();
                player.openInventory(minion.getActionInventory());
            } else if (displayName.equals(ChatColor.GREEN + "Plant Once")) {
                minion.plantBlock();
                player.openInventory(minion.getActionInventory());
            } else if (displayName.startsWith(ChatColor.YELLOW + "Mode: ")) {
                minion.toggleMode();
                player.openInventory(minion.getActionInventory());
            } else if (displayName.equals(ChatColor.BLUE + "Open Minion Storage")) {
                Inventory storage = plugin.getMinionStorage(minionUUID);
                player.openInventory(storage);
            }
        }
    }
}