package me.jaron.minion;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import me.jaron.minion.FarmerState;

import java.util.*;

public class InventoryClick implements Listener {

    private final MinionPlugin plugin;

    public InventoryClick(MinionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMinionInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Minion.MinionInventoryHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true);

        // IMPORTANT FIX: Check if the clicked inventory is the player's inventory
        // If it's the player's inventory (hotbar, etc.), just cancel the event and return
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        UUID minionUUID = holder.getMinionUUID();
        Entity entity = Bukkit.getServer().getEntity(minionUUID);
        if (!(entity instanceof ArmorStand armorStand)) {
            player.closeInventory();
            return;
        }

        Minion minion = new Minion(plugin, armorStand);
        String minionTypeStr = armorStand.getPersistentDataContainer().getOrDefault(plugin.minionTypeKey, PersistentDataType.STRING, MinionType.BLOCK_MINER.name());
        MinionType minionType = MinionType.valueOf(minionTypeStr);

        if (event.getSlot() == 0) { // Toggle Minion Type
            MinionType currentType = MinionType.valueOf(minionTypeStr);
            MinionType newType = currentType == MinionType.BLOCK_MINER ? MinionType.FARMER : MinionType.BLOCK_MINER;
            openTypeConfirmationGUI(player, minionUUID, newType);
        } else if (event.getSlot() == 1 && minionType == MinionType.FARMER) {
            byte wantsSeeds = armorStand.getPersistentDataContainer().getOrDefault(plugin.wantsSeedsKey, PersistentDataType.BYTE, (byte)1);
            armorStand.getPersistentDataContainer().set(plugin.wantsSeedsKey, PersistentDataType.BYTE, (byte)(wantsSeeds == 1 ? 0 : 1));
            player.openInventory(minion.getActionInventory());
        } else if (clickedItem.getType() == Material.CHEST) {
            player.openInventory(minion.getMinionStorage());
        } else if (clickedItem.getType() == Material.BARRIER) {
            player.closeInventory();
        } else if (event.getSlot() == 5) { // Remove Minion
            openRemovalConfirmationGUI(player, minionUUID);
        } else if (event.getSlot() == 6) { // Upgrade Minion
            // Handle upgrade button
            int currentTier = armorStand.getPersistentDataContainer().getOrDefault(plugin.tierKey, PersistentDataType.INTEGER, 1);
            if (currentTier < 5) {
                int targetTier = currentTier + 1;

                // Check if upgrade path exists
                Map<String, Integer> upgradeCosts = plugin.getUpgradeManager().getUpgradeCost(targetTier);
                if (upgradeCosts.isEmpty()) {
                    player.sendMessage(ChatColor.RED + "No upgrade path defined for tier " + targetTier);
                    return;
                }

                // Show the upgrade confirmation GUI instead of auto-upgrading
                openUpgradeConfirmationGUI(player, minionUUID, targetTier);
            } else {
                player.sendMessage(ChatColor.RED + "This minion is already at maximum tier!");
            }
        } else if (event.getSlot() == 7) { // Target selection
            if (minionType == MinionType.FARMER) {
                player.openInventory(getFarmerTargetSelectGUI(minionUUID));
            } else {
                player.openInventory(getTargetSelectGUI(minionUUID));
            }
        }
    }

    private Inventory getTargetSelectGUI(UUID minionUUID) {
        return Bukkit.createInventory(new TargetSelectHolder(minionUUID), 54, "Select Target Block");
    }

    private Inventory getFarmerTargetSelectGUI(UUID minionUUID) {
        Inventory inv = Bukkit.createInventory(new FarmerTargetSelectHolder(minionUUID), 27, "Select Farmer Target");
        inv.setItem(10, new ItemStack(Material.WHEAT));
        inv.setItem(11, new ItemStack(Material.CARROT));
        inv.setItem(12, new ItemStack(Material.POTATO));
        inv.setItem(13, new ItemStack(Material.BEETROOT));

        ItemStack sugarCane = new ItemStack(Material.SUGAR_CANE);
        ItemMeta sugarCaneMeta = sugarCane.getItemMeta();
        if (sugarCaneMeta != null) {
            sugarCaneMeta.setLore(List.of(ChatColor.RED + "Sorry that is unavailable at the moment"));
            sugarCane.setItemMeta(sugarCaneMeta);
        }
        inv.setItem(14, sugarCane);

        ItemStack netherWart = new ItemStack(Material.NETHER_WART);
        ItemMeta netherWartMeta = netherWart.getItemMeta();
        if (netherWartMeta != null) {
            netherWartMeta.setLore(List.of(ChatColor.RED + "Sorry that is unavailable at the moment"));
            netherWart.setItemMeta(netherWartMeta);
        }
        inv.setItem(15, netherWart);
        inv.setItem(26, Minion.createBackButton());
        return inv;
    }

    public static class TargetSelectHolder implements InventoryHolder {
        private final UUID minionUUID;
        private final Inventory inventory;

        public TargetSelectHolder(UUID minionUUID) {
            this.minionUUID = minionUUID;
            this.inventory = Bukkit.createInventory(this, 9, "Select Target Block");
        }

        public UUID getMinionUUID() {
            return minionUUID;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public static class FarmerTargetSelectHolder implements InventoryHolder {
        private final UUID minionUUID;

        public FarmerTargetSelectHolder(UUID minionUUID) {
            this.minionUUID = minionUUID;
        }

        public UUID getMinionUUID() {
            return minionUUID;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public static class TypeConfirmationHolder implements InventoryHolder {
        private final UUID minionUUID;
        private final MinionType newType;

        public TypeConfirmationHolder(UUID minionUUID, MinionType newType) {
            this.minionUUID = minionUUID;
            this.newType = newType;
        }

        public UUID getMinionUUID() {
            return minionUUID;
        }

        public MinionType getNewType() {
            return newType;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public static class RemovalConfirmationHolder implements InventoryHolder {
        private final UUID minionUUID;

        public RemovalConfirmationHolder(UUID minionUUID) {
            this.minionUUID = minionUUID;
        }

        public UUID getMinionUUID() {
            return minionUUID;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

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

    private void openTypeConfirmationGUI(Player player, UUID minionUUID, MinionType newType) {
        Inventory confirmationGUI = Bukkit.createInventory(new TypeConfirmationHolder(minionUUID, newType), 27, "Confirm Switch to " + newType.name());

        // Add display item
        ItemStack displayItem;
        if (newType == MinionType.FARMER) {
            displayItem = new ItemStack(Material.DIAMOND_HOE);
        } else {
            displayItem = new ItemStack(Material.DIAMOND_PICKAXE);
        }
        ItemMeta displayMeta = displayItem.getItemMeta();
        if (displayMeta != null) {
            displayMeta.setDisplayName(ChatColor.AQUA + "Switch to " + newType.name());
            displayItem.setItemMeta(displayMeta);
        }
        confirmationGUI.setItem(13, displayItem);

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

    private void openRemovalConfirmationGUI(Player player, UUID minionUUID) {
        Inventory confirmationGUI = Bukkit.createInventory(new RemovalConfirmationHolder(minionUUID), 27, "Confirm Minion Removal");

        ItemStack confirm = new ItemStack(Material.GREEN_WOOL);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setDisplayName(ChatColor.GREEN + "Confirm");
        confirm.setItemMeta(confirmMeta);
        confirmationGUI.setItem(11, confirm);

        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.RED + "Cancel");
        cancel.setItemMeta(cancelMeta);
        confirmationGUI.setItem(15, cancel);

        player.openInventory(confirmationGUI);
    }

    private void openUpgradeConfirmationGUI(Player player, UUID minionUUID, int targetTier) {
        Entity entity = Bukkit.getServer().getEntity(minionUUID);
        if (!(entity instanceof ArmorStand armorStand)) {
            player.closeInventory();
            return;
        }

        int currentTier = armorStand.getPersistentDataContainer().getOrDefault(plugin.tierKey, PersistentDataType.INTEGER, 1);
        // Create a larger inventory to contain upgrade materials
        Inventory confirmationGUI = Bukkit.createInventory(new UpgradeConfirmationHolder(minionUUID, targetTier), 36, "Upgrade Minion to Tier " + targetTier);

        // Add display item with upgrade information
        ItemStack displayItem = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta displayMeta = displayItem.getItemMeta();
        if (displayMeta != null) {
            displayMeta.setDisplayName(ChatColor.AQUA + "Upgrade to Tier " + targetTier);

            // Get the upgrade costs
            Map<String, Integer> upgradeCosts = plugin.getUpgradeManager().getUpgradeCost(targetTier);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.YELLOW + "Current Tier: " + ChatColor.WHITE + currentTier);
            lore.add(ChatColor.YELLOW + "Target Tier: " + ChatColor.GREEN + targetTier);
            lore.add("");
            lore.add(ChatColor.GOLD + "Required Materials:");

            // Add each required item to the lore
            for (Map.Entry<String, Integer> entry : upgradeCosts.entrySet()) {
                try {
                    Material material = Material.valueOf(entry.getKey());
                    int amount = entry.getValue();
                    lore.add(ChatColor.WHITE + " - " + amount + "x " + formatMaterialName(material.name()));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid material in upgrade costs: " + entry.getKey());
                }
            }

            lore.add("");
            lore.add(ChatColor.YELLOW + "Place the required materials in the slots above");
            lore.add(ChatColor.YELLOW + "and click 'Confirm Upgrade' when ready.");

            displayMeta.setLore(lore);
            displayItem.setItemMeta(displayMeta);
        }
        confirmationGUI.setItem(31, displayItem);

        // Add material placeholders in the top rows
        int slot = 10;
        Map<String, Integer> upgradeCosts = plugin.getUpgradeManager().getUpgradeCost(targetTier);
        for (Map.Entry<String, Integer> entry : upgradeCosts.entrySet()) {
            try {
                Material material = Material.valueOf(entry.getKey());
                int amount = entry.getValue();

                ItemStack placeholder = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
                ItemMeta placeholderMeta = placeholder.getItemMeta();
                if (placeholderMeta != null) {
                    placeholderMeta.setDisplayName(ChatColor.YELLOW + "Place " + amount + "x " + formatMaterialName(material.name()) + " here");
                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.GRAY + "Required for upgrade");
                    placeholderMeta.setLore(lore);
                    placeholder.setItemMeta(placeholderMeta);
                }
                confirmationGUI.setItem(slot, placeholder);
                slot++;
                if (slot == 17) break; // Maximum of 7 different materials
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in upgrade costs: " + entry.getKey());
            }
        }

        // Add the confirm upgrade button
        ItemStack accept = new ItemStack(Material.GREEN_WOOL);
        ItemMeta acceptMeta = accept.getItemMeta();
        if (acceptMeta != null) {
            acceptMeta.setDisplayName(ChatColor.GREEN + "Confirm Upgrade");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Click to confirm after placing materials");
            acceptMeta.setLore(lore);
            accept.setItemMeta(acceptMeta);
        }
        confirmationGUI.setItem(30, accept);

        // Add the cancel button
        ItemStack deny = new ItemStack(Material.RED_WOOL);
        ItemMeta denyMeta = deny.getItemMeta();
        if (denyMeta != null) {
            denyMeta.setDisplayName(ChatColor.RED + "Cancel");
            deny.setItemMeta(denyMeta);
        }
        confirmationGUI.setItem(32, deny);

        // Add decorative border with glass panes
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        if (borderMeta != null) {
            borderMeta.setDisplayName(" ");
            border.setItemMeta(borderMeta);
        }

        for (int i = 0; i < 36; i++) {
            if (confirmationGUI.getItem(i) == null) {
                // Bottom row, sides, and material border
                if (i < 9 || i > 26 || i % 9 == 0 || i % 9 == 8) {
                    confirmationGUI.setItem(i, border);
                }
            }
        }

        player.openInventory(confirmationGUI);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;

        // If player clicked their own inventory while a minion GUI is open, just cancel the event
        if (event.getClickedInventory() == player.getInventory() &&
            (event.getInventory().getHolder() instanceof Minion.MinionInventoryHolder ||
             event.getInventory().getHolder() instanceof MinionPlugin.StorageHolder ||
             event.getInventory().getHolder() instanceof TargetSelectHolder ||
             event.getInventory().getHolder() instanceof FarmerTargetSelectHolder ||
             event.getInventory().getHolder() instanceof TypeConfirmationHolder ||
             event.getInventory().getHolder() instanceof RemovalConfirmationHolder ||
             event.getInventory().getHolder() instanceof ConfirmationHolder ||
             event.getInventory().getHolder() instanceof MinionBundleManager.RawItemsHolder ||
             event.getInventory().getHolder() instanceof MinionBundleManager.CategoriesHolder ||
             event.getInventory().getHolder() instanceof MinionBundleManager.MinionsHolder ||
             event.getInventory().getHolder() instanceof MinionBundleManager.CategoryConfirmationHolder ||
             event.getInventory().getHolder() instanceof MinionBundleManager.PartialDeletionHolder)) {
            event.setCancelled(true);
            return; // Exit early - don't process hotbar clicks
        }

        // Cancel any click in a minion inventory or storage
        if (event.getInventory().getHolder() instanceof Minion.MinionInventoryHolder ||
            event.getInventory().getHolder() instanceof MinionPlugin.StorageHolder ||
            event.getInventory().getHolder() instanceof TargetSelectHolder ||
            event.getInventory().getHolder() instanceof MinionBundleManager.RawItemsHolder ||
            event.getInventory().getHolder() instanceof MinionBundleManager.CategoriesHolder ||
            event.getInventory().getHolder() instanceof MinionBundleManager.MinionsHolder) {
            event.setCancelled(true);

            // Handle back button click
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            // Only process clicks for items with ItemMeta and display names to fix the bug with wool/target items
            if (!clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) return;

            // Only process clicks in the actual GUI inventory, not player inventory
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;

            String displayName = clickedItem.getItemMeta().getDisplayName();

            // Handle back button
            if (clickedItem.getType() == Material.BARRIER && displayName.equals(ChatColor.RED + "Back")) {
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

            // Handle collect buttons
            if (clickedItem.getType() == Material.HOPPER) {
                ItemMeta meta = clickedItem.getItemMeta();
                if (meta != null) {
                    displayName = meta.getDisplayName();
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
                int currentTier = armorStand.getPersistentDataContainer().getOrDefault(plugin.tierKey, PersistentDataType.INTEGER, 1);
                if (currentTier < 5) {
                    int targetTier = currentTier + 1;

                    // Check if upgrade path exists
                    Map<String, Integer> upgradeCosts = plugin.getUpgradeManager().getUpgradeCost(targetTier);
                    if (upgradeCosts.isEmpty()) {
                        player.sendMessage(ChatColor.RED + "No upgrade path defined for tier " + targetTier);
                        return;
                    }

                    // Show the upgrade confirmation GUI instead of auto-upgrading
                    openUpgradeConfirmationGUI(player, minionUUID, targetTier);
                } else {
                    player.sendMessage(ChatColor.RED + "This minion is already at maximum tier!");
                }
            } else if (displayName.equals(ChatColor.YELLOW + "Set Target") ||
                       displayName.startsWith(ChatColor.AQUA + "Target: ")) {
                String minionTypeStr = armorStand.getPersistentDataContainer().getOrDefault(plugin.minionTypeKey, PersistentDataType.STRING, MinionType.BLOCK_MINER.name());
                MinionType minionType = MinionType.valueOf(minionTypeStr);
                if (minionType == MinionType.FARMER) {
                    player.openInventory(getFarmerTargetSelectGUI(minionUUID));
                } else {
                    player.openInventory(getTargetSelectGUI(minionUUID));
                }
            }
        }

        // Handle bundle inventory clicks
        if (event.getInventory().getHolder() instanceof MinionBundleManager.RawItemsHolder) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || !clickedItem.hasItemMeta()) return;

            String displayName = clickedItem.getItemMeta().getDisplayName();
            if (displayName.equals(ChatColor.YELLOW + "Next Page")) {
                plugin.getBundleManager().nextPage(player);
            } else if (displayName.equals(ChatColor.YELLOW + "Previous Page")) {
                plugin.getBundleManager().previousPage(player);
            } else if (displayName.equals(ChatColor.GREEN + "View Categories")) {
                plugin.getBundleManager().openCategoriesView(player);
            } else if (displayName.equals(ChatColor.DARK_AQUA + "My Minions")) {
                plugin.getBundleManager().openMinionsView(player);
            } else if (displayName.equals(ChatColor.GREEN + "Collect All")) {
                CollectAllCommand collectAll = new CollectAllCommand(plugin);
                int collected = collectAll.collectAllForPlayer(player);
                if (collected > 0) {
                    player.sendMessage(ChatColor.GREEN + "Collected " + collected + " items!");
                    plugin.getBundleManager().refreshInventory(player);
                } else {
                    player.sendMessage(ChatColor.YELLOW + "No items to collect!");
                }
            }
            return;
        }

        if (event.getInventory().getHolder() instanceof MinionBundleManager.CategoriesHolder) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            if (clickedItem.hasItemMeta()) {
                String displayName = clickedItem.getItemMeta().getDisplayName();
                if (displayName.equals(ChatColor.YELLOW + "Next Page")) {
                    plugin.getBundleManager().nextPage(player);
                    return;
                } else if (displayName.equals(ChatColor.YELLOW + "Previous Page")) {
                    plugin.getBundleManager().previousPage(player);
                    return;
                } else if (displayName.equals(ChatColor.AQUA + "View Raw Items")) {
                    plugin.getBundleManager().openRawItemsView(player);
                    return;
                } else if (displayName.equals(ChatColor.DARK_AQUA + "My Minions")) {
                    plugin.getBundleManager().openMinionsView(player);
                    return;
                } else if (displayName.equals(ChatColor.GREEN + "Collect All")) {
                    CollectAllCommand collectAll = new CollectAllCommand(plugin);
                    int collected = collectAll.collectAllForPlayer(player);
                    if (collected > 0) {
                        player.sendMessage(ChatColor.GREEN + "Collected " + collected + " items!");
                        plugin.getBundleManager().refreshInventory(player);
                    } else {
                        player.sendMessage(ChatColor.YELLOW + "No items to collect!");
                    }
                    return;
                }
            }

            // It's a category item
            Material material = clickedItem.getType();
            int totalAmount = 0;
            if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasLore()) {
                String loreLine = clickedItem.getItemMeta().getLore().get(0); // "Total: <amount>"
                try {
                    totalAmount = Integer.parseInt(ChatColor.stripColor(loreLine).split(" ")[1]);
                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "Error parsing item amount.");
                    return;
                }
            }
            if (totalAmount > 0) {
                if (event.isRightClick()) {
                    // Right-click opens delete options
                    player.openInventory(plugin.getBundleManager().getPartialDeletionGUI(player, material, totalAmount));
                } else {
                    // Left-click opens withdrawal confirmation (original behavior)
                    player.openInventory(plugin.getBundleManager().getCategoryConfirmationGUI(player, material, totalAmount));
                }
            }
            return;
        }

        if (event.getInventory().getHolder() instanceof MinionBundleManager.MinionsHolder) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            if (clickedItem.hasItemMeta()) {
                String displayName = clickedItem.getItemMeta().getDisplayName();
                if (displayName.equals(ChatColor.YELLOW + "Next Page")) {
                    plugin.getBundleManager().nextPage(player);
                } else if (displayName.equals(ChatColor.YELLOW + "Previous Page")) {
                    plugin.getBundleManager().previousPage(player);
                } else if (displayName.equals(ChatColor.GREEN + "View Categories")) {
                    plugin.getBundleManager().openCategoriesView(player);
                } else if (displayName.equals(ChatColor.GREEN + "Collect All")) {
                    CollectAllCommand collectAll = new CollectAllCommand(plugin);
                    int collected = collectAll.collectAllForPlayer(player);
                    if (collected > 0) {
                        player.sendMessage(ChatColor.GREEN + "Collected " + collected + " items!");
                        plugin.getBundleManager().refreshInventory(player);
                    } else {
                        player.sendMessage(ChatColor.YELLOW + "No items to collect!");
                    }
                } else if (displayName.equals(ChatColor.RED + "Delete All Minions")) {
                    // Open confirmation dialog for deleting all minions
                    Inventory confirmationGUI = Bukkit.createInventory(
                        new DeleteAllMinionsConfirmationHolder(player.getUniqueId()),
                        27, "Confirm Delete ALL Minions");

                    // Add warning message
                    ItemStack warningItem = new ItemStack(Material.BARRIER);
                    ItemMeta warningMeta = warningItem.getItemMeta();
                    if (warningMeta != null) {
                        warningMeta.setDisplayName(ChatColor.RED + "WARNING!");
                        List<String> lore = new ArrayList<>();
                        lore.add(ChatColor.GRAY + "This will delete ALL of your minions");
                        lore.add(ChatColor.GRAY + "and transfer their items to your bundle.");
                        lore.add("");
                        lore.add(ChatColor.RED + "This action cannot be undone!");
                        warningMeta.setLore(lore);
                        warningItem.setItemMeta(warningMeta);
                    }
                    confirmationGUI.setItem(13, warningItem);

                    // Add confirm button
                    ItemStack confirmButton = new ItemStack(Material.GREEN_WOOL);
                    ItemMeta confirmMeta = confirmButton.getItemMeta();
                    if (confirmMeta != null) {
                        confirmMeta.setDisplayName(ChatColor.GREEN + "Confirm Delete All");
                        confirmButton.setItemMeta(confirmMeta);
                    }
                    confirmationGUI.setItem(11, confirmButton);

                    // Add cancel button
                    ItemStack cancelButton = new ItemStack(Material.RED_WOOL);
                    ItemMeta cancelMeta = cancelButton.getItemMeta();
                    if (cancelMeta != null) {
                        cancelMeta.setDisplayName(ChatColor.RED + "Cancel");
                        cancelButton.setItemMeta(cancelMeta);
                    }
                    confirmationGUI.setItem(15, cancelButton);

                    player.openInventory(confirmationGUI);
                } else {
                    int clickedSlot = event.getSlot();
                    int page = plugin.getBundleManager().getMinionsCurrentPage(player.getUniqueId());
                    int minionIndex = (page * 45) + clickedSlot;
                    List<Minion> minions = plugin.getMinions().get(player.getUniqueId());

                    if (minions != null && minionIndex < minions.size()) {
                        Minion clickedMinion = minions.get(minionIndex);
                        openMinionOptionsGUI(player, clickedMinion.getUUID());
                    }
                }
            }
            return;
        }

        if (event.getInventory().getHolder() instanceof MinionOptionsHolder holder) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            if (clickedItem.getType() == Material.ENDER_PEARL) {
                openTeleportConfirmationGUI(player, holder.getMinionUUID());
            } else if (clickedItem.getType() == Material.BARRIER) {
                openRemovalConfirmationGUI(player, holder.getMinionUUID());
            } else if (clickedItem.getType() == Material.ARROW) {
                player.openInventory(plugin.getBundleManager().getMinionsInventory(player));
            }
        }

        if (event.getInventory().getHolder() instanceof TeleportConfirmationHolder holder) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            if (clickedItem.getType() == Material.GREEN_WOOL) {
                Entity entity = Bukkit.getServer().getEntity(holder.getMinionUUID());
                if (entity != null) {
                    player.teleport(entity.getLocation());
                    player.sendMessage(ChatColor.GREEN + "Teleported to minion!");
                    player.closeInventory();
                }
            } else if (clickedItem.getType() == Material.RED_WOOL) {
                openMinionOptionsGUI(player, holder.getMinionUUID());
            }
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

    @EventHandler
    public void onRemovalConfirmationClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof RemovalConfirmationHolder holder)) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        UUID minionUUID = holder.getMinionUUID();
        Entity entity = Bukkit.getServer().getEntity(minionUUID);

        if (clickedItem.getType() == Material.GREEN_WOOL) {
            if (entity instanceof ArmorStand armorStand) {
                Minion minion = new Minion(plugin, armorStand);
                Inventory minionInv = minion.getMinionStorage();
                List<ItemStack> itemsToCollect = new ArrayList<>();
                for (ItemStack item : minionInv.getContents()) {
                    if (item != null && item.getType() != Material.BARRIER && item.getType() != Material.HOPPER) {
                        itemsToCollect.add(item);
                    }
                }

                for (ItemStack item : itemsToCollect) {
                    HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(item);
                    if (!remaining.isEmpty()) {
                        plugin.getBundleManager().addItemToBundle(player, remaining.get(0));
                        player.sendMessage(ChatColor.YELLOW + "Some items could not fit in your inventory and were added to your bundle.");
                    }
                }

                plugin.removeMinion(player, armorStand);
                player.sendMessage(ChatColor.GREEN + "Minion removed.");
                player.closeInventory();
            }
        } else if (clickedItem.getType() == Material.RED_WOOL) {
            if (entity instanceof ArmorStand armorStand) {
                player.openInventory(new Minion(plugin, armorStand).getActionInventory());
            }
        }
    }

    private void openMinionOptionsGUI(Player player, UUID minionUUID) {
        Inventory optionsGUI = Bukkit.createInventory(new MinionOptionsHolder(minionUUID), 27, "Minion Options");

        // Add teleport option
        ItemStack teleportItem = new ItemStack(Material.ENDER_PEARL);
        ItemMeta teleportMeta = teleportItem.getItemMeta();
        if (teleportMeta != null) {
            teleportMeta.setDisplayName(ChatColor.AQUA + "Teleport to Minion");
            teleportItem.setItemMeta(teleportMeta);
        }
        optionsGUI.setItem(11, teleportItem);

        // Add remove option
        ItemStack removeItem = new ItemStack(Material.BARRIER);
        ItemMeta removeMeta = removeItem.getItemMeta();
        if (removeMeta != null) {
            removeMeta.setDisplayName(ChatColor.RED + "Remove Minion");
            removeItem.setItemMeta(removeMeta);
        }
        optionsGUI.setItem(15, removeItem);

        // Add back button
        optionsGUI.setItem(22, Minion.createBackButton());

        player.openInventory(optionsGUI);
    }

    private void openTeleportConfirmationGUI(Player player, UUID minionUUID) {
        Inventory confirmationGUI = Bukkit.createInventory(new TeleportConfirmationHolder(minionUUID), 27, "Confirm Teleport to Minion");

        // Add teleport confirmation item
        ItemStack teleportItem = new ItemStack(Material.GREEN_WOOL);
        ItemMeta teleportMeta = teleportItem.getItemMeta();
        if (teleportMeta != null) {
            teleportMeta.setDisplayName(ChatColor.GREEN + "Confirm Teleport");
            teleportItem.setItemMeta(teleportMeta);
        }
        confirmationGUI.setItem(11, teleportItem);

        // Add cancel button
        ItemStack cancelItem = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setDisplayName(ChatColor.RED + "Cancel");
            cancelItem.setItemMeta(cancelMeta);
        }
        confirmationGUI.setItem(15, cancelItem);

        player.openInventory(confirmationGUI);
    }

    public static class TeleportConfirmationHolder implements InventoryHolder {
        private final UUID minionUUID;

        public TeleportConfirmationHolder(UUID minionUUID) {
            this.minionUUID = minionUUID;
        }

        public UUID getMinionUUID() {
            return minionUUID;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public static class MinionOptionsHolder implements InventoryHolder {
        private final UUID minionUUID;

        public MinionOptionsHolder(UUID minionUUID) {
            this.minionUUID = minionUUID;
        }

        public UUID getMinionUUID() {
            return minionUUID;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    // New holder for upgrade confirmation GUI
    public static class UpgradeConfirmationHolder implements InventoryHolder {
        private final UUID minionUUID;
        private final int targetTier;

        public UpgradeConfirmationHolder(UUID minionUUID, int targetTier) {
            this.minionUUID = minionUUID;
            this.targetTier = targetTier;
        }

        public UUID getMinionUUID() {
            return minionUUID;
        }

        public int getTargetTier() {
            return targetTier;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    // New holder for Delete All Minions confirmation GUI
    public static class DeleteAllMinionsConfirmationHolder implements InventoryHolder {
        private final UUID playerUUID;

        public DeleteAllMinionsConfirmationHolder(UUID playerUUID) {
            this.playerUUID = playerUUID;
        }

        public UUID getPlayerUUID() {
            return playerUUID;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    @EventHandler
    public void onTypeConfirmationClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof TypeConfirmationHolder holder)) return;

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
            MinionType newType = holder.getNewType();
            armorStand.getPersistentDataContainer().set(plugin.minionTypeKey, PersistentDataType.STRING, newType.name());

            // If switching to FARMER, set default target to WHEAT_SEEDS
            if (newType == MinionType.FARMER) {
                armorStand.getPersistentDataContainer().set(plugin.targetKey, PersistentDataType.STRING, Material.WHEAT_SEEDS.name());
                armorStand.getPersistentDataContainer().set(plugin.farmerStateKey, PersistentDataType.STRING, FarmerState.HOEING.name());
            } else if (newType == MinionType.BLOCK_MINER) {
                // If switching to MINER and no target set, set to COBBLESTONE
                String currentTarget = armorStand.getPersistentDataContainer().getOrDefault(plugin.targetKey, PersistentDataType.STRING, "");
                if (currentTarget.isEmpty() || !Material.getMaterial(currentTarget).isBlock()) {
                    armorStand.getPersistentDataContainer().set(plugin.targetKey, PersistentDataType.STRING, Material.COBBLESTONE.name());
                }
            }

            player.sendMessage(ChatColor.GREEN + "Minion type changed to " + newType.name());
            player.openInventory(minion.getActionInventory());
        } else if (clickedItem.getType() == Material.RED_WOOL) {
            player.openInventory(minion.getActionInventory());
        }
    }

    @EventHandler
    public void onFarmerTargetSelect(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof FarmerTargetSelectHolder holder) {
            event.setCancelled(true);

            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();

            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            // Special handling for barrier ("Back" button)
            if (clickedItem.getType() == Material.BARRIER) {
                UUID minionUUID = holder.getMinionUUID();
                Entity entity = Bukkit.getServer().getEntity(minionUUID);
                if (entity instanceof ArmorStand armorStand) {
                    player.openInventory(new Minion(plugin, armorStand).getActionInventory());
                }
                return;
            }

            // Check for disabled items
            if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasLore()) {
                List<String> lore = clickedItem.getItemMeta().getLore();
                for (String loreLine : lore) {
                    if (loreLine.contains("unavailable")) {
                        player.sendMessage(ChatColor.RED + "This crop is currently unavailable.");
                        return;
                    }
                }
            }

            // Handle valid crop selections
            Material cropMaterial = clickedItem.getType();
            if (cropMaterial == Material.WHEAT || cropMaterial == Material.CARROT ||
                cropMaterial == Material.POTATO || cropMaterial == Material.BEETROOT ||
                cropMaterial == Material.NETHER_WART) {

                UUID minionUUID = holder.getMinionUUID();
                Entity entity = Bukkit.getServer().getEntity(minionUUID);
                if (entity instanceof ArmorStand armorStand) {
                    // For seed-based crops, store the seed material name
                    if (cropMaterial == Material.WHEAT) {
                        cropMaterial = Material.WHEAT_SEEDS;
                    } else if (cropMaterial == Material.BEETROOT) {
                        cropMaterial = Material.BEETROOT_SEEDS;
                    }

                    armorStand.getPersistentDataContainer().set(plugin.targetKey, PersistentDataType.STRING, cropMaterial.name());
                    player.sendMessage(ChatColor.GREEN + "Farmer target set to " + cropMaterial.name());

                    // Reset farmer state to HOEING when target changes
                    armorStand.getPersistentDataContainer().set(plugin.farmerStateKey, PersistentDataType.STRING, FarmerState.HOEING.name());

                    // Return to the minion action inventory
                    player.openInventory(new Minion(plugin, armorStand).getActionInventory());
                }
            }
        }
    }

    @EventHandler
    public void onUpgradeConfirmationClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Check if this is our upgrade confirmation inventory
        if (event.getInventory().getHolder() instanceof UpgradeConfirmationHolder holder) {
            // Special case for UI elements that shouldn't be clicked
            ItemStack clickedItem = event.getCurrentItem();

            // Always allow clicks in player inventory (bottom inventory)
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                // This is a click in the player's inventory, allow it
                return;
            }

            // Check if this is a shift-click in player inventory or a transfer between inventories
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY ||
                event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                // For shift-clicking from player inventory to upgrade inventory
                if (clickedItem != null && !clickedItem.getType().isAir()) {
                    // Only allow if destination is in slots 10-16
                    for (int slot = 10; slot <= 16; slot++) {
                        ItemStack slotItem = event.getInventory().getItem(slot);
                        if (slotItem == null || slotItem.getType() == Material.AIR ||
                            slotItem.getType() == Material.LIGHT_GRAY_STAINED_GLASS_PANE) {
                            // There's an empty slot for this item
                            return; // Allow the action
                        }
                    }
                }
            }

            // Protect UI elements
            if (clickedItem != null && (
                clickedItem.getType() == Material.BLACK_STAINED_GLASS_PANE ||
                clickedItem.getType() == Material.GREEN_WOOL ||
                clickedItem.getType() == Material.RED_WOOL ||
                clickedItem.getType() == Material.EXPERIENCE_BOTTLE
            )) {
                event.setCancelled(true); // Cancel clicks on UI elements

                // Handle confirm/cancel button clicks
                if (clickedItem.getType() == Material.GREEN_WOOL || clickedItem.getType() == Material.RED_WOOL) {
                    UUID minionUUID = holder.getMinionUUID();
                    Entity entity = Bukkit.getServer().getEntity(minionUUID);
                    if (!(entity instanceof ArmorStand armorStand)) {
                        player.closeInventory();
                        return;
                    }

                    Minion minion = new Minion(plugin, armorStand);
                    int targetTier = holder.getTargetTier();

                    // Handle button clicks
                    if (clickedItem.getType() == Material.GREEN_WOOL) {
                        // Get the upgrade costs
                        Map<String, Integer> upgradeCosts = plugin.getUpgradeManager().getUpgradeCost(targetTier);
                        if (upgradeCosts.isEmpty()) {
                            player.sendMessage(ChatColor.RED + "No upgrade path defined for tier " + targetTier);
                            player.openInventory(minion.getActionInventory());
                            return;
                        }

                        // Convert to Material map
                        Map<Material, Integer> requiredMaterials = new HashMap<>();
                        for (Map.Entry<String, Integer> entry : upgradeCosts.entrySet()) {
                            try {
                                Material material = Material.valueOf(entry.getKey());
                                int amount = entry.getValue();
                                requiredMaterials.put(material, amount);
                            } catch (IllegalArgumentException e) {
                                plugin.getLogger().warning("Invalid material in upgrade costs: " + entry.getKey());
                            }
                        }

                        // Count all items in the upgrade slots (10-16)
                        Map<Material, Integer> foundMaterials = new HashMap<>();
                        List<ItemStack> allItems = new ArrayList<>();

                        for (int slot = 10; slot <= 16; slot++) {
                            ItemStack item = event.getInventory().getItem(slot);
                            if (item != null && !item.getType().isAir() && item.getType() != Material.LIGHT_GRAY_STAINED_GLASS_PANE) {
                                allItems.add(item.clone()); // Clone to avoid modifying the inventory directly
                                Material material = item.getType();

                                // Only count required materials towards the upgrade
                                if (requiredMaterials.containsKey(material)) {
                                    foundMaterials.put(material, foundMaterials.getOrDefault(material, 0) + item.getAmount());
                                }
                            }
                        }

                        // Check if all materials are present in sufficient quantities
                        boolean allMaterialsPresent = true;
                        StringBuilder missingItems = new StringBuilder();

                        for (Map.Entry<Material, Integer> entry : requiredMaterials.entrySet()) {
                            Material material = entry.getKey();
                            int requiredAmount = entry.getValue();
                            int foundAmount = foundMaterials.getOrDefault(material, 0);

                            if (foundAmount < requiredAmount) {
                                allMaterialsPresent = false;
                                missingItems.append("\n").append(ChatColor.RED).append("- ")
                                        .append(requiredAmount - foundAmount).append("x ")
                                        .append(formatMaterialName(material.name()));
                            }
                        }

                        if (!allMaterialsPresent) {
                            player.sendMessage(ChatColor.RED + "You're missing these materials:" + missingItems);
                            return;
                        }

                        // Process and return extra items
                        List<ItemStack> itemsToReturn = new ArrayList<>();
                        Map<Material, Integer> materialsToConsume = new HashMap<>(requiredMaterials);

                        for (ItemStack item : allItems) {
                            Material material = item.getType();

                            // If this is a required material
                            if (materialsToConsume.containsKey(material)) {
                                int requiredAmount = materialsToConsume.get(material);

                                if (requiredAmount > 0) {
                                    // If we need more than the stack has
                                    if (requiredAmount >= item.getAmount()) {
                                        materialsToConsume.put(material, requiredAmount - item.getAmount());
                                        // We consumed the entire stack, nothing to return
                                    } else {
                                        // We only need part of the stack
                                        ItemStack extra = item.clone();
                                        extra.setAmount(item.getAmount() - requiredAmount);
                                        itemsToReturn.add(extra);
                                        materialsToConsume.put(material, 0);
                                    }
                                } else {
                                    // We already have enough of this material
                                    itemsToReturn.add(item.clone());
                                }
                            } else {
                                // This isn't a required material, return it
                                itemsToReturn.add(item.clone());
                            }
                        }

                        // Return any extra items to the player
                        if (!itemsToReturn.isEmpty()) {
                            for (ItemStack item : itemsToReturn) {
                                HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(item);

                                // If inventory is full, drop the items
                                if (!remaining.isEmpty()) {
                                    for (ItemStack remainingItem : remaining.values()) {
                                        player.getWorld().dropItemNaturally(player.getLocation(), remainingItem);
                                    }
                                }
                            }

                            player.sendMessage(ChatColor.YELLOW + "Extra items have been returned to your inventory.");
                        }

                        // Apply the upgrade
                        armorStand.getPersistentDataContainer().set(plugin.tierKey, PersistentDataType.INTEGER, targetTier);

                        // Update the minion's name to reflect its tier
                        String minionTypeStr = armorStand.getPersistentDataContainer().getOrDefault(plugin.minionTypeKey, PersistentDataType.STRING, MinionType.BLOCK_MINER.name());
                        armorStand.setCustomName(ChatColor.GOLD + minionTypeStr + " " + ChatColor.WHITE + "[Tier " + targetTier + "]");

                        // Send success message
                        player.sendMessage(ChatColor.GREEN + "Successfully upgraded minion to tier " + targetTier + "!");

                        // Update storage inventory size based on tier
                        UUID minionUUID2 = armorStand.getUniqueId();
                        int size = Math.min(targetTier * 9, 54); // Tier 1 = 9, Tier 2 = 18, etc. (max 54)

                        // Get old storage inventory
                        Inventory oldStorage = plugin.getMinionStorage(minionUUID2);

                        // Create new inventory with larger size
                        Inventory newStorage = Bukkit.createInventory(plugin.new StorageHolder(minionUUID2), size, ChatColor.AQUA + "Minion Storage");

                        // Copy items from old inventory to new one
                        if (oldStorage != null) {
                            for (int i = 0; i < Math.min(oldStorage.getSize(), newStorage.getSize()); i++) {
                                ItemStack item = oldStorage.getItem(i);
                                // Skip copying the control buttons from old storage
                                if (item != null &&
                                    !(item.getType() == Material.BARRIER ||
                                      (item.getType() == Material.HOPPER &&
                                       item.hasItemMeta() &&
                                       (item.getItemMeta().getDisplayName().contains("Collect All") ||
                                        item.getItemMeta().getDisplayName().contains("Collect from Chest"))))) {
                                    newStorage.setItem(i, item);
                                }
                            }
                        }

                        // Set the new storage inventory
                        plugin.setMinionStorage(minionUUID2, newStorage);

                        // If the minion was running automation, restart it to apply new delay
                        if (plugin.isAutomationActive(player.getUniqueId())) {
                            minion.stopAutomation();
                            minion.startAutomation();
                        }

                        player.openInventory(minion.getActionInventory());
                    } else if (clickedItem.getType() == Material.RED_WOOL) {
                        // Return all items to the player before closing
                        for (int slot = 10; slot <= 16; slot++) {
                            ItemStack item = event.getInventory().getItem(slot);
                            if (item != null && !item.getType().isAir() && item.getType() != Material.LIGHT_GRAY_STAINED_GLASS_PANE) {
                                HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(item);

                                // If inventory is full, drop the items
                                if (!remaining.isEmpty()) {
                                    for (ItemStack remainingItem : remaining.values()) {
                                        player.getWorld().dropItemNaturally(player.getLocation(), remainingItem);
                                    }
                                }
                            }
                        }

                        player.openInventory(minion.getActionInventory());
                    }
                }
                return;
            }

            // If clicking in material slots (10-16)
            if (event.getRawSlot() >= 10 && event.getRawSlot() <= 16) {
                // If clicking on a placeholder, remove it
                if (clickedItem != null && clickedItem.getType() == Material.LIGHT_GRAY_STAINED_GLASS_PANE) {
                    event.setCurrentItem(null);
                }

                // Allow any interaction in the material slots
                event.setCancelled(false);
                return;
            }

            // For any other slots, cancel by default
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBundleWithdrawalClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Handle bundle withdrawal confirmation clicks
        if (event.getInventory().getHolder() instanceof MinionBundleManager.CategoryConfirmationHolder holder) {
            event.setCancelled(true);

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            // Handle confirm/cancel buttons
            if (clickedItem.getType() == Material.GREEN_WOOL) {
                // Process withdrawal
                plugin.getBundleManager().withdrawFromBundle(player, holder.getMaterial());
                player.openInventory(plugin.getBundleManager().getCategoriesInventory(player));
            } else if (clickedItem.getType() == Material.RED_WOOL) {
                // Cancel and go back to categories view
                player.openInventory(plugin.getBundleManager().getCategoriesInventory(player));
            }
        }
    }

    @EventHandler
    public void onPartialDeletionClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Handle partial deletion GUI clicks
        if (event.getInventory().getHolder() instanceof MinionBundleManager.PartialDeletionHolder holder) {
            event.setCancelled(true);

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            Material material = holder.getMaterial();
            int totalAmount = holder.getTotalAmount();

            // Handle different deletion options
            if (clickedItem.getType() == Material.TNT) {
                // Delete ALL items
                plugin.getBundleManager().deleteFromBundle(player, material, totalAmount);
                player.openInventory(plugin.getBundleManager().getCategoriesInventory(player));
            } else if (clickedItem.getType() == Material.RED_CONCRETE) {
                // Delete 1/2 of items
                int amountToDelete = totalAmount / 2;
                plugin.getBundleManager().deleteFromBundle(player, material, amountToDelete);
                player.openInventory(plugin.getBundleManager().getCategoriesInventory(player));
            } else if (clickedItem.getType() == Material.ORANGE_CONCRETE) {
                // Delete 1/4 of items
                int amountToDelete = totalAmount / 4;
                plugin.getBundleManager().deleteFromBundle(player, material, amountToDelete);
                player.openInventory(plugin.getBundleManager().getCategoriesInventory(player));
            } else if (clickedItem.getType() == Material.YELLOW_CONCRETE) {
                // Delete 1/8 of items
                int amountToDelete = totalAmount / 8;
                plugin.getBundleManager().deleteFromBundle(player, material, amountToDelete);
                player.openInventory(plugin.getBundleManager().getCategoriesInventory(player));
            } else if (clickedItem.getType() == Material.BARRIER) {
                // Cancel deletion and return to categories view
                player.openInventory(plugin.getBundleManager().getCategoriesInventory(player));
            }
        }
    }

    /**
     * Helper method to format material names for display
     * Converts names like DIAMOND_SWORD to Diamond Sword
     */
    private String formatMaterialName(String materialName) {
        String[] words = materialName.split("_");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.length() > 0) {
                result.append(word.substring(0, 1).toUpperCase())
                      .append(word.substring(1).toLowerCase())
                      .append(" ");
            }
        }

        return result.toString().trim();
    }

    /**
     * Helper method to remove items from player inventory
     */
    private void removeItems(Player player, Material material, int amount) {
        int remaining = amount;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                if (item.getAmount() <= remaining) {
                    remaining -= item.getAmount();
                    item.setAmount(0);
                } else {
                    item.setAmount(item.getAmount() - remaining);
                    remaining = 0;
                }

                if (remaining <= 0) break;
            }
        }
    }

    @EventHandler
    public void onDeleteAllMinionsClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof DeleteAllMinionsConfirmationHolder holder)) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (clickedItem.getType() == Material.GREEN_WOOL) {
            // Execute the delete all minions command
            DeleteAllMinionsCommand deleteCommand = new DeleteAllMinionsCommand(plugin);
            boolean success = deleteCommand.deleteAllMinions(player);

            if (success) {
                // Return to bundle view
                player.openInventory(plugin.getBundleManager().getCategoriesInventory(player));
            } else {
                // Close inventory if there was an issue
                player.closeInventory();
            }
        } else if (clickedItem.getType() == Material.RED_WOOL) {
            // Cancel and return to minions view
            player.openInventory(plugin.getBundleManager().getMinionsInventory(player));
        }
    }
}
