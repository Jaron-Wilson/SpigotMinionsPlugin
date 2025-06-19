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

        // Cancel any click in a minion inventory or storage
        if (event.getInventory().getHolder() instanceof Minion.MinionInventoryHolder ||
            event.getInventory().getHolder() instanceof MinionPlugin.StorageHolder ||
            event.getInventory().getHolder() instanceof TargetSelectHolder) {
            event.setCancelled(true);

            // Handle back button click
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.getType() == Material.BARRIER) {
                ItemMeta meta = clickedItem.getItemMeta();
                if (meta != null && meta.getDisplayName().equals(ChatColor.RED + "Back")) {
                    // If we're in the main menu, close the inventory
                    if (event.getView().getTitle().equals("Minion Control Panel")) {
                        player.closeInventory();
                        return;
                    }

                    // For other inventories, return to main menu
                    if (event.getInventory().getHolder() instanceof Minion.MinionInventoryHolder holder) {
                        UUID minionUUID = holder.getMinionUUID();
                        Entity entity = Bukkit.getServer().getEntity(minionUUID);
                        if (entity instanceof ArmorStand armorStand) {
                            player.openInventory(new Minion(plugin, armorStand).getActionInventory());
                        }
                    } else if (event.getInventory().getHolder() instanceof TargetSelectHolder holder) {
                        Entity entity = Bukkit.getServer().getEntity(holder.getMinionUUID());
                        if (entity instanceof ArmorStand armorStand) {
                            player.openInventory(new Minion(plugin, armorStand).getActionInventory());
                        }
                    } else if (event.getInventory().getHolder() instanceof MinionPlugin.StorageHolder storageHolder) {
                        Entity entity = Bukkit.getServer().getEntity(storageHolder.getMinionUUID());
                        if (entity instanceof ArmorStand armorStand) {
                            player.openInventory(new Minion(plugin, armorStand).getActionInventory());
                        }
                    }
                    return;
                }
            }

            // Handle collect buttons
            if (clickedItem != null && clickedItem.getType() == Material.HOPPER) {
                ItemMeta meta = clickedItem.getItemMeta();
                if (meta != null) {
                    String displayName = meta.getDisplayName();
                    if (displayName.equals(ChatColor.GREEN + "Collect All")) {
                        if (event.getInventory().getHolder() instanceof MinionPlugin.StorageHolder storageHolder) {
                            Entity entity = Bukkit.getServer().getEntity(storageHolder.getMinionUUID());
                            if (entity instanceof ArmorStand armorStand) {
                                Minion minion = new Minion(plugin, armorStand);
                                Inventory storage = plugin.getMinionStorage(armorStand.getUniqueId());
                                Inventory chestInv = minion.checkForChest(player.getWorld(), armorStand.getLocation());

                                CollectAllCommand collector = new CollectAllCommand(plugin);
                                int collected = collector.collectFromInventory(player, storage);
                                if (chestInv != null) {
                                    collected += collector.collectFromInventory(player, chestInv);
                                }

                                if (collected > 0) {
                                    player.sendMessage(ChatColor.GREEN + "Collected " + collected + " items!");
                                } else {
                                    player.sendMessage(ChatColor.YELLOW + "No items to collect!");
                                }
                                player.openInventory(minion.getMinionStorage());
                            }
                        }
                    } else if (displayName.equals(ChatColor.GREEN + "Collect from Chest")) {
                        if (event.getInventory().getHolder() instanceof MinionPlugin.StorageHolder storageHolder) {
                            Entity entity = Bukkit.getServer().getEntity(storageHolder.getMinionUUID());
                            if (entity instanceof ArmorStand armorStand) {
                                Minion minion = new Minion(plugin, armorStand);
                                Inventory chestInv = minion.checkForChest(player.getWorld(), armorStand.getLocation());

                                if (chestInv != null) {
                                    CollectAllCommand collector = new CollectAllCommand(plugin);
                                    int collected = collector.collectFromInventory(player, chestInv);
                                    if (collected > 0) {
                                        player.sendMessage(ChatColor.GREEN + "Collected " + collected + " items from chest!");
                                    } else {
                                        player.sendMessage(ChatColor.YELLOW + "No items to collect from chest!");
                                    }
                                } else {
                                    player.sendMessage(ChatColor.RED + "No chest found!");
                                }
                                player.openInventory(minion.getMinionStorage());
                            }
                        }
                    }
                }
            }
        }

        // Handle other minion inventory interactions
        if (event.getClickedInventory().getHolder() instanceof Minion.MinionInventoryHolder holder) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null) return;
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta == null || !meta.hasDisplayName()) return;
            String displayName = meta.getDisplayName();

            UUID minionUUID = holder.getMinionUUID();
            Entity entity = Bukkit.getServer().getEntity(minionUUID);

            if (!(entity instanceof ArmorStand armorStand)) {
                player.closeInventory();
                return;
            }

            Minion minion = new Minion(plugin, armorStand);

            if (displayName.equals(ChatColor.GOLD + "Mine Once")) {
                minion.mineBlock();
                player.openInventory(minion.getActionInventory());
            } else if (displayName.equals(ChatColor.BLUE + "Open Minion Storage")) {
                player.openInventory(minion.getMinionStorage());
            } else if (displayName.equals(ChatColor.RED + "Back")) {
                player.openInventory(minion.getActionInventory());
            } else if (displayName.equals(ChatColor.GREEN + "Upgrade Minion")) {
                int tier = armorStand.getPersistentDataContainer().getOrDefault(plugin.tierKey, PersistentDataType.INTEGER, 1);
                if (tier < 5) {
                    tier++;
                    armorStand.getPersistentDataContainer().set(plugin.tierKey, PersistentDataType.INTEGER, tier);
                    player.sendMessage(ChatColor.GREEN + "Upgraded minion to tier " + tier);
                } else {
                    player.sendMessage(ChatColor.RED + "Max tier reached!");
                }
                player.openInventory(minion.getActionInventory());
            } else if (displayName.equals(ChatColor.YELLOW + "Set Target") ||
                       displayName.startsWith(ChatColor.AQUA + "Target: ")) {
                Inventory targetSelect = Bukkit.createInventory(new TargetSelectHolder(minionUUID), 9, "Select Target Block");
                targetSelect.setItem(8, Minion.createBackButton());  // Add back button to target selection
                player.openInventory(targetSelect);
            }
        }

        // Handle bundle inventory clicks
        if (event.getInventory().getHolder() instanceof MinionBundleManager.BundleHolder) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null) return;

            if (clickedItem.getType() == Material.ARROW) {
                String displayName = clickedItem.getItemMeta().getDisplayName();
                if (displayName.equals(ChatColor.YELLOW + "Next Page")) {
                    plugin.getBundleManager().nextPage(player);
                } else if (displayName.equals(ChatColor.YELLOW + "Previous Page")) {
                    plugin.getBundleManager().previousPage(player);
                }
            } else if (clickedItem.getType() == Material.HOPPER &&
                      clickedItem.getItemMeta().getDisplayName().equals(ChatColor.GREEN + "Collect All")) {
                CollectAllCommand collector = new CollectAllCommand(plugin);
                collector.onCommand(player, null, "", new String[]{});
            }
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTargetSelect(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof TargetSelectHolder holder) {
            Player player = (Player) event.getWhoClicked();

            if (event.getAction() == InventoryAction.PLACE_ALL ||
                event.getAction() == InventoryAction.PLACE_ONE ||
                event.getAction() == InventoryAction.PLACE_SOME) {

                ItemStack cursor = event.getCursor();
                if (cursor != null && cursor.getType() != Material.AIR) {
                    Entity entity = Bukkit.getServer().getEntity(holder.getMinionUUID());
                    if (entity instanceof ArmorStand as) {
                        as.getPersistentDataContainer().set(plugin.targetKey,
                            PersistentDataType.STRING, cursor.getType().name());
                        as.setCustomName(ChatColor.YELLOW + cursor.getType().name() + "Minion");
                        player.sendMessage(ChatColor.GREEN + "Set minion target to " + cursor.getType().name());

                        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new Minion(plugin, as).getActionInventory()));
                    }
                }
            }
        }
    }

    private static class TargetSelectHolder implements InventoryHolder {
        private final UUID minionUUID;
        private final Inventory inventory;

        public TargetSelectHolder(UUID minionUUID) {
            this.minionUUID = minionUUID;
            this.inventory = Bukkit.createInventory(this, 9, "Select Target Block");
        }

        public UUID getMinionUUID() { return minionUUID; }

        @Override
        public Inventory getInventory() { return inventory; }
    }
}