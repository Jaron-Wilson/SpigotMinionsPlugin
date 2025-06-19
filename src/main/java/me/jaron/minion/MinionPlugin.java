package me.jaron.minion;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;
import java.util.Objects;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.meta.ItemMeta;

public class MinionPlugin extends JavaPlugin {

    public final NamespacedKey ownerKey = new NamespacedKey(this, "minion_owner");
    public final NamespacedKey isMinionKey = new NamespacedKey(this, "is_minion");
    public final NamespacedKey minionEggKey = new NamespacedKey(this, "minion_spawn_egg");
    public final NamespacedKey tierKey = new NamespacedKey(this, "minion_tier");
    public final NamespacedKey modeKey = new NamespacedKey(this, "minion_mode");
    public final NamespacedKey indexKey = new NamespacedKey(this, "minion_index");
    public final NamespacedKey targetKey = new NamespacedKey(this, "minion_target");

    private final Set<UUID> automationPlayers = new HashSet<>();
    private final Map<UUID, Inventory> minionInventories = new HashMap<>();
    private MinionBundleManager bundleManager;

    public Inventory getMinionStorage(UUID uuid) {
        return minionInventories.computeIfAbsent(uuid, u -> {
            Entity e = Bukkit.getServer().getEntity(u);
            int tier = 1;
            if (e instanceof ArmorStand as) {
                tier = as.getPersistentDataContainer().getOrDefault(tierKey, PersistentDataType.INTEGER, 1);
            }
            int size = Math.min(tier * 9, 54);
            return Bukkit.createInventory(new StorageHolder(u), size, ChatColor.AQUA + "Minion Storage");
        });
    }

    public class StorageHolder implements InventoryHolder {
        private final UUID uuid;
        public StorageHolder(UUID uuid) { this.uuid = uuid; }
        public UUID getMinionUUID() { return uuid; }
        @Override
        public Inventory getInventory() { return minionInventories.get(uuid); }
    }

