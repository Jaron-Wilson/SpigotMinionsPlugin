package me.jaron.minion.api.registry;

import me.jaron.minion.api.minion.IMinionType;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry for all minion types in the system.
 * This allows plugins to register custom minion types.
 */
public class MinionRegistry {
    private final Map<String, IMinionType> minionTypes = new HashMap<>();

    /**
     * Register a new minion type
     * @param type The minion type to register
     * @return True if successful, false if a minion with the same ID already exists
     */
    public boolean registerMinionType(IMinionType type) {
        if (minionTypes.containsKey(type.getId())) {
            return false;
        }
        minionTypes.put(type.getId(), type);
        return true;
    }

    /**
     * Unregister a minion type by its ID
     * @param id The ID of the minion type to unregister
     * @return The unregistered minion type, or null if not found
     */
    public IMinionType unregisterMinionType(String id) {
        return minionTypes.remove(id);
    }

    /**
     * Get a minion type by its ID
     * @param id The ID of the minion type
     * @return The minion type, or null if not found
     */
    public IMinionType getMinionType(String id) {
        return minionTypes.get(id);
    }

    /**
     * Get all registered minion types
     * @return A collection of all minion types
     */
    public Collection<IMinionType> getAllMinionTypes() {
        return minionTypes.values();
    }

    /**
     * Check if a minion type with the given ID is registered
     * @param id The ID to check
     * @return True if registered, false otherwise
     */
    public boolean isRegistered(String id) {
        return minionTypes.containsKey(id);
    }
}
