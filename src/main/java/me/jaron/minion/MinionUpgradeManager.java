package me.jaron.minion;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MinionUpgradeManager {
    private final Plugin plugin;
    private FileConfiguration config;

    // Cache for tier settings to avoid repeated config lookups
    private final Map<String, Map<Integer, Map<String, Object>>> tierSettings = new HashMap<>();
    private final Map<Integer, Map<String, Integer>> upgradeCosts = new HashMap<>();

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
        // Check cache first
        Map<Integer, Map<String, Object>> typeCache = tierSettings.computeIfAbsent(type, k -> new HashMap<>());
        Map<String, Object> tierCache = typeCache.computeIfAbsent(tier, k -> {
            Map<String, Object> settings = new HashMap<>();

            // Load settings from config
            String path = type + ".tiers." + tier;
            ConfigurationSection section = config.getConfigurationSection(path);
            if (section != null) {
                for (String settingKey : section.getKeys(false)) {
                    settings.put(settingKey, section.get(settingKey));
                }
            }

            return settings;
        });

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
}