    @Override
    public void onEnable() {
        // Initialize bundle manager first
        bundleManager = new MinionBundleManager(this);

        // Load all saved data
        loadAllData();

        // Register event listeners
        getServer().getPluginManager().registerEvents(new ArmorStandClicked(this), this);
        getServer().getPluginManager().registerEvents(new InventoryClick(this), this);
        getServer().getPluginManager().registerEvents(new MinionSpawnListener(this), this);
        getServer().getPluginManager().registerEvents(new BundleInteractListener(this), this);

        // Register commands
        Objects.requireNonNull(getCommand("createminion")).setExecutor(new CreateMinionCommand(this));
        Objects.requireNonNull(getCommand("showminioninventory")).setExecutor(new ShowMinionInventoryCommand(this));
        Objects.requireNonNull(getCommand("giveminionegg")).setExecutor(new GiveMinionEggCommand(this));
        Objects.requireNonNull(getCommand("minionautomation")).setExecutor(new MinionAutomationCommand(this));
        Objects.requireNonNull(getCommand("collectall")).setExecutor(new CollectAllCommand(this));
        Objects.requireNonNull(getCommand("getbundle")).setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
                return true;
            }
            bundleManager.createBundle(player);
            player.sendMessage(ChatColor.GREEN + "You received a Minion Collection Bundle!");
            return true;
        });
    }

    public MinionBundleManager getBundleManager() {
        return bundleManager;
    }

    public void spawnMinion(Player owner, Location location) {
        if (location.getWorld() == null) return;

        location.getWorld().spawn(location, ArmorStand.class, as -> {
            as.setBasePlate(false);
            as.setSmall(true);
            as.setGravity(false);
            as.setInvulnerable(true);
            as.setArms(true);

            as.setCustomNameVisible(true);

            if (as.getEquipment() != null) {
                as.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
                as.getEquipment().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
                as.getEquipment().setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
                as.getEquipment().setBoots(new ItemStack(Material.LEATHER_BOOTS));
            }

            as.getPersistentDataContainer().set(isMinionKey, PersistentDataType.BYTE, (byte) 1);
            as.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
            as.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, 1);
            as.getPersistentDataContainer().set(modeKey, PersistentDataType.BYTE, (byte) 0);
            as.getPersistentDataContainer().set(indexKey, PersistentDataType.INTEGER, 0);
            as.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, Material.COBBLESTONE.name());

            // auto-start if owner has automation active
            if (automationPlayers.contains(owner.getUniqueId())) {
                new Minion(this, as).startAutomation();
            }
        });

        owner.sendMessage(ChatColor.GREEN + "A new CobbleMinion has been spawned!");
    }

    /** Called by MinionAutomationCommand */
    public boolean isAutomationActive(UUID player) {
        return automationPlayers.contains(player);
    }
    public void setAutomationActive(UUID player, boolean active) {
        if (active) automationPlayers.add(player);
        else automationPlayers.remove(player);
    }

    @Override
    public void onDisable() {
        // Save all data before plugin is disabled
        saveAllData();

    }

    private void saveAllData() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        // Save minion inventories
        File minionStorage = new File(dataFolder, "minion-storage.yml");
        YamlConfiguration minionConfig = new YamlConfiguration();

        for (Map.Entry<UUID, Inventory> entry : minionInventories.entrySet()) {
            String path = "minions." + entry.getKey().toString();
            Inventory inv = entry.getValue();

            List<ItemStack> itemsToSave = new ArrayList<>();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    String displayName = item.getItemMeta().getDisplayName();
                    // Skip UI elements
                    if (displayName.equals(ChatColor.RED + "Back") ||
                        displayName.equals(ChatColor.GREEN + "Collect All") ||
                        displayName.equals(ChatColor.GREEN + "Collect from Chest")) {
                        continue;
                    }
                }
                if (item != null && !item.getType().isAir()) {
                    minionConfig.set(path + ".items." + i, item);
                }
            }
            minionConfig.set(path + ".size", inv.getSize());
        }

        try {
            minionConfig.save(minionStorage);
            getLogger().info("Successfully saved minion storage data!");
        } catch (IOException e) {
            getLogger().warning("Failed to save minion storage data: " + e.getMessage());
        }

        // Save bundle contents
        File bundleStorage = new File(dataFolder, "bundle-storage.yml");
        YamlConfiguration bundleConfig = new YamlConfiguration();

        bundleManager.saveData(bundleConfig);

        try {
            bundleConfig.save(bundleStorage);
            getLogger().info("Successfully saved bundle data!");
        } catch (IOException e) {
            getLogger().warning("Failed to save bundle storage data: " + e.getMessage());
        }
    }

    private void loadAllData() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) {
            return;
        }

        // Load minion inventories
        File minionStorage = new File(dataFolder, "minion-storage.yml");
        if (minionStorage.exists()) {
            YamlConfiguration minionConfig = YamlConfiguration.loadConfiguration(minionStorage);

            if (minionConfig.contains("minions")) {
                ConfigurationSection minionsSection = minionConfig.getConfigurationSection("minions");
                if (minionsSection != null) {
                    for (String uuidStr : minionsSection.getKeys(false)) {
                        try {
                            UUID uuid = UUID.fromString(uuidStr);
                            String path = "minions." + uuidStr;
                            int size = minionConfig.getInt(path + ".size", 27);

                            // Create inventory with proper name and size
                            Inventory inv = Bukkit.createInventory(new StorageHolder(uuid), size, ChatColor.AQUA + "Minion Storage");

                            // Load items
                            ConfigurationSection items = minionConfig.getConfigurationSection(path + ".items");
                            if (items != null) {
                                for (String slot : items.getKeys(false)) {
                                    ItemStack item = items.getItemStack(slot);
                                    if (item != null && !item.getType().isAir()) {
                                        inv.setItem(Integer.parseInt(slot), item);
                                    }
                                }
                            }

                            // Add UI elements
                            setupMinionStorageUI(inv);

                            minionInventories.put(uuid, inv);
                        } catch (IllegalArgumentException e) {
                            getLogger().warning("Invalid UUID in storage file: " + uuidStr);
                        }
                    }
                }
            }
            getLogger().info("Successfully loaded minion storage data!");
        }

        // Load bundle contents
        File bundleStorage = new File(dataFolder, "bundle-storage.yml");
        if (bundleStorage.exists()) {
            YamlConfiguration bundleConfig = YamlConfiguration.loadConfiguration(bundleStorage);
            bundleManager.loadData(bundleConfig);
            getLogger().info("Successfully loaded bundle data!");
        }
    }

    private void setupMinionStorageUI(Inventory inv) {
        // Add collect from chest button
        ItemStack collectFromChest = new ItemStack(Material.HOPPER);
        ItemMeta collectChestMeta = collectFromChest.getItemMeta();
        if (collectChestMeta != null) {
            collectChestMeta.setDisplayName(ChatColor.GREEN + "Collect from Chest");
            collectFromChest.setItemMeta(collectChestMeta);
        }
        inv.setItem(inv.getSize() - 3, collectFromChest);

        // Add collect all button
        ItemStack collectAll = new ItemStack(Material.HOPPER);
        ItemMeta collectAllMeta = collectAll.getItemMeta();
        if (collectAllMeta != null) {
            collectAllMeta.setDisplayName(ChatColor.GREEN + "Collect All");
            collectAll.setItemMeta(collectAllMeta);
        }
        inv.setItem(inv.getSize() - 2, collectAll);

        // Add back button
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ChatColor.RED + "Back");
            back.setItemMeta(backMeta);
        }
        inv.setItem(inv.getSize() - 1, back);
    }
}