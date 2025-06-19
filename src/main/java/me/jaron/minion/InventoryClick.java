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

    // ConfirmationHolder for the target selection confirmation GUI
    public static class ConfirmationHolder implements InventoryHolder {
        private final UUID minionUUID;
        private final Material targetMaterial;

        public ConfirmationHolder(UUID minionUUID, Material targetMaterial) {
            this.minionUUID = minionUUID;
            this.targetMaterial = targetMaterial;
        }

        public UUID getMinionUUID() {
            return minionUUID;
        }

        public Material getTargetMaterial() {
            return targetMaterial;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private void openConfirmationGUI(Player player, UUID minionUUID, Material targetMaterial) {
        Inventory confirmationGUI = Bukkit.createInventory(new ConfirmationHolder(minionUUID, targetMaterial), 27, "Confirm Target: " + targetMaterial.name());

        // Add the target block display
        ItemStack targetBlock = new ItemStack(targetMaterial);
        ItemMeta targetMeta = targetBlock.getItemMeta();
        if (targetMeta != null) {
            targetMeta.setDisplayName(ChatColor.AQUA + targetMaterial.name());
            targetBlock.setItemMeta(targetMeta);
        }
        confirmationGUI.setItem(13, targetBlock);

        // Add the accept button
        ItemStack accept = new ItemStack(Material.GREEN_WOOL);
        ItemMeta acceptMeta = accept.getItemMeta();
        if (acceptMeta != null) {
            acceptMeta.setDisplayName(ChatColor.GREEN + "Accept");
            accept.setItemMeta(acceptMeta);
        }
        confirmationGUI.setItem(11, accept);

        // Add the deny button
        ItemStack deny = new ItemStack(Material.RED_WOOL);
        ItemMeta denyMeta = deny.getItemMeta();
        if (denyMeta != null) {
            denyMeta.setDisplayName(ChatColor.RED + "Deny");
            deny.setItemMeta(denyMeta);
        }
        confirmationGUI.setItem(15, deny);

        player.openInventory(confirmationGUI);
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
                if (cursor != null && cursor.getType().isBlock()) {
                    openConfirmationGUI(player, holder.getMinionUUID(), cursor.getType());
                }
                event.setCancelled(true); // Prevent placing the item
            } else if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
                // Handle clicking on an item already in the inventory
                if (event.getCurrentItem().getType().isBlock()) {
                    openConfirmationGUI(player, holder.getMinionUUID(), event.getCurrentItem().getType());
                }
            }
        }
    }

    @EventHandler
    public void onConfirmationClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof ConfirmationHolder holder)) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        UUID minionUUID = holder.getMinionUUID();
        Entity entity = Bukkit.getServer().getEntity(minionUUID);
        if (!(entity instanceof ArmorStand armorStand)) {
            player.closeInventory();
            return;
        }

        Minion minion = new Minion(plugin, armorStand);

        if (clickedItem.getType() == Material.GREEN_WOOL) {
            Material targetMaterial = holder.getTargetMaterial();
            armorStand.getPersistentDataContainer().set(plugin.targetKey, PersistentDataType.STRING, targetMaterial.name());
            player.sendMessage(ChatColor.GREEN + "Minion target set to " + targetMaterial.name());
            player.openInventory(minion.getActionInventory());
        } else if (clickedItem.getType() == Material.RED_WOOL) {
            player.openInventory(minion.getActionInventory());
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