package me.jaron.minion.api;

import me.jaron.minion.MinecraftMinionPlugin;
import me.jaron.minion.api.events.EventRegistry;
import me.jaron.minion.api.registry.MinionRegistry;
import me.jaron.minion.api.registry.MinionUpgradeRegistry;
import me.jaron.minion.api.registry.MinionAbilityRegistry;
import org.bukkit.plugin.Plugin;

/**
 * The central API class for the Minions plugin.
 * This provides access to all registries and core functionality.
 */
public class MinionsAPI {
    private static MinionsAPI instance;
    private final MinionRegistry minionRegistry;
    private final MinionUpgradeRegistry upgradeRegistry;
    private final MinionAbilityRegistry abilityRegistry;
    private final EventRegistry eventRegistry;
    private final MinecraftMinionPlugin plugin;

    public MinionsAPI(MinecraftMinionPlugin plugin) {
        this.plugin = plugin;
        this.minionRegistry = new MinionRegistry();
        this.upgradeRegistry = new MinionUpgradeRegistry(plugin);
        this.abilityRegistry = new MinionAbilityRegistry();
        this.eventRegistry = new EventRegistry();
        instance = this;
    }

    /**
     * Get the instance of the MinionsAPI
     * @return The API instance
     */
    public static MinionsAPI getInstance() {
        return instance;
    }

    /**
     * Get the MinionRegistry for registering and retrieving minion types
     * @return The MinionRegistry
     */
    public MinionRegistry getMinionRegistry() {
        return minionRegistry;
    }

    /**
     * Get the MinionUpgradeRegistry for registering and retrieving minion upgrades
     * @return The MinionUpgradeRegistry
     */
    public MinionUpgradeRegistry getUpgradeRegistry() {
        return upgradeRegistry;
    }

    /**
     * Get the MinionAbilityRegistry for registering and retrieving minion abilities
     * @return The MinionAbilityRegistry
     */
    public MinionAbilityRegistry getAbilityRegistry() {
        return abilityRegistry;
    }

    /**
     * Get the EventRegistry for accessing custom events
     * @return The EventRegistry
     */
    public EventRegistry getEventRegistry() {
        return eventRegistry;
    }

    /**
     * Get the plugin instance
     * @return The plugin instance
     */
    public MinecraftMinionPlugin getPlugin() {
        return plugin;
    }
}
