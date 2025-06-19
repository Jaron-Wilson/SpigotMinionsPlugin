package me.jaron.minion;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
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
    private final Map<UUID, BundleView> playerBundleView = new HashMap<>();

    private final NamespacedKey bundleKey;
    private static final int ITEMS_PER_PAGE = 45;

    public enum BundleView {
        RAW_ITEMS,
        CATEGORIES
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
    }

    public boolean isBundle(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(bundleKey, PersistentDataType.BYTE);
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
                .collect(Collectors.toList());

        int page = categoriesCurrentPage.computeIfAbsent(player.getUniqueId(), k -> 0);
        int totalPages = Math.max(1, (int) Math.ceil(sortedCategories.size() / (double) ITEMS_PER_PAGE));

        Inventory inv = Bukkit.createInventory(new CategoriesHolder(player.getUniqueId()), 54,
                ChatColor.GOLD + "Minion Bundle " + ChatColor.GRAY + "(Page " + (page + 1) + "/" + totalPages + ")");

        int startIndex = page * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE && startIndex + i < sortedCategories.size(); i++) {
            Map.Entry<Material, Integer> entry = sortedCategories.get(startIndex + i);
            ItemStack displayItem = new ItemStack(entry.getKey());
            ItemMeta meta = displayItem.getItemMeta();
            meta.setDisplayName(ChatColor.AQUA + formatMaterialName(entry.getKey()));
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Total: " + ChatColor.YELLOW + entry.getValue(),
                    "",
                    ChatColor.GREEN + "Click to withdraw."
            ));
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            displayItem.setItemMeta(meta);
            inv.setItem(i, displayItem);
        }

        setupNavigationButtons(inv, page, totalPages, BundleView.CATEGORIES);
        return inv;
    }

    public Inventory getCategoryConfirmationGUI(Player player, Material material, int totalAmount) {
        Inventory inv = Bukkit.createInventory(new CategoryConfirmationHolder(player.getUniqueId(), material, totalAmount), 27, "Confirm Withdrawal");

        ItemStack displayItem = new ItemStack(material);
        ItemMeta meta = displayItem.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + formatMaterialName(material));
        meta.setLore(Collections.singletonList(ChatColor.GRAY + "Amount: " + ChatColor.YELLOW + totalAmount));
        displayItem.setItemMeta(meta);
        inv.setItem(13, displayItem);

        ItemStack confirm = new ItemStack(Material.GREEN_WOOL);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setDisplayName(ChatColor.GREEN + "Confirm");
        confirm.setItemMeta(confirmMeta);
        inv.setItem(11, confirm);

        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.RED + "Cancel");
        cancel.setItemMeta(cancelMeta);
        inv.setItem(15, cancel);

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
        if (currentPage > 0) {
            ItemStack prevButton = new ItemStack(Material.ARROW);
            ItemMeta meta = prevButton.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + "Previous Page");
                prevButton.setItemMeta(meta);
            }
            inv.setItem(45, prevButton);
        }

        if (currentPage < totalPages - 1) {
            ItemStack nextButton = new ItemStack(Material.ARROW);
            ItemMeta meta = nextButton.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + "Next Page");
                nextButton.setItemMeta(meta);
            }
            inv.setItem(53, nextButton);
        }

        if (currentView == BundleView.CATEGORIES) {
            ItemStack rawViewButton = new ItemStack(Material.ITEM_FRAME);
            ItemMeta meta = rawViewButton.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.AQUA + "View Raw Items");
                meta.setLore(Collections.singletonList(ChatColor.GRAY + "View all items individually."));
                rawViewButton.setItemMeta(meta);
            }
            inv.setItem(48, rawViewButton);
        } else { // RAW_ITEMS
            ItemStack catViewButton = new ItemStack(Material.CHEST);
            ItemMeta meta = catViewButton.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GREEN + "View Categories");
                meta.setLore(Collections.singletonList(ChatColor.GRAY + "Return to categories view."));
                catViewButton.setItemMeta(meta);
            }
            inv.setItem(48, catViewButton);
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
        Map<UUID, Integer> pageMap = view == BundleView.RAW_ITEMS ? rawItemsCurrentPage : categoriesCurrentPage;
        int current = pageMap.getOrDefault(player.getUniqueId(), 0);
        pageMap.put(player.getUniqueId(), current + 1);
        refreshInventory(player);
    }

    public void previousPage(Player player) {
        BundleView view = playerBundleView.getOrDefault(player.getUniqueId(), BundleView.CATEGORIES);
        Map<UUID, Integer> pageMap = view == BundleView.RAW_ITEMS ? rawItemsCurrentPage : categoriesCurrentPage;
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
        @Override public Inventory getInventory() { return null; }
    }

    public static class CategoriesHolder implements org.bukkit.inventory.InventoryHolder {
        private final UUID playerUUID;
        public CategoriesHolder(UUID playerUUID) { this.playerUUID = playerUUID; }
        public UUID getPlayerUUID() { return playerUUID; }
        @Override public Inventory getInventory() { return null; }
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
        @Override public Inventory getInventory() { return null; }
    }
}
