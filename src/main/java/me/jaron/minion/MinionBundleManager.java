package me.jaron.minion;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class MinionBundleManager {
    private final MinionPlugin plugin;
    private final Map<UUID, List<ItemStack>> bundleContents = new HashMap<>();
    private final Map<UUID, Integer> currentPage = new HashMap<>();
    private final NamespacedKey bundleKey;
    private static final int ITEMS_PER_PAGE = 45; // Leave space for navigation

    public MinionBundleManager(MinionPlugin plugin) {
        this.plugin = plugin;
        this.bundleKey = new NamespacedKey(plugin, "minion_bundle");
    }

    public void createBundle(Player player) {
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        ItemMeta meta = bundle.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Minion Collection Bundle");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Right-click to open");
            lore.add(ChatColor.GRAY + "Stores unlimited items!");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(bundleKey, PersistentDataType.BYTE, (byte) 1);
            bundle.setItemMeta(meta);
        }
        player.getInventory().addItem(bundle);
        bundleContents.putIfAbsent(player.getUniqueId(), new ArrayList<>());
        currentPage.putIfAbsent(player.getUniqueId(), 0);
    }

    public boolean isBundle(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        return container.has(bundleKey, PersistentDataType.BYTE);
    }

    public Inventory getBundleInventory(Player player) {
        List<ItemStack> items = bundleContents.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
        int page = currentPage.computeIfAbsent(player.getUniqueId(), k -> 0);
        int totalPages = (int) Math.ceil(items.size() / (double) ITEMS_PER_PAGE);

        Inventory inv = Bukkit.createInventory(new BundleHolder(player.getUniqueId()), 54,
                ChatColor.GOLD + "Bundle " + ChatColor.GRAY + "(Page " + (page + 1) + "/" + Math.max(1, totalPages) + ")");

        // Add items for current page
        int startIndex = page * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE && startIndex + i < items.size(); i++) {
            inv.setItem(i, items.get(startIndex + i));
        }

        // Add navigation and collect buttons
        setupNavigationButtons(inv, page, totalPages);

        return inv;
    }

    private void setupNavigationButtons(Inventory inv, int currentPage, int totalPages) {
        // Previous page button (if not on first page)
        if (currentPage > 0) {
            ItemStack prevButton = new ItemStack(Material.ARROW);
            ItemMeta meta = prevButton.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + "Previous Page");
                prevButton.setItemMeta(meta);
            }
            inv.setItem(45, prevButton);
        }

        // Next page button (if not on last page)
        if (currentPage < totalPages - 1) {
            ItemStack nextButton = new ItemStack(Material.ARROW);
            ItemMeta meta = nextButton.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + "Next Page");
                nextButton.setItemMeta(meta);
            }
            inv.setItem(53, nextButton);
        }

        // Collect All button in the middle
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

        // Try to merge with existing stacks first
        boolean merged = false;
        for (ItemStack existing : items) {
            if (existing.isSimilar(item)) {
                int space = existing.getMaxStackSize() - existing.getAmount();
                if (space > 0) {
                    int toAdd = Math.min(space, item.getAmount());
                    existing.setAmount(existing.getAmount() + toAdd);
                    item.setAmount(item.getAmount() - toAdd);
                    merged = true;
                    if (item.getAmount() <= 0) break;
                }
            }
        }

        // If there's still items to add, add as new stack
        if (item.getAmount() > 0) {
            items.add(item.clone());
        }

        // If the player has the bundle inventory open, refresh it
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof BundleHolder) {
            Inventory newInv = getBundleInventory(player);
            player.openInventory(newInv);
        }
    }

    public void nextPage(Player player) {
        int current = currentPage.getOrDefault(player.getUniqueId(), 0);
        int totalPages = (int) Math.ceil(bundleContents.get(player.getUniqueId()).size() / (double) ITEMS_PER_PAGE);
        if (current < totalPages - 1) {
            currentPage.put(player.getUniqueId(), current + 1);
            player.openInventory(getBundleInventory(player));
        }
    }

    public void previousPage(Player player) {
        int current = currentPage.getOrDefault(player.getUniqueId(), 0);
        if (current > 0) {
            currentPage.put(player.getUniqueId(), current - 1);
            player.openInventory(getBundleInventory(player));
        }
    }

    public static class BundleHolder implements org.bukkit.inventory.InventoryHolder {
        private final UUID playerUUID;

        public BundleHolder(UUID playerUUID) {
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
}
