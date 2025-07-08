package me.jaron.minion.api.registry;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;

/**
 * Registry for managing minion upgrades and tiers.
 */
public class MinionUpgradeRegistry {
    private final Plugin plugin;
    private FileConfiguration config;

    // Cache for tier settings to avoid repeated config lookups
    private final Map<String, Map<Integer, Map<String, Object>>> tierSettings = new HashMap<>();
    private final Map<Integer, Map<String, Integer>> upgradeCosts = new HashMap<>();
    private final Set<Integer> availableTiers = new HashSet<>();
    private final Map<Integer, Integer> tierStorageSizes = new HashMap<>();
    private int maxTier = 5;

    /**
     * Creates a new MinionUpgradeRegistry
     * @param plugin The plugin instance
     */
    public MinionUpgradeRegistry(Plugin plugin) {
        this.plugin = plugin;
        loadConfiguration();
    }

    /**
     * Loads the upgrade configuration from file
     */
    public void loadConfiguration() {
        // Create default config if it doesn't exist
        plugin.saveResource("minion-upgrades.yml", false);

        // Load config
        File configFile = new File(plugin.getDataFolder(), "minion-upgrades.yml");
        config = YamlConfiguration.loadConfiguration(configFile);

        // Clear caches
        tierSettings.clear();
        upgradeCosts.clear();
        availableTiers.clear();

        // Find all available tiers in the default section
        ConfigurationSection defaultTiers = config.getConfigurationSection("default.tiers");
        if (defaultTiers != null) {
            for (String tierKey : defaultTiers.getKeys(false)) {
                try {
                    int tier = Integer.parseInt(tierKey);
                    availableTiers.add(tier);

                    // Cache the storage size for this tier
                    int storageSize = config.getInt("default.tiers." + tier + ".storage_size", 27);
                    tierStorageSizes.put(tier, storageSize);

                    // Find the highest tier
                    if (tier > maxTier) maxTier = tier;
                } catch (NumberFormatException ignored) {
                    plugin.getLogger().warning("Invalid tier number: " + tierKey);
                }
            }
        }

        // Load upgrade costs
        ConfigurationSection upgradeCostSection = config.getConfigurationSection("default.upgrade_costs");
        if (upgradeCostSection != null) {
            for (String tierKey : upgradeCostSection.getKeys(false)) {
                try {
                    int tier = Integer.parseInt(tierKey);
                    ConfigurationSection costSection = upgradeCostSection.getConfigurationSection(tierKey);
                    if (costSection != null) {
                        Map<String, Integer> costs = new HashMap<>();
                        for (String material : costSection.getKeys(false)) {
                            costs.put(material, costSection.getInt(material));
                        }
                        upgradeCosts.put(tier, costs);
                    }
                } catch (NumberFormatException ignored) {
                    plugin.getLogger().warning("Invalid tier number in upgrade costs: " + tierKey);
                }
            }
        }
    }

    /**
     * Register a custom tier for a specific minion type
     * @param minionTypeId The minion type ID
     * @param tier The tier level
     * @param settings Map of settings for this tier
     */
    public void registerCustomTier(String minionTypeId, int tier, Map<String, Object> settings) {
        Map<Integer, Map<String, Object>> typeTiers = tierSettings.computeIfAbsent(minionTypeId, k -> new HashMap<>());
        typeTiers.put(tier, settings);

        if (tier > maxTier) {
            maxTier = tier;
        }
        availableTiers.add(tier);
    }

    /**
     * Get the delay between actions for a specific minion type and tier
     * @param minionTypeId The minion type ID
     * @param tier The tier level
     * @return The delay in ticks
     */
    public long getDelay(String minionTypeId, int tier) {
        Object value = getTierSetting(minionTypeId, tier, "delay");
        return value instanceof Number ? ((Number) value).longValue() : 6000L;
    }

    /**
     * Get the efficiency multiplier for a specific minion type and tier
     * @param minionTypeId The minion type ID
     * @param tier The tier level
     * @return The efficiency multiplier
     */
    public double getEfficiency(String minionTypeId, int tier) {
        Object value = getTierSetting(minionTypeId, tier, "efficiency");
        return value instanceof Number ? ((Number) value).doubleValue() : 1.0;
    }

    /**
     * Get the display name for a specific minion type and tier
     * @param minionTypeId The minion type ID
     * @param tier The tier level
     * @return The formatted display name
     */
    public String getDisplayName(String minionTypeId, int tier) {
        Object value = getTierSetting(minionTypeId, tier, "display_name");
        return value != null ? value.toString() : "Minion Tier " + tier;
    }

    /**
     * Get the hologram message for a specific minion type and tier
     * @param minionTypeId The minion type ID
     * @param tier The tier level
     * @return The hologram message
     */
    public String getHologramMessage(String minionTypeId, int tier) {
        Object value = getTierSetting(minionTypeId, tier, "hologram_message");
        return value != null ? value.toString() : "Working...";
    }

    /**
     * Get a message for a specific action, minion type and tier
     * @param minionTypeId The minion type ID
     * @param tier The tier level
     * @param action The action type
     * @return The message string
     */
    public String getMessage(String minionTypeId, int tier, String action) {
        Map<String, Object> messages = (Map<String, Object>) getTierSetting(minionTypeId, tier, "messages");
        if (messages != null && messages.containsKey(action)) {
            return messages.get(action).toString();
        }
        return "Working...";
    }

    /**
     * Get the inventory size for a specific tier
     * @param tier The tier level
     * @return The inventory size (number of slots)
     */
    public int getStorageSize(int tier) {
        return tierStorageSizes.getOrDefault(tier, 27);
    }

    /**
     * Get all available tiers
     * @return Set of available tier levels
     */
    public Set<Integer> getAvailableTiers() {
        return new HashSet<>(availableTiers);
    }

    /**
     * Get the maximum tier level
     * @return The maximum tier level
     */
    public int getMaxTier() {
        return maxTier;
    }

    /**
     * Get the upgrade costs for a specific tier
     * @param tier The tier to upgrade to
     * @return Map of material name to amount needed
     */
    public Map<String, Integer> getUpgradeCosts(int tier) {
        return upgradeCosts.getOrDefault(tier, new HashMap<>());
    }

    /**
     * Helper method to get a tier setting with fallback to default
     */
    private Object getTierSetting(String minionTypeId, int tier, String setting) {
        // Check for cached type-specific setting
        Map<Integer, Map<String, Object>> typeTiers = tierSettings.get(minionTypeId);
        if (typeTiers != null && typeTiers.containsKey(tier)) {
            Map<String, Object> tierConfig = typeTiers.get(tier);
            if (tierConfig.containsKey(setting)) {
                return tierConfig.get(setting);
            }
        }

        // Fall back to config file
        String typeSpecificPath = minionTypeId + ".tiers." + tier + "." + setting;
        if (config.contains(typeSpecificPath)) {
            return config.get(typeSpecificPath);
        }

        // Fall back to default
        return config.get("default.tiers." + tier + "." + setting);
    }
}
