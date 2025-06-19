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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import me.jaron.minion.FarmerState;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

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

        // Handle Farmer Target Select clicks
        if(event.getClickedInventory().getHolder() instanceof FarmerTargetSelectHolder holder) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType().isAir()) return;

            UUID minionUUID = holder.getMinionUUID();
            Entity entity = Bukkit.getServer().getEntity(minionUUID);
            if (!(entity instanceof ArmorStand armorStand)) {
                player.closeInventory();
                return;
            }

            if (clickedItem.getType() == Material.BARRIER) {
                player.openInventory(new Minion(plugin, armorStand).getActionInventory());
                return;
            }

            if (clickedItem.getType() == Material.SUGAR_CANE || clickedItem.getType() == Material.NETHER_WART) {
                player.sendMessage(ChatColor.RED + "Sorry that is unavailable at the moment");
                player.closeInventory();
                return;
            }

            List<Material> validCrops = Arrays.asList(Material.WHEAT, Material.CARROT, Material.POTATO, Material.BEETROOT);
            if (validCrops.contains(clickedItem.getType())) {
                armorStand.getPersistentDataContainer().set(plugin.targetKey, PersistentDataType.STRING, clickedItem.getType().name());
                if (clickedItem.getType() == Material.NETHER_WART) {
                    armorStand.getPersistentDataContainer().set(plugin.farmerStateKey, PersistentDataType.STRING, FarmerState.PLACING_SOUL_SAND.name());
                } else {
                    armorStand.getPersistentDataContainer().set(plugin.farmerStateKey, PersistentDataType.STRING, FarmerState.HOEING.name());
                }
                player.sendMessage(ChatColor.GREEN + "Minion target set to " + clickedItem.getType().name());
                player.openInventory(new Minion(plugin, armorStand).getActionInventory());
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

    /*
    @EventHandler
    public void onFarmerTargetSelectClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof FarmerTargetSelectHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        UUID minionUUID = holder.getMinionUUID();
        Entity entity = Bukkit.getServer().getEntity(minionUUID);
        if (!(entity instanceof ArmorStand armorStand)) {
            player.closeInventory();
            return;
        }

        if (clickedItem.getType() == Material.BARRIER) {
            player.openInventory(new Minion(plugin, armorStand).getActionInventory());
            return;
        }

        List<Material> validCrops = Arrays.asList(Material.WHEAT, Material.CARROT, Material.POTATO, Material.BEETROOT, Material.SUGAR_CANE, Material.NETHER_WART);
        if (validCrops.contains(clickedItem.getType())) {
            armorStand.getPersistentDataContainer().set(plugin.targetKey, PersistentDataType.STRING, clickedItem.getType().name());
            player.sendMessage(ChatColor.GREEN + "Minion target set to " + clickedItem.getType().name());
            player.openInventory(new Minion(plugin, armorStand).getActionInventory());
        }
    }
    */

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

            if (newType == MinionType.BLOCK_MINER) {
                armorStand.getPersistentDataContainer().set(plugin.targetKey, PersistentDataType.STRING, Material.COBBLESTONE.name());
            } else { // FARMER
                armorStand.getPersistentDataContainer().set(plugin.targetKey, PersistentDataType.STRING, Material.WHEAT.name());
                armorStand.getPersistentDataContainer().set(plugin.farmerStateKey, PersistentDataType.STRING, FarmerState.HOEING.name());
            }
            player.sendMessage(ChatColor.GREEN + "Minion type switched to " + newType.name());
            if (newType == MinionType.FARMER) {
                player.openInventory(getFarmerTargetSelectGUI(minionUUID));
            } else {
                player.openInventory(minion.getActionInventory());
            }
        } else if (clickedItem.getType() == Material.RED_WOOL) {
            player.openInventory(minion.getActionInventory());
        }
    }
}
