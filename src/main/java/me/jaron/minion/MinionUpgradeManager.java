package me.jaron.minion;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;

public class MinionUpgradeManager {
    private final Plugin plugin;
    private FileConfiguration config;

    // Cache for tier settings to avoid repeated config lookups
    private final Map<String, Map<Integer, Map<String, Object>>> tierSettings = new HashMap<>();
    private final Map<Integer, Map<String, Integer>> upgradeCosts = new HashMap<>();
    private final Set<Integer> availableTiers = new HashSet<>();
    private final Map<Integer, Integer> tierStorageSizes = new HashMap<>();
    private int maxTier = 5;

    public MinionUpgradeManager(Plugin plugin) {
        this.plugin = plugin;
        loadConfiguration();
    }

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
                } catch (NumberFormatException ignored) {
                    plugin.getLogger().warning("Invalid tier number: " + tierKey);
                }
            }
        }

        if (!availableTiers.isEmpty()) {
            maxTier = Collections.max(availableTiers);
        }

        // Cache upgrade costs
        ConfigurationSection costSection = config.getConfigurationSection("upgrade_costs");
        if (costSection != null) {
            for (String tierKey : costSection.getKeys(false)) {
                try {
                    int tier = Integer.parseInt(tierKey);
                    ConfigurationSection tierCosts = costSection.getConfigurationSection(tierKey);
                    if (tierCosts != null) {
                        Map<String, Integer> costs = new HashMap<>();
                        for (String material : tierCosts.getKeys(false)) {
                            costs.put(material, tierCosts.getInt(material));
                        }
                        upgradeCosts.put(tier, costs);
                    }
                } catch (NumberFormatException ignored) {
                    plugin.getLogger().warning("Invalid tier number in upgrade costs: " + tierKey);
                }
            }
        }
        if (defaultTiers != null) {
            for (String tierKey : defaultTiers.getKeys(false)) {
                int tier = Integer.parseInt(tierKey);
                int storageSize = defaultTiers.getInt(tierKey + ".storage-size");
                if (storageSize > 0) {
                    tierStorageSizes.put(tier, storageSize);
                }
            }
        }
    }

    /**
     * Gets the storage size for a given minion type and tier from the config.
     * It checks the specific minion type first, then falls back to default settings.
     *
     * @param minionType The type of minion (e.g., "miner")
     * @param tier The tier number to look up.
     * @return The configured storage size.
     */
    public int getStorageSizeForTier(String minionType, int tier) {
        FileConfiguration config = plugin.getConfig();
        String tierStr = String.valueOf(tier);

        // Path for the specific minion type (e.g., "miner.tiers.2")
        String specificTierPath = minionType.toLowerCase() + ".tiers." + tierStr;
        // Path for the default settings
        String defaultTierPath = "default.tiers." + tierStr;

        int size = 0;

        // 1. Check the specific minion type's config first (e.g., miner.tiers.2.storage-size)
        if (config.isSet(specificTierPath + ".storage-size")) {
            size = config.getInt(specificTierPath + ".storage-size");
        }
        // 2. If not found, check the default tier settings (e.g., default.tiers.2.storage-size)
        else if (config.isSet(defaultTierPath + ".storage-size")) {
            size = config.getInt(defaultTierPath + ".storage-size");
        }

        // 3. If no value is found in the config, calculate a default size
        if (size <= 0) {
            return Math.min(tier * 9, 54); // Default: 9 slots per tier, max 54
        }

        // Ensure the size is a multiple of 9 for a clean GUI, and not over 54
        size = Math.min(54, (int) (Math.ceil(size / 9.0) * 9));
        return Math.max(9, size); // Ensure minimum size is 9
    }

    public int getStorageSize(int tier) {
        return tierStorageSizes.getOrDefault(tier, 9);
    }

    public int getDelay(MinionType type, int tier) {
        int delay = getIntSetting(type.name().toLowerCase(), tier, "delay", 5000);
        return Math.max(delay, 500); // Minimum delay of 500ms
    }

    public double getEfficiency(MinionType type, int tier) {
        return getDoubleSetting(type.name().toLowerCase(), tier, "efficiency", 1.0);
    }

    public int getRange(MinionType type, int tier) {
        return getIntSetting(type.name().toLowerCase(), tier, "range", 2);
    }

    public double getBoneMealChance(int tier) {
        return getDoubleSetting("farmer", tier, "bonemeal_chance", 0.0);
    }

    public double getDoubleCropsChance(int tier) {
        return getDoubleSetting("farmer", tier, "double_crops", 0.0);
    }

    public double getFortuneChance(int tier) {
        return getDoubleSetting("miner", tier, "fortune_chance", 0.0);
    }

    public boolean canReplantSaplings(int tier) {
        return getBooleanSetting("lumberjack", tier, "replant_saplings", tier > 1);
    }

    /**
     * Get any custom setting from the configuration
     *
     * @param type        Minion type
     * @param tier        Tier number
     * @param key         Setting key
     * @param defaultValue Default value if setting is not found
     * @return The setting value, or default if not found
     */
    public Object getCustomSetting(String type, int tier, String key, Object defaultValue) {
        Object setting = getSettingFromCache(type, tier, key, Object.class);
        if (setting != null) {
            return setting;
        }

        Object defaultSetting = getSettingFromCache("default", tier, key, Object.class);
        return defaultSetting != null ? defaultSetting : defaultValue;
    }

    /**
     * Check if a custom setting exists for the given type and tier
     *
     * @param type Minion type
     * @param tier Tier number
     * @param key  Setting key
     * @return true if the setting exists, false otherwise
     */
    public boolean hasCustomSetting(String type, int tier, String key) {
        Map<Integer, Map<String, Object>> typeCache = tierSettings.get(type);
        if (typeCache != null) {
            Map<String, Object> tierCache = typeCache.get(tier);
            if (tierCache != null) {
                return tierCache.containsKey(key);
            }
        }

        // Check default settings as fallback
        typeCache = tierSettings.get("default");
        if (typeCache != null) {
            Map<String, Object> tierCache = typeCache.get(tier);
            if (tierCache != null) {
                return tierCache.containsKey(key);
            }
        }

        return false;
    }

    /**
     * Get all available tiers from the configuration
     *
     * @return Set of tier numbers
     */
    public Set<Integer> getAvailableTiers() {
        return Collections.unmodifiableSet(availableTiers);
    }

    /**
     * Get the maximum configured tier
     *
     * @return Maximum tier number
     */
    public int getMaxTier() {
        return maxTier;
    }

    /**
     * Get all custom settings for a specific minion type and tier
     *
     * @param type Minion type
     * @param tier Tier number
     * @return Map of setting keys to values
     */
    public Map<String, Object> getAllSettings(String type, int tier) {
        Map<String, Object> result = new HashMap<>();

        // First get default settings
        Map<Integer, Map<String, Object>> defaultTypeCache = tierSettings.get("default");
        if (defaultTypeCache != null && defaultTypeCache.containsKey(tier)) {
            result.putAll(defaultTypeCache.get(tier));
        }

        // Then override with type-specific settings
        Map<Integer, Map<String, Object>> typeCache = tierSettings.get(type);
        if (typeCache != null && typeCache.containsKey(tier)) {
            result.putAll(typeCache.get(tier));
        }

        return result;
    }

    public Map<String, Integer> getUpgradeCost(int targetTier) {
        return upgradeCosts.getOrDefault(targetTier, new HashMap<>());
    }

    private int getIntSetting(String type, int tier, String key, int defaultValue) {
        // First check type-specific setting
        Integer typeSetting = getSettingFromCache(type, tier, key, Integer.class);
        if (typeSetting != null) {
            return typeSetting;
        }

        // Fall back to default setting
        Integer defaultSetting = getSettingFromCache("default", tier, key, Integer.class);
        return defaultSetting != null ? defaultSetting : defaultValue;
    }

    private double getDoubleSetting(String type, int tier, String key, double defaultValue) {
        // First check type-specific setting
        Double typeSetting = getSettingFromCache(type, tier, key, Double.class);
        if (typeSetting != null) {
            return typeSetting;
        }

        // Fall back to default setting
        Double defaultSetting = getSettingFromCache("default", tier, key, Double.class);
        return defaultSetting != null ? defaultSetting : defaultValue;
    }

    private boolean getBooleanSetting(String type, int tier, String key, boolean defaultValue) {
        // First check type-specific setting
        Boolean typeSetting = getSettingFromCache(type, tier, key, Boolean.class);
        if (typeSetting != null) {
            return typeSetting;
        }

        // Fall back to default setting
        Boolean defaultSetting = getSettingFromCache("default", tier, key, Boolean.class);
        return defaultSetting != null ? defaultSetting : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private <T> T getSettingFromCache(String type, int tier, String key, Class<T> clazz) {
        // Map BLOCK_MINER to miner for configuration lookup
        String configType;
        if (type.equalsIgnoreCase("block_miner")) {
            configType = "miner";
        } else {
            configType = type;
        }

        // Handle nested properties (like messages.mining) directly from config first
        if (key.contains(".")) {
            String[] parts = key.split("\\.", 2);
            String baseKey = parts[0];
            String subKey = parts[1];

            // Try to get directly from config for specific type first
            String typePath = configType + ".tiers." + tier + "." + baseKey + "." + subKey;
            if (config.contains(typePath)) {
                Object value = config.get(typePath);
                if (value != null && clazz.isInstance(value)) {
                    return (T) value;
                }
            }

            // If not found for specific type, check default
            if (!configType.equals("default")) {
                String defaultPath = "default.tiers." + tier + "." + baseKey + "." + subKey;
                if (config.contains(defaultPath)) {
                    Object value = config.get(defaultPath);
                    if (value != null && clazz.isInstance(value)) {
                        return (T) value;
                    }
                }
            }
        }

        // Check cache for regular properties
        Map<Integer, Map<String, Object>> typeCache = tierSettings.computeIfAbsent(configType, k -> new HashMap<>());
        Map<String, Object> tierCache = typeCache.computeIfAbsent(tier, k -> {
            Map<String, Object> settings = new HashMap<>();

            // Load settings from config
            String path = configType + ".tiers." + tier;
            ConfigurationSection section = config.getConfigurationSection(path);
            if (section != null) {
                for (String settingKey : section.getKeys(false)) {
                    settings.put(settingKey, section.get(settingKey));
                }
            }

            return settings;
        });

        // Handle regular properties
        if (tierCache.containsKey(key)) {
            Object value = tierCache.get(key);
            if (clazz.isInstance(value)) {
                return (T) value;
            }
        }

        return null;
    }

    public boolean hasRequiredItems(Map<Material, Integer> inventory, int targetTier) {
        Map<String, Integer> costs = getUpgradeCost(targetTier);
        if (costs.isEmpty()) {
            return false; // No upgrade path defined
        }

        for (Map.Entry<String, Integer> entry : costs.entrySet()) {
            try {
                Material material = Material.valueOf(entry.getKey());
                int requiredAmount = entry.getValue();

                Integer availableAmount = inventory.getOrDefault(material, 0);
                if (availableAmount < requiredAmount) {
                    return false;
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in upgrade costs: " + entry.getKey());
                return false;
            }
        }

        return true;
    }

    /**
     * Get the custom display name for a minion based on its type and tier
     *
     * @param type Minion type
     * @param tier Tier number
     * @return Custom display name or null if none is defined
     */
    public String getCustomDisplayName(String type, int tier) {
        String displayName = getStringSetting(type, tier, "display_name", null);
        return displayName;
    }

    /**
     * Get the hologram message for a minion based on its type and tier
     *
     * @param type Minion type
     * @param tier Tier number
     * @return Hologram message or null if none is defined
     */
    public String getHologramMessage(String type, int tier) {
        String message = getStringSetting(type, tier, "hologram_message", null);
        return message;
    }

    /**
     * Get the list of potion effects for a minion based on its type and tier
     *
     * @param type Minion type
     * @param tier Tier number
     * @return List of potion effect strings or empty list if none are defined
     */
    @SuppressWarnings("unchecked")
    public List<String> getPotionEffects(String type, int tier) {
        List<String> effects = getListSetting(type, tier, "potion_effects", new ArrayList<>());
        return effects;
    }

    /**
     * Get the list of special abilities for a minion based on its type and tier
     *
     * @param type Minion type
     * @param tier Tier number
     * @return List of special ability strings or empty list if none are defined
     */
    @SuppressWarnings("unchecked")
    public List<String> getSpecialAbilities(String type, int tier) {
        List<String> abilities = getListSetting(type, tier, "special_abilities", new ArrayList<>());
        return abilities;
    }

    private String getStringSetting(String type, int tier, String key, String defaultValue) {
        // First check type-specific setting
        String typeSetting = getSettingFromCache(type, tier, key, String.class);
        if (typeSetting != null) {
            return typeSetting;
        }

        // Fall back to default setting
        String defaultSetting = getSettingFromCache("default", tier, key, String.class);
        return defaultSetting != null ? defaultSetting : defaultValue;
    }

    /**
     * Get the display name for a minion based on its type and tier
     *
     * @param type Minion type
     * @param tier Tier number
     * @return Display name or null if none is defined
     */
    public String getDisplayName(String type, int tier) {
        return getStringSetting(type, tier, "display_name", null);
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> getListSetting(String type, int tier, String key, List<T> defaultValue) {
        // First check type-specific setting
        List<T> typeSetting = getSettingFromCache(type, tier, key, List.class);
        if (typeSetting != null) {
            return typeSetting;
        }

        // Fall back to default setting
        List<T> defaultSetting = getSettingFromCache("default", tier, key, List.class);
        return defaultSetting != null ? defaultSetting : defaultValue;
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public void setConfig(FileConfiguration config) {
        this.config = config;
    }

    public Map<String, Map<Integer, Map<String, Object>>> getTierSettings() {
        return tierSettings;
    }

    public Map<Integer, Map<String, Integer>> getUpgradeCosts() {
        return upgradeCosts;
    }

    public Map<Integer, Integer> getTierStorageSizes() {
        return tierStorageSizes;
    }

    public void setMaxTier(int maxTier) {
        this.maxTier = maxTier;
    }
}
