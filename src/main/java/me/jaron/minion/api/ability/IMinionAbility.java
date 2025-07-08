package me.jaron.minion.api.ability;

import me.jaron.minion.api.minion.IMinionType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

/**
 * Interface for minion abilities.
 * Implement this to create custom abilities for minions.
 */
public interface IMinionAbility {

    /**
     * Get the unique identifier for this ability
     * @return The ability ID
     */
    String getId();

    /**
     * Get the display name of this ability
     * @return The formatted display name
     */
    String getDisplayName();

    /**
     * Get the description of this ability
     * @return The formatted description
     */
    String getDescription();

    /**
     * Check if this ability is compatible with the given minion type
     * @param minionType The minion type to check compatibility with
     * @return True if compatible, false otherwise
     */
    boolean isCompatibleWith(IMinionType minionType);

    /**
     * Get the minimum tier level required for this ability
     * @return The minimum tier level
     */
    int getMinimumTierRequired();

    /**
     * Execute the ability
     * @param armorStand The armor stand representing the minion
     * @param tier The minion's current tier
     * @return True if execution was successful, false otherwise
     */
    boolean execute(ArmorStand armorStand, int tier);

    /**
     * Get the cooldown time for this ability (in ticks)
     * @param tier The minion's current tier
     * @return The cooldown time in ticks
     */
    long getCooldown(int tier);
}
