package me.jaron.minion;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.stream.Collectors;

public class MinionBundleManager {
    private final MinionPlugin plugin;
    private final Map<UUID, List<ItemStack>> bundleContents = new HashMap<>();
    private final Map<UUID, Integer> rawItemsCurrentPage = new HashMap<>();
    private final Map<UUID, Integer> categoriesCurrentPage = new HashMap<>();
    private final Map<UUID, Integer> minionsCurrentPage = new HashMap<>();
    private final Map<UUID, BundleView> playerBundleView = new HashMap<>();

    private final NamespacedKey bundleKey;
    private static final int ITEMS_PER_PAGE = 45;

    public enum BundleView {
        RAW_ITEMS,
        CATEGORIES,
        MINIONS
    }

    public MinionBundleManager(MinionPlugin plugin) {
        this.plugin = plugin;
        this.bundleKey = new NamespacedKey(plugin, "minion_bundle");
    }

    public void createBundle(Player player) {
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        ItemMeta meta = bundle.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Minion Collection Bundle");
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Right-click to open", ChatColor.GRAY + "Stores unlimited items!"));
            meta.getPersistentDataContainer().set(bundleKey, PersistentDataType.BYTE, (byte) 1);
            bundle.setItemMeta(meta);
        }
        player.getInventory().addItem(bundle);
        bundleContents.putIfAbsent(player.getUniqueId(), new ArrayList<>());
        rawItemsCurrentPage.putIfAbsent(player.getUniqueId(), 0);
        categoriesCurrentPage.putIfAbsent(player.getUniqueId(), 0);
        minionsCurrentPage.putIfAbsent(player.getUniqueId(), 0);
    }

    public boolean isBundle(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(bundleKey, PersistentDataType.BYTE);
    }

    public void openBundle(Player player) {
        playerBundleView.put(player.getUniqueId(), BundleView.CATEGORIES);
        player.openInventory(getCategoriesInventory(player));
    }

    public Inventory getRawItemsInventory(Player player) {
        playerBundleView.put(player.getUniqueId(), BundleView.RAW_ITEMS);
        List<ItemStack> items = bundleContents.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
        int page = rawItemsCurrentPage.computeIfAbsent(player.getUniqueId(), k -> 0);
        int totalPages = Math.max(1, (int) Math.ceil(items.size() / (double) ITEMS_PER_PAGE));

        Inventory inv = Bukkit.createInventory(new RawItemsHolder(player.getUniqueId()), 54,
                ChatColor.GOLD + "Bundle (Raw) " + ChatColor.GRAY + "(Page " + (page + 1) + "/" + totalPages + ")");

        int startIndex = page * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE && startIndex + i < items.size(); i++) {
            inv.setItem(i, items.get(startIndex + i));
        }

        setupNavigationButtons(inv, page, totalPages, BundleView.RAW_ITEMS);
        return inv;
    }

    public Inventory getCategoriesInventory(Player player) {
        playerBundleView.put(player.getUniqueId(), BundleView.CATEGORIES);
        List<ItemStack> items = bundleContents.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());

        Map<Material, Integer> categoryCounts = new LinkedHashMap<>();
        for (ItemStack item : items) {
            categoryCounts.put(item.getType(), categoryCounts.getOrDefault(item.getType(), 0) + item.getAmount());
        }

        List<Map.Entry<Material, Integer>> sortedCategories = categoryCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Enum::name)))
                .toList();

        int page = categoriesCurrentPage.computeIfAbsent(player.getUniqueId(), k -> 0);
        int totalPages = Math.max(1, (int) Math.ceil(sortedCategories.size() / (double) ITEMS_PER_PAGE));

        Inventory inv = Bukkit.createInventory(new CategoriesHolder(player.getUniqueId()), 54,
                ChatColor.GOLD + "Minion Bundle " + ChatColor.GRAY + "(Page " + (page + 1) + "/" + totalPages + ")");

        int startIndex = page * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE && startIndex + i < sortedCategories.size(); i++) {
            Map.Entry<Material, Integer> entry = sortedCategories.get(startIndex + i);
            ItemStack displayItem = new ItemStack(entry.getKey());
            ItemMeta meta = displayItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.AQUA + formatMaterialName(entry.getKey()));
                meta.setLore(Arrays.asList(
                        ChatColor.GRAY + "Total: " + ChatColor.YELLOW + entry.getValue(),
                        "",
                        ChatColor.GREEN + "Left-Click: " + ChatColor.GRAY + "Withdraw items",
                        ChatColor.RED + "Right-Click: " + ChatColor.GRAY + "Delete items"
                ));
                meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                displayItem.setItemMeta(meta);
            }
            inv.setItem(i, displayItem);
        }

        setupNavigationButtons(inv, page, totalPages, BundleView.CATEGORIES);
        return inv;
    }

    public Inventory getMinionsInventory(Player player) {
        playerBundleView.put(player.getUniqueId(), BundleView.MINIONS);
        List<Minion> minions = plugin.getMinions().get(player.getUniqueId());
        if (minions == null) {
            minions = new ArrayList<>();
        }

        int page = minionsCurrentPage.computeIfAbsent(player.getUniqueId(), k -> 0);
        int totalPages = Math.max(1, (int) Math.ceil(minions.size() / (double) ITEMS_PER_PAGE));

        Inventory inv = Bukkit.createInventory(new MinionsHolder(player.getUniqueId()), 54,
                ChatColor.DARK_AQUA + "My Minions " + ChatColor.GRAY + "(Page " + (page + 1) + "/" + totalPages + ")");

        int startIndex = page * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE && startIndex + i < minions.size(); i++) {
            Minion minion = minions.get(startIndex + i);
            Material targetMaterial = minion.getTargetMaterial();
            ItemStack minionItem;
            if (targetMaterial.isItem()) {
                minionItem = new ItemStack(targetMaterial);
            } else {
                minionItem = new ItemStack(Material.BARRIER);
            }
            ItemMeta meta = minionItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.AQUA + "Minion " + (startIndex + i + 1));
                Location minionLoc = minion.getLocation();
                double distance = -1;
                if (player.getWorld().equals(minionLoc.getWorld())) {
                    distance = player.getLocation().distance(minionLoc);
                }

                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Type: " + ChatColor.YELLOW + minion.getMinionType().name());
                lore.add(ChatColor.GRAY + "Location: " + ChatColor.YELLOW + minionLoc.getBlockX() + ", " + minionLoc.getBlockY() + ", " + minionLoc.getBlockZ());
                if (distance != -1) {
                    lore.add(ChatColor.GRAY + "Distance: " + ChatColor.YELLOW + String.format("%.1f", distance) + " blocks");
                } else {
                    lore.add(ChatColor.GRAY + "Distance: " + ChatColor.YELLOW + "Different world");
                }
                lore.add("");
                lore.add(ChatColor.GREEN + "Click for options.");
                meta.setLore(lore);
                minionItem.setItemMeta(meta);
            }
            inv.setItem(i, minionItem);
        }

        setupNavigationButtons(inv, page, totalPages, BundleView.MINIONS);
        return inv;
    }

    public Inventory getCategoryConfirmationGUI(Player player, Material material, int totalAmount) {
        Inventory inv = Bukkit.createInventory(new CategoryConfirmationHolder(player.getUniqueId(), material, totalAmount), 27, "Confirm Withdrawal");

        ItemStack displayItem = new ItemStack(material);
        ItemMeta meta = displayItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + formatMaterialName(material));
            meta.setLore(Collections.singletonList(ChatColor.GRAY + "Amount: " + ChatColor.YELLOW + totalAmount));
            displayItem.setItemMeta(meta);
        }
        inv.setItem(13, displayItem);

        ItemStack confirm = new ItemStack(Material.GREEN_WOOL);
        ItemMeta confirmMeta = confirm.getItemMeta();
        if (confirmMeta != null) {
            confirmMeta.setDisplayName(ChatColor.GREEN + "Confirm");
            confirm.setItemMeta(confirmMeta);
        }
        inv.setItem(11, confirm);

        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setDisplayName(ChatColor.RED + "Cancel");
            cancel.setItemMeta(cancelMeta);
        }
        inv.setItem(15, cancel);

        return inv;
    }

    /**
     * Opens a GUI with options to delete all or a fraction of items in a category
     */
    public Inventory getPartialDeletionGUI(Player player, Material material, int totalAmount) {
        Inventory inv = Bukkit.createInventory(new PartialDeletionHolder(player.getUniqueId(), material, totalAmount), 36,
                ChatColor.RED + "Delete Items: " + ChatColor.AQUA + formatMaterialName(material));

        // Display info about the category
        ItemStack infoItem = new ItemStack(material);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(ChatColor.GOLD + formatMaterialName(material));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "You have " + ChatColor.YELLOW + totalAmount + ChatColor.GRAY + " items");
            lore.add(ChatColor.GRAY + "Choose how many to delete:");
            infoMeta.setLore(lore);
            infoItem.setItemMeta(infoMeta);
        }
        inv.setItem(4, infoItem);

        // Delete all option
        ItemStack deleteAll = new ItemStack(Material.TNT);
        ItemMeta deleteAllMeta = deleteAll.getItemMeta();
        if (deleteAllMeta != null) {
            deleteAllMeta.setDisplayName(ChatColor.RED + "Delete ALL Items");
            deleteAllMeta.setLore(Collections.singletonList(ChatColor.GRAY + "Delete all " + totalAmount + " items"));
            deleteAll.setItemMeta(deleteAllMeta);
        }
        inv.setItem(19, deleteAll);

        // Delete 1/2 option
        ItemStack deleteHalf = new ItemStack(Material.RED_CONCRETE);
        ItemMeta deleteHalfMeta = deleteHalf.getItemMeta();
        if (deleteHalfMeta != null) {
            int amount = totalAmount / 2;
            deleteHalfMeta.setDisplayName(ChatColor.RED + "Delete 1/2");
            deleteHalfMeta.setLore(Collections.singletonList(ChatColor.GRAY + "Delete " + amount + " items"));
            deleteHalf.setItemMeta(deleteHalfMeta);
        }
        inv.setItem(21, deleteHalf);

        // Delete 1/4 option
        ItemStack deleteQuarter = new ItemStack(Material.ORANGE_CONCRETE);
        ItemMeta deleteQuarterMeta = deleteQuarter.getItemMeta();
        if (deleteQuarterMeta != null) {
            int amount = totalAmount / 4;
            deleteQuarterMeta.setDisplayName(ChatColor.GOLD + "Delete 1/4");
            deleteQuarterMeta.setLore(Collections.singletonList(ChatColor.GRAY + "Delete " + amount + " items"));
            deleteQuarter.setItemMeta(deleteQuarterMeta);
        }
        inv.setItem(23, deleteQuarter);

        // Delete 1/8 option
        ItemStack deleteEighth = new ItemStack(Material.YELLOW_CONCRETE);
        ItemMeta deleteEighthMeta = deleteEighth.getItemMeta();
        if (deleteEighthMeta != null) {
            int amount = totalAmount / 8;
            deleteEighthMeta.setDisplayName(ChatColor.YELLOW + "Delete 1/8");
            deleteEighthMeta.setLore(Collections.singletonList(ChatColor.GRAY + "Delete " + amount + " items"));
            deleteEighth.setItemMeta(deleteEighthMeta);
        }
        inv.setItem(25, deleteEighth);

        // Cancel button
        ItemStack cancel = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancel.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setDisplayName(ChatColor.GREEN + "Cancel");
            cancel.setItemMeta(cancelMeta);
        }
        inv.setItem(31, cancel);

        return inv;
    }

    public void withdrawFromBundle(Player player, Material material) {
        List<ItemStack> items = bundleContents.get(player.getUniqueId());
        if (items == null) return;

        int totalToWithdraw = items.stream()
                .filter(item -> item.getType() == material)
                .mapToInt(ItemStack::getAmount)
                .sum();

        int spaceAvailable = 0;
        for (ItemStack invItem : player.getInventory().getStorageContents()) {
            if (invItem == null || invItem.getType() == Material.AIR) {
                spaceAvailable += material.getMaxStackSize();
            } else if (invItem.getType() == material) {
                spaceAvailable += invItem.getMaxStackSize() - invItem.getAmount();
            }
        }

        int amountToGive = Math.min(totalToWithdraw, spaceAvailable);
        if (amountToGive <= 0) {
            player.sendMessage(ChatColor.RED + "You don't have enough space in your inventory!");
            return;
        }

        // Remove from bundle
        int amountRemoved = 0;
        ListIterator<ItemStack> iterator = items.listIterator(items.size());
        while (iterator.hasPrevious() && amountRemoved < amountToGive) {
            ItemStack current = iterator.previous();
            if (current.getType() == material) {
                int toRemove = Math.min(amountToGive - amountRemoved, current.getAmount());
                current.setAmount(current.getAmount() - toRemove);
                amountRemoved += toRemove;
                if (current.getAmount() <= 0) {
                    iterator.remove();
                }
            }
        }

        // Give to player
        int remainingToGive = amountToGive;
        while (remainingToGive > 0) {
            int stackSize = Math.min(remainingToGive, material.getMaxStackSize());
            player.getInventory().addItem(new ItemStack(material, stackSize));
            remainingToGive -= stackSize;
        }
        player.sendMessage(ChatColor.GREEN + "Withdrew " + amountToGive + " " + formatMaterialName(material) + ".");

        // Refresh inventory if open
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof CategoriesHolder) {
            categoriesCurrentPage.put(player.getUniqueId(), 0); // Reset to first page
            player.openInventory(getCategoriesInventory(player));
        }
    }


    private String formatMaterialName(Material material) {
        return Arrays.stream(material.name().split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    private void setupNavigationButtons(Inventory inv, int currentPage, int totalPages, BundleView currentView) {
        // Previous page button
        if (currentPage > 0) {
            ItemStack prevButton = new ItemStack(Material.ARROW);
            ItemMeta meta = prevButton.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + "Previous Page");
                prevButton.setItemMeta(meta);
            }
            inv.setItem(45, prevButton);
        }

        // Next page button
        if (currentPage < totalPages - 1) {
            ItemStack nextButton = new ItemStack(Material.ARROW);
            ItemMeta meta = nextButton.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + "Next Page");
                nextButton.setItemMeta(meta);
            }
            inv.setItem(53, nextButton);
        }

        // Add Delete All Minions button when in Minions view
        if (currentView == BundleView.MINIONS) {
            ItemStack deleteAllButton = getStack();
            // Changed from slot 49 to slot 51 to avoid conflict with Collect All button
            inv.setItem(51, deleteAllButton);
        }

        if (currentView == BundleView.CATEGORIES) {
            ItemStack rawViewButton = new ItemStack(Material.ITEM_FRAME);
            ItemMeta meta = rawViewButton.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.AQUA + "View Raw Items");
                meta.setLore(
                        Arrays.asList(
                                ChatColor.GRAY + "View all items individually.",
                                ChatColor.AQUA + "Just for show :)"
                        ));
                rawViewButton.setItemMeta(meta);
            }
            inv.setItem(48, rawViewButton);
        } else { // RAW_ITEMS or MINIONS
            ItemStack catViewButton = new ItemStack(Material.CHEST);
            ItemMeta meta = catViewButton.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GREEN + "View Categories");
                meta.setLore(Collections.singletonList(ChatColor.GRAY + "Return to categories view."));
                catViewButton.setItemMeta(meta);
            }
            inv.setItem(48, catViewButton);
        }

        if (currentView != BundleView.MINIONS) {
            ItemStack minionsButton = new ItemStack(Material.CREEPER_HEAD);
            ItemMeta minionsMeta = minionsButton.getItemMeta();
            if (minionsMeta != null) {
                minionsMeta.setDisplayName(ChatColor.DARK_AQUA + "My Minions");
                minionsMeta.setLore(Collections.singletonList(ChatColor.GRAY + "Click to view and teleport to your minions."));
                minionsButton.setItemMeta(minionsMeta);
            }
            inv.setItem(50, minionsButton);
        }

        ItemStack collectButton = new ItemStack(Material.HOPPER);
        ItemMeta meta = collectButton.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "Collect All");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Click to collect items",
                    ChatColor.GRAY + "from all your minions"
            ));
            collectButton.setItemMeta(meta);
        }
        inv.setItem(49, collectButton);
    }

    private static ItemStack getStack() {
        ItemStack deleteAllButton = getItemStack();
        ItemMeta deleteAllMeta = deleteAllButton.getItemMeta();
        if (deleteAllMeta != null) {
            deleteAllMeta.setDisplayName(ChatColor.RED + "Delete All Minions");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Delete all your minions");
            lore.add(ChatColor.GRAY + "and transfer their items");
            lore.add(ChatColor.GRAY + "to your bundle.");
            lore.add("");
            lore.add(ChatColor.RED + "Warning: This cannot be undone!");
            deleteAllMeta.setLore(lore);
            deleteAllButton.setItemMeta(deleteAllMeta);
        }
        return deleteAllButton;
    }

    private static ItemStack getItemStack() {
        ItemStack deleteAllButton = new ItemStack(Material.TNT);
        ItemMeta deleteAllMeta = deleteAllButton.getItemMeta();
        if (deleteAllMeta != null) {
            deleteAllMeta.setDisplayName(ChatColor.RED + "Delete All Minions");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Delete all your minions");
            lore.add(ChatColor.GRAY + "and transfer their items");
            lore.add(ChatColor.GRAY + "to your bundle.");
            lore.add("");
            lore.add(ChatColor.RED + "Warning: This cannot be undone!");
            deleteAllMeta.setLore(lore);
            deleteAllButton.setItemMeta(deleteAllMeta);
        }
        return deleteAllButton;
    }

    public void addItemToBundle(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        List<ItemStack> items = bundleContents.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
        ItemStack toAdd = item.clone();

        // Try to merge with existing stacks that are not full
        for (ItemStack existingItem : items) {
            if (toAdd.getAmount() > 0 && existingItem.isSimilar(toAdd) && existingItem.getAmount() < existingItem.getMaxStackSize()) {
                int canAdd = existingItem.getMaxStackSize() - existingItem.getAmount();
                int amountToAdd = Math.min(toAdd.getAmount(), canAdd);
                existingItem.setAmount(existingItem.getAmount() + amountToAdd);
                toAdd.setAmount(toAdd.getAmount() - amountToAdd);
            }
        }

        // If there's still some left, add as new stacks
        while (toAdd.getAmount() > 0) {
            int stackSize = Math.min(toAdd.getAmount(), toAdd.getMaxStackSize());
            ItemStack newStack = toAdd.clone();
            newStack.setAmount(stackSize);
            items.add(newStack);
            toAdd.setAmount(toAdd.getAmount() - stackSize);
        }

        if (player.getOpenInventory().getTopInventory().getHolder() instanceof RawItemsHolder ||
            player.getOpenInventory().getTopInventory().getHolder() instanceof CategoriesHolder) {
            refreshInventory(player);
        }
    }

    public void nextPage(Player player) {
        BundleView view = playerBundleView.getOrDefault(player.getUniqueId(), BundleView.CATEGORIES);
        Map<UUID, Integer> pageMap = view == BundleView.RAW_ITEMS ? rawItemsCurrentPage : view == BundleView.CATEGORIES ? categoriesCurrentPage : minionsCurrentPage;
        int current = pageMap.getOrDefault(player.getUniqueId(), 0);
        pageMap.put(player.getUniqueId(), current + 1);
        refreshInventory(player);
    }

    public int getMinionsCurrentPage(UUID playerUUID) {
        return minionsCurrentPage.getOrDefault(playerUUID, 0);
    }

    public void previousPage(Player player) {
        BundleView view = playerBundleView.getOrDefault(player.getUniqueId(), BundleView.CATEGORIES);
        Map<UUID, Integer> pageMap = view == BundleView.RAW_ITEMS ? rawItemsCurrentPage : view == BundleView.CATEGORIES ? categoriesCurrentPage : minionsCurrentPage;
        int current = pageMap.getOrDefault(player.getUniqueId(), 0);
        if (current > 0) {
            pageMap.put(player.getUniqueId(), current - 1);
            refreshInventory(player);
        }
    }

    public void refreshInventory(Player player) {
        BundleView view = playerBundleView.getOrDefault(player.getUniqueId(), BundleView.CATEGORIES);
        switch (view) {
            case RAW_ITEMS:
                player.openInventory(getRawItemsInventory(player));
                break;
            case CATEGORIES:
                player.openInventory(getCategoriesInventory(player));
                break;
            case MINIONS:
                player.openInventory(getMinionsInventory(player));
                break;
        }
    }

    public void openRawItemsView(Player player) {
        playerBundleView.put(player.getUniqueId(), BundleView.RAW_ITEMS);
        player.openInventory(getRawItemsInventory(player));
    }

    public void openCategoriesView(Player player) {
        playerBundleView.put(player.getUniqueId(), BundleView.CATEGORIES);
        player.openInventory(getCategoriesInventory(player));
    }

    public void openMinionsView(Player player) {
        playerBundleView.put(player.getUniqueId(), BundleView.MINIONS);
        player.openInventory(getMinionsInventory(player));
    }

    public void saveData(YamlConfiguration config) {
        for (Map.Entry<UUID, List<ItemStack>> entry : bundleContents.entrySet()) {
            String path = "bundles." + entry.getKey().toString() + ".items";
            List<ItemStack> items = entry.getValue();
            config.set(path, null); // Clear old list/section
            for (int i = 0; i < items.size(); i++) {
                config.set(path + "." + i, items.get(i));
            }
        }
    }

    public void loadData(YamlConfiguration config) {
        bundleContents.clear();
        if (!config.contains("bundles")) return;
        ConfigurationSection bundlesSection = config.getConfigurationSection("bundles");
        if (bundlesSection == null) return;
        for (String uuidStr : bundlesSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                String itemsPath = "bundles." + uuidStr + ".items";
                List<ItemStack> items = new ArrayList<>();
                if (config.isConfigurationSection(itemsPath)) {
                    ConfigurationSection itemsSection = config.getConfigurationSection(itemsPath);
                    if (itemsSection != null) {
                        for (String key : itemsSection.getKeys(false)) {
                            ItemStack item = itemsSection.getItemStack(key);
                            if (item != null && !item.getType().isAir()) {
                                items.add(item);
                            }
                        }
                    }
                }
                bundleContents.put(uuid, items);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in bundle storage: " + uuidStr);
            }
        }
    }


    // --- Inventory Holders ---

    public static class RawItemsHolder implements org.bukkit.inventory.InventoryHolder {
        private final UUID playerUUID;
        public RawItemsHolder(UUID playerUUID) { this.playerUUID = playerUUID; }
        public UUID getPlayerUUID() { return playerUUID; }
        @Override public Inventory getInventory() { return Bukkit.createInventory(this, 0); }
    }

    public static class CategoriesHolder implements org.bukkit.inventory.InventoryHolder {
        private final UUID playerUUID;
        public CategoriesHolder(UUID playerUUID) { this.playerUUID = playerUUID; }
        public UUID getPlayerUUID() { return playerUUID; }
        @Override public Inventory getInventory() { return Bukkit.createInventory(this, 0); }
    }

    public static class MinionsHolder implements org.bukkit.inventory.InventoryHolder {
        private final UUID playerUUID;
        public MinionsHolder(UUID playerUUID) { this.playerUUID = playerUUID; }
        public UUID getPlayerUUID() { return playerUUID; }
        @Override public Inventory getInventory() { return Bukkit.createInventory(this, 0); }
    }

    public static class CategoryConfirmationHolder implements org.bukkit.inventory.InventoryHolder {
        private final UUID playerUUID;
        private final Material material;
        private final int amount;
        public CategoryConfirmationHolder(UUID playerUUID, Material material, int amount) {
            this.playerUUID = playerUUID;
            this.material = material;
            this.amount = amount;
        }
        public UUID getPlayerUUID() { return playerUUID; }
        public Material getMaterial() { return material; }
        public int getAmount() { return amount; }
        @Override public Inventory getInventory() { return Bukkit.createInventory(this, 0); }
    }

    // Inner class for partial deletion confirmation GUI
    public class PartialDeletionHolder implements InventoryHolder {
        private final UUID playerUUID;
        private final Material material;
        private final int totalAmount;

        public PartialDeletionHolder(UUID playerUUID, Material material, int totalAmount) {
            this.playerUUID = playerUUID;
            this.material = material;
            this.totalAmount = totalAmount;
        }

        public UUID getPlayerUUID() {
            return playerUUID;
        }

        public Material getMaterial() {
            return material;
        }

        public int getTotalAmount() {
            return totalAmount;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    /**
     * Deletes a specific amount of items of a given material from the bundle
     *
     * @param player The player whose bundle to modify
     * @param material The material type to delete
     * @param amountToDelete The amount of items to delete
     */
    public void deleteFromBundle(Player player, Material material, int amountToDelete) {
        List<ItemStack> items = bundleContents.get(player.getUniqueId());
        if (items == null || items.isEmpty()) return;

        int amountDeleted = 0;
        ListIterator<ItemStack> iterator = items.listIterator(items.size());
        while (iterator.hasPrevious() && amountDeleted < amountToDelete) {
            ItemStack current = iterator.previous();
            if (current.getType() == material) {
                int toRemove = Math.min(amountToDelete - amountDeleted, current.getAmount());
                current.setAmount(current.getAmount() - toRemove);
                amountDeleted += toRemove;
                if (current.getAmount() <= 0) {
                    iterator.remove();
                }
            }
        }

        player.sendMessage(ChatColor.GREEN + "Deleted " + amountDeleted + " " + formatMaterialName(material) + " from your bundle.");

        // Refresh inventory if open
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof CategoriesHolder) {
            player.openInventory(getCategoriesInventory(player));
        }
    }

    public Map<UUID, List<ItemStack>> getBundleContents() {
        return bundleContents;
    }

    public Map<UUID, Integer> getRawItemsCurrentPage() {
        return rawItemsCurrentPage;
    }

    public Map<UUID, Integer> getCategoriesCurrentPage() {
        return categoriesCurrentPage;
    }

    public Map<UUID, Integer> getMinionsCurrentPage() {
        return minionsCurrentPage;
    }

    public Map<UUID, BundleView> getPlayerBundleView() {
        return playerBundleView;
    }

    public NamespacedKey getBundleKey() {
        return bundleKey;
    }
}
