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
import org.bukkit.event.inventory.InventoryCloseEvent;
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


        if ("stats".equals(holder.getInventoryType())) {
            // Only handle back button click for stats inventory
            if (event.getSlot() == 22) {
                UUID minionUUID = holder.getMinionUUID();
                Entity entity = Bukkit.getServer().getEntity(minionUUID);
                if (entity instanceof ArmorStand armorStand) {
                    Minion minion = new Minion(plugin, armorStand);
                    player.openInventory(minion.getActionInventory());
                }
            }
            return; // Exit early for stats inventory clicks
        }


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

        // Handle clicks based on new slot layout
        if (event.getSlot() == 10) { // Toggle Minion Type
            MinionType currentType = MinionType.valueOf(minionTypeStr);
            MinionType newType = currentType == MinionType.BLOCK_MINER ? MinionType.FARMER : MinionType.BLOCK_MINER;
            openTypeConfirmationGUI(player, minionUUID, newType);
        } else if (event.getSlot() == 11 && minionType == MinionType.FARMER) { // Toggle Seeds (only for farmer)
            byte wantsSeeds = armorStand.getPersistentDataContainer().getOrDefault(plugin.wantsSeedsKey, PersistentDataType.BYTE, (byte)1);
            armorStand.getPersistentDataContainer().set(plugin.wantsSeedsKey, PersistentDataType.BYTE, (byte)(wantsSeeds == 1 ? 0 : 1));
            player.openInventory(minion.getActionInventory());
        } else if (event.getSlot() == 19) { // View Stats
            player.openInventory(minion.getStatsInventory());
        } else if (event.getSlot() == 13) { // Open Storage
            player.openInventory(minion.getMinionStorage());
        } else if (event.getSlot() == 31) { // Close menu
            player.closeInventory();
        } else if (event.getSlot() == 22) { // Remove Minion
            openRemovalConfirmationGUI(player, minionUUID);
        } else if (event.getSlot() == 25) { // Upgrade Minion
            int currentTier = armorStand.getPersistentDataContainer().getOrDefault(plugin.tierKey, PersistentDataType.INTEGER, 1);
            int maxTier = plugin.getUpgradeManager().getMaxTier();
            if (currentTier < maxTier) {
                int targetTier = currentTier + 1;
                Map<String, Integer> upgradeCosts = plugin.getUpgradeManager().getUpgradeCost(targetTier);
                if (upgradeCosts.isEmpty()) {
                    player.sendMessage(ChatColor.RED + "No upgrade path defined for tier " + targetTier);
                    return;
                }
                openUpgradeConfirmationGUI(player, minionUUID, targetTier);
            } else {
                player.sendMessage(ChatColor.RED + "This minion is already at maximum tier!");
            }
        } else if (event.getSlot() == 16) { // Target selection
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
        // Create inventory with clear title
        Inventory confirmationGUI = Bukkit.createInventory(new UpgradeConfirmationHolder(minionUUID, targetTier), 45,
                ChatColor.DARK_PURPLE + "Upgrade: " + ChatColor.GRAY + "Tier " + currentTier + " → " + ChatColor.GREEN + "Tier " + targetTier);

        // Add display item with upgrade information
//        ItemStack displayItem = new ItemStack(Material.EXPERIENCE_BOTTLE);
//        ItemMeta displayMeta = displayItem.getItemMeta();
//        if (displayMeta != null) {
//            displayMeta.setDisplayName(ChatColor.GOLD + "Upgrade to Tier " + targetTier);
//
//            // Get the upgrade costs
//            Map<String, Integer> upgradeCosts = plugin.getUpgradeManager().getUpgradeCost(targetTier);
//
//            List<String> lore = new ArrayList<>();
//            lore.add(ChatColor.YELLOW + "Current Tier: " + ChatColor.WHITE + currentTier);
//            lore.add(ChatColor.YELLOW + "Target Tier: " + ChatColor.GREEN + targetTier);
//            lore.add("");
//            lore.add(ChatColor.GOLD + "Place required materials in slots above");
//
//            displayMeta.setLore(lore);
//            displayItem.setItemMeta(displayMeta);
//        }
//        confirmationGUI.setItem(31, displayItem);

        // Create border
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        if (borderMeta != null) {
            borderMeta.setDisplayName(" ");
            border.setItemMeta(borderMeta);
        }

        // Add border around edges
        for (int i = 0; i < 45; i++) {
            if (i < 9 || i > 35 || i % 9 == 0 || i % 9 == 8) {
                confirmationGUI.setItem(i, border);
            }
        }

        // Add auto-place button
        ItemStack autoPlaceButton = new ItemStack(Material.HOPPER);
        ItemMeta autoPlaceMeta = autoPlaceButton.getItemMeta();
        if (autoPlaceMeta != null) {
            autoPlaceMeta.setDisplayName(ChatColor.GREEN + "Auto-Place Materials");
            List<String> autoPlacelore = new ArrayList<>();
            autoPlacelore.add(ChatColor.GRAY + "Click to automatically place");
            autoPlacelore.add(ChatColor.GRAY + "required materials from your inventory");
            autoPlaceMeta.setLore(autoPlacelore);
            autoPlaceButton.setItemMeta(autoPlaceMeta);
        }
        confirmationGUI.setItem(40, autoPlaceButton);

        // Add material requirements in the top rows
        Map<String, Integer> upgradeCosts = plugin.getUpgradeManager().getUpgradeCost(targetTier);

        // Display required materials in a clearer way
        int materialSlot = 11;
        List<Material> requiredMaterials = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : upgradeCosts.entrySet()) {
            try {
                Material material = Material.valueOf(entry.getKey());
                requiredMaterials.add(material);
                int amount = entry.getValue();

                // Create item display showing what's needed
                ItemStack requiredItem = new ItemStack(material);
                ItemMeta requiredMeta = requiredItem.getItemMeta();
                if (requiredMeta != null) {
                    requiredMeta.setDisplayName(ChatColor.YELLOW + formatMaterialName(material.name()));
                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.WHITE + "Required: " + ChatColor.GOLD + amount);
                    lore.add(ChatColor.GRAY + "Place items here ↓");
                    requiredMeta.setLore(lore);
                    requiredItem.setItemMeta(requiredMeta);
                }
                requiredItem.setAmount(Math.min(amount, 64));

                // Place in top row
                confirmationGUI.setItem(materialSlot, requiredItem);

                // Create empty slot below for placing items
                ItemStack placeholderItem = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
                ItemMeta placeholderMeta = placeholderItem.getItemMeta();
                if (placeholderMeta != null) {
                    placeholderMeta.setDisplayName(ChatColor.AQUA + "Place " + formatMaterialName(material.name()) + " here");
                    placeholderMeta.setLore(List.of(ChatColor.GRAY + "Drop items here"));
                    placeholderItem.setItemMeta(placeholderMeta);
                }
                confirmationGUI.setItem(materialSlot + 9, placeholderItem);

                materialSlot += 2;
                if (materialSlot > 16) break; // Maximum of 4 different materials in a row
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in upgrade costs: " + entry.getKey());
            }
        }

        // Add the confirm upgrade button
        ItemStack accept = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta acceptMeta = accept.getItemMeta();
        if (acceptMeta != null) {
            acceptMeta.setDisplayName(ChatColor.GREEN + "Confirm Upgrade");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Click to confirm after placing materials");
            acceptMeta.setLore(lore);
            accept.setItemMeta(acceptMeta);
        }
        confirmationGUI.setItem(38, accept);


        // Add the cancel button
        ItemStack deny = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta denyMeta = deny.getItemMeta();
        if (denyMeta != null) {
            denyMeta.setDisplayName(ChatColor.RED + "Cancel");
            deny.setItemMeta(denyMeta);
        }
        confirmationGUI.setItem(42, deny);

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
            return;
        }

        // Cancel any click in a minion inventory or storage
        if (event.getInventory().getHolder() instanceof Minion.MinionInventoryHolder ||
            event.getInventory().getHolder() instanceof MinionPlugin.StorageHolder ||
            event.getInventory().getHolder() instanceof TargetSelectHolder ||
            event.getInventory().getHolder() instanceof MinionBundleManager.RawItemsHolder ||
            event.getInventory().getHolder() instanceof MinionBundleManager.CategoriesHolder ||
            event.getInventory().getHolder() instanceof MinionBundleManager.MinionsHolder
        ) {
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
            if (clickedItem.getType() == Material.RED_STAINED_GLASS_PANE && displayName.equals(ChatColor.RED + "Back")) {
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
                // Use the dynamic max tier from the upgrade manager instead of hardcoded value
                int maxTier = plugin.getUpgradeManager().getMaxTier();
                if (currentTier < maxTier) {
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
            } else if (clickedItem.getType() == Material.RED_STAINED_GLASS_PANE && clickedItem.getItemMeta().getDisplayName().equalsIgnoreCase(ChatColor.RED + "Back")) {
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
            // Cancel all clicks in the top inventory by default
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                event.setCancelled(true);
            }

            // Get upgrade costs for reference
            int targetTier = holder.getTargetTier();
            Map<String, Integer> upgradeCosts = plugin.getUpgradeManager().getUpgradeCost(targetTier);
            Map<Material, Integer> requiredMaterials = new HashMap<>();
            Map<Material, Integer> placeholderSlots = new HashMap<>();

            // Convert string materials to Material objects
            for (Map.Entry<String, Integer> entry : upgradeCosts.entrySet()) {
                try {
                    Material material = Material.valueOf(entry.getKey());
                    requiredMaterials.put(material, entry.getValue());
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid material in upgrade costs: " + entry.getKey());
                }
            }

            // Find placeholder slots for each material
            for (int slot = 0; slot < event.getInventory().getSize(); slot++) {
                ItemStack item = event.getInventory().getItem(slot);
                if (item != null && item.getType() == Material.LIGHT_BLUE_STAINED_GLASS_PANE && item.hasItemMeta()) {
                    String displayName = item.getItemMeta().getDisplayName();
                    for (Material material : requiredMaterials.keySet()) {
                        if (displayName.contains(formatMaterialName(material.name()))) {
                            placeholderSlots.put(material, slot);
                            break;
                        }
                    }
                }
            }

            // Handle auto-place button
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.HOPPER) {
                event.setCancelled(true);

                // Auto-place materials from player inventory
                for (Material material : requiredMaterials.keySet()) {
                    int required = requiredMaterials.get(material);
                    int placeholderSlot = placeholderSlots.getOrDefault(material, -1);

                    if (placeholderSlot != -1) {
                        // Check if there are already items in this slot
                        ItemStack existingItem = event.getInventory().getItem(placeholderSlot);
                        int alreadyPlaced = 0;

                        // Count existing materials or ignore the placeholder
                        if (existingItem != null) {
                            if (existingItem.getType() == material) {
                                alreadyPlaced = existingItem.getAmount();
                            } else if (existingItem.getType() != Material.LIGHT_BLUE_STAINED_GLASS_PANE) {
                                // Wrong material in slot, skip this one
                                continue;
                            }
                        }

                        int stillNeeded = required - alreadyPlaced;
                        if (stillNeeded <= 0) continue;

                        // Find material in player inventory
                        int available = 0;
                        HashMap<Integer, ItemStack> materialSlots = new HashMap<>();

                        for (int i = 0; i < player.getInventory().getSize(); i++) {
                            ItemStack item = player.getInventory().getItem(i);
                            if (item != null && item.getType() == material) {
                                available += item.getAmount();
                                materialSlots.put(i, item);
                            }
                        }

                        int toTransfer = Math.min(available, stillNeeded);
                        if (toTransfer > 0) {
                            // Remove placeholder if exists
                            if (existingItem != null && existingItem.getType() == Material.LIGHT_BLUE_STAINED_GLASS_PANE) {
                                event.getInventory().setItem(placeholderSlot, null);
                            }

                            // Create new stack or add to existing
                            ItemStack newStack;
                            if (existingItem == null || existingItem.getType() == Material.LIGHT_BLUE_STAINED_GLASS_PANE || existingItem.getType().isAir()) {
                                newStack = new ItemStack(material, toTransfer);
                            } else {
                                existingItem.setAmount(existingItem.getAmount() + toTransfer);
                                newStack = existingItem;
                            }
                            event.getInventory().setItem(placeholderSlot, newStack);

                            // Remove from player inventory
                            int remaining = toTransfer;
                            for (Map.Entry<Integer, ItemStack> entry : materialSlots.entrySet()) {
                                if (remaining <= 0) break;

                                ItemStack item = entry.getValue();
                                int slotIndex = entry.getKey();

                                if (item.getAmount() <= remaining) {
                                    remaining -= item.getAmount();
                                    player.getInventory().setItem(slotIndex, null);
                                } else {
                                    item.setAmount(item.getAmount() - remaining);
                                    remaining = 0;
                                }
                            }
                        }
                    }
                }
                player.updateInventory();
                return;
            }

            // Handle confirm/cancel button clicks
            if (event.getCurrentItem() != null && (event.getCurrentItem().getType() == Material.LIME_STAINED_GLASS_PANE ||
                    event.getCurrentItem().getType() == Material.RED_STAINED_GLASS_PANE)) {
                event.setCancelled(true);

                UUID minionUUID = holder.getMinionUUID();
                Entity entity = Bukkit.getServer().getEntity(minionUUID);
                if (!(entity instanceof ArmorStand armorStand)) {
                    player.closeInventory();
                    return;
                }

                Minion minion = new Minion(plugin, armorStand);

                // Handle button clicks
                if (event.getCurrentItem().getType() == Material.LIME_STAINED_GLASS_PANE) {
                    // Process the upgrade by checking materials provided
                    boolean hasAllMaterials = true;
                    StringBuilder missingMaterials = new StringBuilder();

                    // Check if all required materials are provided
                    for (Map.Entry<Material, Integer> entry : requiredMaterials.entrySet()) {
                        Material material = entry.getKey();
                        int required = entry.getValue();
                        int placeholderSlot = placeholderSlots.getOrDefault(material, -1);

                        if (placeholderSlot != -1) {
                            ItemStack slotItem = event.getInventory().getItem(placeholderSlot);
                            int provided = 0;

                            // Check if the slot has the required material
                            if (slotItem != null && slotItem.getType() == material) {
                                provided = slotItem.getAmount();
                            }

                            if (provided < required) {
                                hasAllMaterials = false;
                                missingMaterials.append("\n").append(ChatColor.RED)
                                        .append(formatMaterialName(material.name())).append(": ")
                                        .append(provided).append("/").append(required);
                            }
                        }
                    }

                    if (hasAllMaterials) {
                        // Process the upgrade
                        int currentTier = armorStand.getPersistentDataContainer().getOrDefault(plugin.tierKey, PersistentDataType.INTEGER, 1);
                        armorStand.getPersistentDataContainer().set(plugin.tierKey, PersistentDataType.INTEGER, targetTier);

                        // *** MINION STORAGE UPGRADE LOGIC STARTS HERE ***

// 1. Get the minion's type to look up the correct config section
                        String minionTypeStr = armorStand.getPersistentDataContainer().getOrDefault(
                                plugin.minionTypeKey, PersistentDataType.STRING, "miner" // Default to "miner" if not set
                        );

// 2. Get the new storage size from the UpgradeManager using the method from Step 1
                        int newSize = plugin.getUpgradeManager().getStorageSizeForTier(minionTypeStr, targetTier);

// 3. Get the old storage inventory
                        Inventory oldStorage = plugin.getMinionStorage(minionUUID);

// 4. Create a new inventory with the larger size (This requires the fix to StorageHolder)
                        Inventory newStorage = Bukkit.createInventory(new MinionPlugin.StorageHolder(minionUUID), newSize, ChatColor.AQUA + "Minion Storage");

// 5. Copy items from the old inventory to the new one
                        if (oldStorage != null) {
                            for (int i = 0; i < Math.min(oldStorage.getSize(), newStorage.getSize()); i++) {
                                ItemStack item = oldStorage.getItem(i);
                                // Copy the item if it's not a UI button
                                if (item != null && item.getType() != Material.BARRIER && item.getType() != Material.HOPPER && item.getType() != Material.RED_STAINED_GLASS_PANE) {
                                    newStorage.setItem(i, item);
                                }
                            }
                        }

// 6. Set the new storage inventory for the minion and add the UI buttons back
                        plugin.setMinionStorage(minionUUID, newStorage);
                        plugin.setupMinionStorageUI(newStorage); // Re-apply UI buttons like "Back" and "Collect"


                        // Consume the materials (they're already in the GUI)
                        player.sendMessage(ChatColor.GREEN + "Minion upgraded from Tier " + currentTier + " to Tier " + targetTier + "!");
                        player.openInventory(minion.getActionInventory());
                    } else {
                        player.sendMessage(ChatColor.RED + "Missing materials for upgrade:" + missingMaterials);
                    }
                }
                else if (event.getCurrentItem().getType() == Material.RED_STAINED_GLASS_PANE && event.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase(ChatColor.RED + "cancel")) {
                    // return materials to player's inventory
                    for (Material material : requiredMaterials.keySet()) {
                        int slot = placeholderSlots.getOrDefault(material, -1);
                        if (slot == -1) { // this means that its -1 which means filled in those slots!
                            int firstUpgradeSlot = 20;
                            int secondUpgradeSlot = 22;
                            int thirdUpgradeSlot = 24;

                            ItemStack firstUpgradeItem = event.getInventory().getItem(firstUpgradeSlot);
                            ItemStack secondUpgradeItem = event.getInventory().getItem(secondUpgradeSlot);
                            ItemStack thirdUpgradeItem = event.getInventory().getItem(thirdUpgradeSlot);

                            HashMap<Integer, ItemStack> leftover = new HashMap<>();
                            if (firstUpgradeItem != null && firstUpgradeItem.getType() != Material.LIGHT_BLUE_STAINED_GLASS_PANE) {
                                leftover.put(firstUpgradeSlot, firstUpgradeItem);
                            }
                            if (secondUpgradeItem != null && secondUpgradeItem.getType() != Material.LIGHT_BLUE_STAINED_GLASS_PANE) {
                                leftover.put(secondUpgradeSlot, secondUpgradeItem);
                            }
                            if (thirdUpgradeItem != null && thirdUpgradeItem.getType() != Material.LIGHT_BLUE_STAINED_GLASS_PANE) {
                                leftover.put(thirdUpgradeSlot, thirdUpgradeItem);
                            }

                            Inventory guiInventory = event.getInventory();
                            for (Map.Entry<Integer, ItemStack> entry : leftover.entrySet()) {
                                int slotNumber = entry.getKey();
                                ItemStack itemToReturn = entry.getValue();
                                player.getInventory().addItem(itemToReturn);
                                player.sendMessage(ChatColor.GREEN + "Returned item: " + itemToReturn.getType());
                                guiInventory.setItem(slotNumber, null);

                            }
                            leftover.clear();

                            player.openInventory(minion.getActionInventory());
                        }
                    }
                    player.sendMessage(ChatColor.YELLOW + "Upgrade cancelled.");
                    player.openInventory(minion.getActionInventory());
                }
                return;
            }

            // Handle shift-click from player inventory to GUI
            if (event.getClickedInventory() == player.getInventory() &&
                    event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                event.setCancelled(true);

                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

                // Check if this is a required material
                Material material = clickedItem.getType();
                if (requiredMaterials.containsKey(material)) {
                    int placeholderSlot = placeholderSlots.getOrDefault(material, -1);
                    if (placeholderSlot != -1) {
                        int requiredAmount = requiredMaterials.get(material);

                        // Check existing item in slot
                        ItemStack existingItem = event.getInventory().getItem(placeholderSlot);
                        int alreadyPlaced = 0;

                        if (existingItem != null && existingItem.getType() == material) {
                            alreadyPlaced = existingItem.getAmount();
                        } else if (existingItem != null && existingItem.getType() == Material.LIGHT_BLUE_STAINED_GLASS_PANE) {
                            // Clear the placeholder
                            event.getInventory().setItem(placeholderSlot, null);
                            existingItem = null;
                        }

                        int stillNeeded = requiredAmount - alreadyPlaced;
                        if (stillNeeded <= 0) return;

                        int toTransfer = Math.min(clickedItem.getAmount(), stillNeeded);

                        // Create or update stack in placeholder slot
                        if (existingItem == null || existingItem.getType().isAir()) {
                            event.getInventory().setItem(placeholderSlot, new ItemStack(material, toTransfer));
                        } else {
                            existingItem.setAmount(existingItem.getAmount() + toTransfer);
                        }

                        // Update player's inventory
                        if (toTransfer >= clickedItem.getAmount()) {
                            player.getInventory().setItem(event.getSlot(), null);
                        } else {
                            clickedItem.setAmount(clickedItem.getAmount() - toTransfer);
                        }

                        player.updateInventory();
                    }
                }
                return;
            }

            // Handle clicks on blue panes for direct placement
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.LIGHT_BLUE_STAINED_GLASS_PANE) {
                ItemStack cursorItem = player.getItemOnCursor();
                if (cursorItem != null && !cursorItem.getType().isAir()) {
                    Material cursorMaterial = cursorItem.getType();

                    // Verify this is the right placeholder for this material
                    for (Map.Entry<Material, Integer> entry : placeholderSlots.entrySet()) {
                        if (entry.getValue() == event.getRawSlot() && entry.getKey() == cursorMaterial) {
                            // Allow placing the material
                            event.setCancelled(false);
                            return;
                        }
                    }
                }
            }
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
