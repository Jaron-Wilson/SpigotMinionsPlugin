package me.jaron.minion.api.registry;

import me.jaron.minion.api.ability.IMinionAbility;
import me.jaron.minion.api.minion.IMinionType;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Registry for minion abilities.
 * This allows plugins to register custom abilities for minions.
 */
public class MinionAbilityRegistry {
    private final Map<String, IMinionAbility> abilities = new HashMap<>();

    /**
     * Register a new minion ability
     * @param ability The ability to register
     * @return True if successful, false if an ability with the same ID already exists
     */
    public boolean registerAbility(IMinionAbility ability) {
        if (abilities.containsKey(ability.getId())) {
            return false;
        }
        abilities.put(ability.getId(), ability);
        return true;
    }

    /**
     * Unregister an ability by its ID
     * @param id The ID of the ability to unregister
     * @return The unregistered ability, or null if not found
     */
    public IMinionAbility unregisterAbility(String id) {
        return abilities.remove(id);
    }

    /**
     * Get an ability by its ID
     * @param id The ID of the ability
     * @return The ability, or null if not found
     */
    public IMinionAbility getAbility(String id) {
        return abilities.get(id);
    }

    /**
     * Get all registered abilities
     * @return A collection of all abilities
     */
    public Collection<IMinionAbility> getAllAbilities() {
        return abilities.values();
    }

    /**
     * Get all abilities compatible with a specific minion type
     * @param minionType The minion type
     * @return A collection of compatible abilities
     */
    public Collection<IMinionAbility> getCompatibleAbilities(IMinionType minionType) {
        return abilities.values().stream()
                .filter(ability -> ability.isCompatibleWith(minionType))
                .collect(Collectors.toList());
    }

    /**
     * Get all abilities available for a specific minion type and tier
     * @param minionType The minion type
     * @param tier The tier level
     * @return A collection of available abilities
     */
    public Collection<IMinionAbility> getAvailableAbilities(IMinionType minionType, int tier) {
        return abilities.values().stream()
                .filter(ability -> ability.isCompatibleWith(minionType) && ability.getMinimumTierRequired() <= tier)
                .collect(Collectors.toList());
    }
}
