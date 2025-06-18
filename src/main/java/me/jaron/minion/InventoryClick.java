package me.jaron.minion;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

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
            } else if (displayName.startsWith(ChatColor.AQUA + "Target: ")) {
                // Allow target selection
                event.setCancelled(false);
                // Create new inventory for target selection
                Inventory selectInv = Bukkit.createInventory(new TargetSelectHolder(minionUUID), 9, "Select Target Block");
                player.openInventory(selectInv);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTargetSelect(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof TargetSelectHolder holder) {
            Player player = (Player) event.getWhoClicked();
            ItemStack clicked = event.getCurrentItem();

            // Don't cancel yet - let them place the item
            if (event.getAction() == InventoryAction.PLACE_ALL ||
                event.getAction() == InventoryAction.PLACE_ONE ||
                event.getAction() == InventoryAction.PLACE_SOME) {

                // Get the cursor item they're placing
                ItemStack cursor = event.getCursor();
                if (cursor != null && cursor.getType() != Material.AIR) {
                    // Get the minion and update its target
                    Entity entity = Bukkit.getServer().getEntity(holder.getMinionUUID());
                    if (entity instanceof ArmorStand as) {
                        // Update target and name
                        as.getPersistentDataContainer().set(plugin.targetKey,
                            PersistentDataType.STRING, cursor.getType().name());
                        as.setCustomName(ChatColor.YELLOW + cursor.getType().name() + "Minion");
                        player.sendMessage(ChatColor.GREEN + "Set minion target to " + cursor.getType().name());

                        // Schedule return to main menu next tick
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.openInventory(new Minion(plugin, as).getActionInventory());
                        });
                    }
                }
            }
         }
     }

    private static class TargetSelectHolder implements InventoryHolder {
        private final UUID minionUUID;
        public TargetSelectHolder(UUID minionUUID) { this.minionUUID = minionUUID; }
        public UUID getMinionUUID() { return minionUUID; }
        @Override
        public Inventory getInventory() { return null; }
    }
}